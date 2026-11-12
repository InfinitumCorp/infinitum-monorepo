/**
 * Consent-based data marketplace ([HRVST] packets, Cumulus Data Exchange, FABRIC aggregated insights,
 * SCALVS.ai agent templates), buyer-funded Missions, contributor earnings and payouts.
 *
 * Rules enforced here:
 *  - nothing is listed without an active consent for (product, "data_licensing");
 *  - withdrawing consent delists every asset that relied on it;
 *  - buyers can only license an asset for a use the contributor permitted;
 *  - earnings are held for the product's refund window before they can be paid out.
 */
import { newId, type Db } from "../db.js";
import { type Ctx, fail, audit } from "../context.js";
import { getMarketplace, PAYOUT_MINIMUM_CENTS } from "../catalog.js";
import { ensureCustomerId, type Org } from "./billing.js";
import { paymentFor } from "./refunds.js";

const PURPOSE = "data_licensing";

// ------------------------------------------------------------- consent

export async function grantConsent(db: Db, org: Org, product: string, purpose = PURPOSE) {
  const [existing] = await db.query(
    "select * from consents where org_id=$1 and product=$2 and purpose=$3 and withdrawn_at is null", [org.id, product, purpose]);
  if (existing) return existing;
  const [c] = await db.query("insert into consents (id, org_id, product, purpose) values ($1,$2,$3,$4) returning *",
    [newId("cns"), org.id, product, purpose]);
  await audit(db, org.id, org.id, "consent.granted", { product, purpose });
  return c;
}

export async function withdrawConsent(db: Db, org: Org, product: string, purpose = PURPOSE) {
  return db.tx(async (tx) => {
    const rows = await tx.query(`update consents set withdrawn_at=now()
      where org_id=$1 and product=$2 and purpose=$3 and withdrawn_at is null returning id`, [org.id, product, purpose]);
    const ids = rows.map((r) => r.id);
    const delisted = ids.length
      ? await tx.query("update data_assets set status='withdrawn' where consent_id = any($1) and status='listed' returning id", [ids])
      : [];
    await audit(tx, org.id, org.id, "consent.withdrawn", { product, purpose, delisted: delisted.length });
    return { withdrawn: ids.length, delisted: delisted.length };
  });
}

// ------------------------------------------------------------- listings & licences

export async function listAsset(db: Db, org: Org, a: {
  product: string; title: string; description?: string; permittedUses: string[]; priceCents: number;
  contentHash: string; expiresAt?: string;
}) {
  if (!getMarketplace(a.product)) fail(400, "no_marketplace", `${a.product} has no data marketplace`);
  if (!a.permittedUses?.length) fail(400, "no_uses", "At least one permitted use is required");
  if (!Number.isInteger(a.priceCents) || a.priceCents < 100) fail(400, "bad_price", "priceCents must be an integer ≥ 100");
  const [consent] = await db.query(
    "select id from consents where org_id=$1 and product=$2 and purpose=$3 and withdrawn_at is null", [org.id, a.product, PURPOSE]);
  if (!consent) fail(403, "consent_required", "Grant data-licensing consent for this product before listing");
  const [asset] = await db.query(
    `insert into data_assets (id, contributor_org_id, product, title, description, consent_id, permitted_uses, price_cents, content_hash, expires_at)
     values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) returning *`,
    [newId("ast"), org.id, a.product, a.title, a.description ?? null, consent.id, a.permittedUses, a.priceCents, a.contentHash, a.expiresAt ?? null]);
  return asset;
}

const LIVE_ASSET = `a.status='listed' and (a.expires_at is null or a.expires_at > now())
  and exists (select 1 from consents c where c.id=a.consent_id and c.withdrawn_at is null)`;

export const browseAssets = (db: Db, product: string) =>
  db.query(`select a.id, a.product, a.title, a.description, a.permitted_uses, a.price_cents, a.content_hash, a.expires_at
    from data_assets a where a.product=$1 and ${LIVE_ASSET} order by a.created_at desc limit 200`, [product]);

export function splitRevenue(product: string, grossCents: number) {
  const terms = getMarketplace(product)!;
  const contributor = Math.floor((grossCents * terms.contributorShareBps) / 10000);
  return { contributor, platform: grossCents - contributor };
}

