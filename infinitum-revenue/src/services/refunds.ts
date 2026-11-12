/**
 * Refunds and lost disputes. Every one-off payment is recorded against what it bought, so when money
 * goes back to a customer the matching value is taken back too:
 *   credits → unspent credits are clawed back pro rata (the balance may go negative and blocks paid usage)
 *   licence → the contributor's share is reversed pro rata; a full refund also ends the licence
 *   order   → a full refund marks the order refunded
 *   mission → a full refund cancels an open mission; contributors keep rewards for accepted work
 * Payouts already sent are not clawed back from bank accounts: a reversal makes the earnings
 * balance negative and is recovered from future earnings.
 */
import { newId, type Db } from "../db.js";
import { audit } from "../context.js";
import { CREDIT_PACKS } from "../catalog.js";

export async function recordPayment(db: Db, p: { paymentIntentId?: string; orgId: string; purpose: string; ref: string; amountCents: number }) {
  if (!p.paymentIntentId) return;
  await db.query(`insert into payments (payment_intent_id, org_id, purpose, ref, amount_cents) values ($1,$2,$3,$4,$5)
    on conflict (payment_intent_id) do nothing`, [p.paymentIntentId, p.orgId, p.purpose, p.ref, p.amountCents]);
}

export const paymentFor = async (db: Db, purpose: string, ref: string) =>
  (await db.query("select * from payments where purpose=$1 and ref=$2", [purpose, ref]))[0] ?? null;

/** Applies a refund given the CUMULATIVE amount refunded on the payment. Safe to call repeatedly. */
export async function applyRefund(db: Db, a: { paymentIntentId: string; cumulativeCents: number; eventId: string }) {
  const [p] = await db.query("select * from payments where payment_intent_id=$1 for update", [a.paymentIntentId]);
  if (!p) {
    await audit(db, null, "payments", "refund.unmatched", a);
    return { applied: false, reason: "unmatched" };
  }
  const amount = Number(p.amount_cents);
  const cumulative = Math.min(amount, a.cumulativeCents);
  const delta = cumulative - Number(p.refunded_cents);
  if (delta <= 0) return { applied: false, reason: "already_applied" };
  await db.query("update payments set refunded_cents=$2 where payment_intent_id=$1", [p.payment_intent_id, cumulative]);
  const full = cumulative >= amount;
  const share = delta / amount;
  const ref = `${p.payment_intent_id}:${a.eventId}`;
  const detail: Record<string, unknown> = { purpose: p.purpose, ref: p.ref, refundedCents: delta, full };

  switch (p.purpose) {
    case "credits": {
      const pack = CREDIT_PACKS.find((x) => x.id === p.ref);
      const clawback = Math.round((pack?.creditCents ?? amount) * share);
      await db.query("insert into credit_ledger (id, org_id, delta_cents, reason, ref) values ($1,$2,$3,'refund',$4) on conflict do nothing",
        [newId("cred"), p.org_id, -clawback, ref]);
      detail.creditsClawedBackCents = clawback;
      break;
    }
    case "licence": {
      const [l] = await db.query(`select l.*, d.contributor_org_id from asset_licences l join data_assets d on d.id=l.asset_id where l.id=$1`, [p.ref]);
      if (!l) break;
      const reverse = Math.floor(Number(l.contributor_cents) * share);
      if (reverse > 0)
        await db.query(`insert into earnings_ledger (id, org_id, delta_cents, source, ref) values ($1,$2,$3,'reversal',$4)
          on conflict (source, ref) do nothing`, [newId("ern"), l.contributor_org_id, -reverse, ref]);
      if (full) await db.query("update asset_licences set status='refunded' where id=$1", [l.id]);
      detail.contributorReversedCents = reverse;
      break;
    }
    case "order":
      if (full) await db.query("update orders set status='refunded' where id=$1 and status <> 'refunded'", [p.ref]);
      break;
    case "mission":
      if (full) await db.query("update missions set status='cancelled' where id=$1 and status in ('open','pending_funding')", [p.ref]);
      break;
  }
  await audit(db, p.org_id, "payments", "refund.applied", detail);
  return { applied: true, ...detail };
}
