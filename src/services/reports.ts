/** Operator revenue report across every stream. */
import type { Db } from "../db.js";
import { getPlan } from "../catalog.js";

export async function revenueReport(db: Db) {
  const subs = await db.query("select plan_id, product, quantity from subscriptions where status in ('active','trialing','past_due')");
  const mrrByProduct: Record<string, number> = {};
  for (const s of subs) {
    const p = getPlan(s.plan_id);
    if (!p) continue;
    const monthly = (p.priceCents * s.quantity) / (p.interval === "year" ? 12 : 1);
    mrrByProduct[s.product] = (mrrByProduct[s.product] ?? 0) + monthly;
  }
  const [usage] = await db.query(`select
      coalesce(sum(cost_cents) filter (where billed_as='overage'),0)::float8 as overage,
      coalesce(sum(cost_cents) filter (where billed_as='credits'),0)::float8 as credits
    from usage_events where occurred_at >= date_trunc('month', now())`);
  const [market] = await db.query(`select coalesce(sum(platform_cents),0)::bigint as licence_take,
      coalesce(sum(gross_cents),0)::bigint as gmv from asset_licences where status='active'`);
  const [missions] = await db.query("select coalesce(sum(fee_cents),0)::bigint as fees from missions where status in ('open','closed')");
  const [hardware] = await db.query(`select coalesce(sum(total_cents),0)::bigint as booked,
      coalesce(sum(total_cents - balance_cents) filter (where status <> 'pending_payment'),0)::bigint as collected
    from orders where status not in ('cancelled','refunded','pending_payment')`);
  const [services] = await db.query(`select coalesce(sum(amount_cents) filter (where status='paid'),0)::bigint as paid,
      coalesce(sum(amount_cents) filter (where status='invoiced'),0)::bigint as outstanding from milestones`);
  const [liab] = await db.query("select coalesce(sum(delta_cents),0)::bigint as owed from earnings_ledger");
  const [prepaid] = await db.query("select coalesce(sum(delta_cents),0)::bigint as unspent from credit_ledger");
  const mrr = Object.values(mrrByProduct).reduce((a, b) => a + b, 0);
  return {
    currency: "usd", unit: "cents",
    recurring: { mrr: Math.round(mrr), arr: Math.round(mrr * 12), byProduct: mrrByProduct },
    usageThisMonth: { overage: usage.overage, prepaidCreditsConsumed: usage.credits },
    marketplace: { gmv: Number(market.gmv), licenceTake: Number(market.licence_take), missionFees: Number(missions.fees) },
    hardware: { booked: Number(hardware.booked), collected: Number(hardware.collected) },
    services: { paid: Number(services.paid), outstanding: Number(services.outstanding) },
    liabilities: { contributorEarningsOwed: Number(liab.owed), unspentPrepaidCredits: Number(prepaid.unspent) },
  };
}