export async function buyLicence(ctx: Ctx, buyer: Org, assetId: string, permittedUse: string) {
  if (buyer.kind === "individual") fail(403, "buyer_not_eligible", "Only verified business or government accounts can license data");
  const [asset] = await ctx.db.query(`select a.* from data_assets a where a.id=$1 and ${LIVE_ASSET}`, [assetId]);
  if (!asset) fail(404, "asset_unavailable", "Asset is not available");
  if (asset.contributor_org_id === buyer.id) fail(400, "own_asset", "Cannot license your own asset");
  if (!asset.permitted_uses.includes(permittedUse)) fail(403, "use_not_permitted", `Contributor has not permitted use "${permittedUse}"`);
  const gross = Number(asset.price_cents);
  const split = splitRevenue(asset.product, gross);
  const id = newId("lic");
  const session = await ctx.pay.paymentCheckout({
    customerId: await ensureCustomerId(ctx, buyer), orgId: buyer.id, purpose: "licence", ref: id,
    items: [{ name: `Data licence (${permittedUse}): ${asset.title}`, amountCents: gross, quantity: 1 }],
    successUrl: `${ctx.config.publicUrl}/marketplace/licences/${id}`, cancelUrl: `${ctx.config.publicUrl}/marketplace`,
  });
  await ctx.db.query(`insert into asset_licences (id, asset_id, buyer_org_id, permitted_use, gross_cents, contributor_cents, platform_cents, status, provider_session_id)
    values ($1,$2,$3,$4,$5,$6,$7,'pending_payment',$8)`, [id, asset.id, buyer.id, permittedUse, gross, split.contributor, split.platform, session.id]);
  return { licenceId: id, checkoutUrl: session.url, grossCents: gross };
}

export async function activateLicence(db: Db, licenceId: string) {
  const [l] = await db.query(`update asset_licences set status='active' where id=$1 and status='pending_payment'
    returning *, (select contributor_org_id from data_assets where id=asset_id) as contributor, (select product from data_assets where id=asset_id) as product`, [licenceId]);
  if (!l) return;
  const hold = getMarketplace(l.product)!.holdDays;
  await credit(db, l.contributor, Number(l.contributor_cents), "licence", l.id, hold);
}

// ------------------------------------------------------------- missions (buyer-funded bounties)

export async function createMission(ctx: Ctx, buyer: Org, a: {
  product: string; title: string; brief: string; permittedUse: string; rewardCents: number; slots: number;
}) {
  if (buyer.kind === "individual") fail(403, "buyer_not_eligible", "Only verified business or government accounts can post missions");
  const terms = getMarketplace(a.product);
  if (!terms || terms.missionFeeBps === 0) fail(400, "no_missions", `${a.product} does not support missions`);
  if (!Number.isInteger(a.rewardCents) || a.rewardCents < 100 || !Number.isInteger(a.slots) || a.slots < 1)
    fail(400, "bad_budget", "rewardCents ≥ 100 and slots ≥ 1 are required");
  const budget = a.rewardCents * a.slots;
  const fee = Math.ceil((budget * terms!.missionFeeBps) / 10000);
  const id = newId("msn");
  const session = await ctx.pay.paymentCheckout({
    customerId: await ensureCustomerId(ctx, buyer), orgId: buyer.id, purpose: "mission", ref: id,
    items: [{ name: `Mission budget: ${a.title} (${a.slots} × $${(a.rewardCents / 100).toFixed(2)})`, amountCents: budget, quantity: 1 },
            { name: "Marketplace fee", amountCents: fee, quantity: 1 }],
    successUrl: `${ctx.config.publicUrl}/missions/${id}`, cancelUrl: `${ctx.config.publicUrl}/missions`,
  });
  await ctx.db.query(`insert into missions (id, buyer_org_id, product, title, brief, permitted_use, reward_cents, slots, fee_cents, status, provider_session_id)
    values ($1,$2,$3,$4,$5,$6,$7,$8,$9,'pending_funding',$10)`,
    [id, buyer.id, a.product, a.title, a.brief, a.permittedUse, a.rewardCents, a.slots, fee, session.id]);
  return { missionId: id, budgetCents: budget, feeCents: fee, checkoutUrl: session.url };
}

export const fundMission = (db: Db, missionId: string) =>
  db.query("update missions set status='open' where id=$1 and status='pending_funding'", [missionId]);

export const openMissions = (db: Db, product: string) =>
  db.query(`select id, product, title, brief, permitted_use, reward_cents, slots, slots_filled from missions
    where product=$1 and status='open' order by created_at desc`, [product]);

export async function submitToMission(db: Db, contributor: Org, missionId: string, assetId: string) {
  const [m] = await db.query("select * from missions where id=$1 and status='open'", [missionId]);
  if (!m) fail(404, "mission_unavailable", "Mission is not open");
  const [a] = await db.query(`select a.* from data_assets a where a.id=$1 and a.contributor_org_id=$2 and ${LIVE_ASSET}`, [assetId, contributor.id]);
  if (!a) fail(404, "asset_unavailable", "Asset not found or not listed");
  if (a.product !== m.product || !a.permitted_uses.includes(m.permitted_use))
    fail(403, "use_not_permitted", "Asset does not permit this mission's use");
  const [s] = await db.query(`insert into mission_submissions (id, mission_id, contributor_org_id, asset_id) values ($1,$2,$3,$4)
    on conflict (mission_id, asset_id) do nothing returning *`, [newId("sbm"), missionId, contributor.id, assetId]);
  return s ?? fail(409, "duplicate_submission", "Asset already submitted to this mission");
}

export async function reviewSubmission(db: Db, buyer: Org, submissionId: string, accept: boolean) {
  return db.tx(async (tx) => {
    const [s] = await tx.query(`select s.*, m.buyer_org_id, m.reward_cents, m.slots, m.slots_filled, m.product, m.permitted_use
      from mission_submissions s join missions m on m.id=s.mission_id where s.id=$1 for update of m`, [submissionId]);
    if (!s || s.buyer_org_id !== buyer.id) fail(404, "submission_not_found", "Submission not found");
    if (s.status !== "submitted") fail(409, "already_reviewed", "Submission already reviewed");
    if (!accept) {
      await tx.query("update mission_submissions set status='rejected' where id=$1", [submissionId]);
      return { status: "rejected" };
    }
    if (s.slots_filled >= s.slots) fail(409, "mission_full", "All mission slots are filled");
    await tx.query("update mission_submissions set status='accepted' where id=$1", [submissionId]);
    await tx.query(`update missions set slots_filled=slots_filled+1,
      status = case when slots_filled+1 >= slots then 'closed' else status end where id=$1`, [s.mission_id]);
    // The accepted asset is licensed to the buyer for the mission's use; the full reward goes to the contributor.
    await tx.query(`insert into asset_licences (id, asset_id, buyer_org_id, permitted_use, gross_cents, contributor_cents, platform_cents, status)
      values ($1,$2,$3,$4,$5,$5,0,'active')`, [newId("lic"), s.asset_id, buyer.id, s.permitted_use, s.reward_cents]);
    await credit(tx, s.contributor_org_id, Number(s.reward_cents), "mission", s.id, getMarketplace(s.product)!.holdDays);
    return { status: "accepted" };
  });
}

/**
 * Cancels a mission and refunds the unspent budget (unfilled slots) plus the matching share of the fee
 * to the buyer's original payment. Rewards already earned for accepted submissions are not refunded.
 */
export async function cancelMission(ctx: Ctx, buyer: Org, missionId: string) {
  const [m] = await ctx.db.query(`update missions n set status='cancelled' from missions o
    where n.id=o.id and n.id=$1 and n.buyer_org_id=$2 and o.status in ('open','pending_funding')
    returning n.*, o.status as previous_status`, [missionId, buyer.id]);
  if (!m) fail(404, "mission_not_found", "Mission not found or not cancellable");
  const unfilled = m.slots - m.slots_filled;
  // Only funded missions collected money.
  const refundDue = m.previous_status === "open" ? unfilled * Number(m.reward_cents) + Math.floor(Number(m.fee_cents) * unfilled / m.slots) : 0;
  let refundId: string | null = null;
  if (refundDue > 0) {
    const payment = await paymentFor(ctx.db, "mission", m.id);
    if (payment) {
      refundId = (await ctx.pay.refund({ paymentIntentId: payment.payment_intent_id, amountCents: refundDue, idempotencyKey: `mission_cancel_${m.id}` })).id;
    }
  }
  await audit(ctx.db, buyer.id, buyer.id, "mission.cancelled", { missionId, refundDueCents: refundDue, refundId });
  // refundId is null only when the original payment cannot be matched; the refund then has to be issued manually.
  return { missionId, refundDueCents: refundDue, refundId, refundIssued: refundId !== null || refundDue === 0 };
}

// ------------------------------------------------------------- earnings & payouts

async function credit(db: Db, orgId: string, cents: number, source: string, ref: string, holdDays: number) {
  if (cents <= 0) return;
  await db.query(`insert into earnings_ledger (id, org_id, delta_cents, source, ref, available_at)
    values ($1,$2,$3,$4,$5, now() + ($6 || ' days')::interval) on conflict (source, ref) do nothing`,
    [newId("ern"), orgId, cents, source, ref, String(holdDays)]);
}
export { credit as creditEarnings };

export async function earningsBalance(db: Db, orgId: string) {
  const [r] = await db.query(`select
      coalesce(sum(delta_cents) filter (where available_at <= now() or delta_cents < 0),0)::bigint as available,
      coalesce(sum(delta_cents) filter (where available_at > now() and delta_cents > 0),0)::bigint as pending
    from earnings_ledger where org_id=$1`, [orgId]);
  return { availableCents: Number(r.available), pendingCents: Number(r.pending) };
}

export async function setupPayoutAccount(ctx: Ctx, org: Org, a: { method: "bank" | "token"; walletAddress?: string; network?: string }) {
  if (a.method === "token") {
    if (!ctx.config.tokenPayoutsEnabled) fail(403, "token_payouts_disabled", "Token payouts are not enabled");
    if (!a.walletAddress || !["ethereum", "base", "solana"].includes(a.network ?? "")) fail(400, "bad_wallet", "walletAddress and network (ethereum|base|solana) are required");
    await ctx.db.query(`insert into payout_accounts (org_id, method, wallet_address, network) values ($1,'token',$2,$3)
      on conflict (org_id) do update set method='token', wallet_address=$2, network=$3, updated_at=now()`, [org.id, a.walletAddress, a.network]);
    return { method: "token" };
  }
  const [existing] = await ctx.db.query("select provider_account_id from payout_accounts where org_id=$1", [org.id]);
  const link = await ctx.pay.connectOnboarding({ existingAccountId: existing?.provider_account_id, email: org.email,
    returnUrl: `${ctx.config.publicUrl}/earnings` });
  await ctx.db.query(`insert into payout_accounts (org_id, method, provider_account_id) values ($1,'bank',$2)
    on conflict (org_id) do update set method='bank', provider_account_id=$2, updated_at=now()`, [org.id, link.accountId]);
  return { method: "bank", onboardingUrl: link.url };
}

export async function requestPayout(ctx: Ctx, org: Org) {
  const [acct] = await ctx.db.query("select * from payout_accounts where org_id=$1", [org.id]);
  if (!acct) fail(400, "no_payout_account", "Set up a payout account first");
  if (acct.method === "token" && !ctx.config.tokenPayoutsEnabled) fail(403, "token_payouts_disabled", "Token payouts are not enabled");

  const payout = await ctx.db.tx(async (db) => {
    await db.query("select pg_advisory_xact_lock(hashtext($1))", [`payout:${org.id}`]);
    const { availableCents } = await earningsBalance(db, org.id);
    if (availableCents < PAYOUT_MINIMUM_CENTS) fail(400, "below_minimum", `Minimum payout is $${PAYOUT_MINIMUM_CENTS / 100}`);
    const id = newId("pay");
    await db.query("insert into payouts (id, org_id, amount_cents, method, status) values ($1,$2,$3,$4,'pending')", [id, org.id, availableCents, acct.method]);
    await db.query("insert into earnings_ledger (id, org_id, delta_cents, source, ref) values ($1,$2,$3,'payout',$4)", [newId("ern"), org.id, -availableCents, id]);
    return { id, amountCents: availableCents };
  });

  if (acct.method === "token") {
    // Settled by the on-chain settlement worker (USDC/product token via FIRM Wallet); stays 'pending' until it confirms.
    return { ...payout, status: "pending" };
  }
  let sentId: string | undefined;
  try {
    const t = await ctx.pay.transfer({ destination: acct.provider_account_id, amountCents: payout.amountCents, idempotencyKey: payout.id });
    sentId = t.id;
    await ctx.db.query("update payouts set status='sent', provider_ref=$2 where id=$1", [payout.id, t.id]);
    return { ...payout, status: "sent" };
  } catch (err) {
    if (sentId) {
      // The bank already accepted the transfer. Putting the earnings back would let the same money be paid twice.
      await ctx.db.query("update payouts set status='sent', provider_ref=$2 where id=$1", [payout.id, sentId]).catch(() => {});
      throw err;
    }
    await ctx.db.query("update payouts set status='failed' where id=$1", [payout.id]);
    await ctx.db.query("insert into earnings_ledger (id, org_id, delta_cents, source, ref) values ($1,$2,$3,'reversal',$4)", [newId("ern"), org.id, payout.amountCents, payout.id]);
    throw err;
  }
}
