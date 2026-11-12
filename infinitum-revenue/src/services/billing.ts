/** Orgs, API keys, subscriptions, entitlements, metered usage and prepaid credits. */
import { createHash, randomBytes } from "node:crypto";
import { newId, type Db } from "../db.js";
import { type Ctx, fail, audit } from "../context.js";
import { PLANS, CREDIT_PACKS, canHold, freePlanFor, getMeter, getPlan, type OrgKind, type Plan, type ProductId, PRODUCTS } from "../catalog.js";

export interface Org {
  id: string; name: string; email: string | null; kind: OrgKind; provider_customer_id: string | null;
  approved_products: string[]; verified_defense: boolean;
}

const hashKey = (k: string) => createHash("sha256").update(k).digest("hex");

export async function createOrg(ctx: Ctx, a: { name: string; email?: string; kind: OrgKind; referralCode?: string }) {
  return ctx.db.tx(async (db) => {
    const id = newId("org");
    await db.query("insert into orgs (id, name, email, kind) values ($1,$2,$3,$4)", [id, a.name, a.email ?? null, a.kind]);
    if (a.referralCode) {
      const [code] = await db.query("select code, owner_org_id from referral_codes where code=$1", [a.referralCode]);
      if (code) await db.query("insert into referral_attributions (org_id, code) values ($1,$2)", [id, code.code]);
    }
    const apiKey = `ik_${randomBytes(24).toString("base64url")}`;
    await db.query("insert into api_keys (id, org_id, key_hash, role) values ($1,$2,$3,'admin')", [newId("key"), id, hashKey(apiKey)]);
    await audit(db, id, "system", "org.created", { kind: a.kind });
    return { orgId: id, apiKey };
  });
}

export async function orgForApiKey(db: Db, key: string): Promise<Org | null> {
  const [row] = await db.query<Org>(
    `select o.* from api_keys k join orgs o on o.id=k.org_id where k.key_hash=$1 and k.revoked_at is null`, [hashKey(key)]);
  return row ?? null;
}

export async function getOrg(db: Db, id: string): Promise<Org> {
  const [o] = await db.query<Org>("select * from orgs where id=$1", [id]);
  return o ?? fail(404, "org_not_found", "Organisation not found");
}

async function customerId(ctx: Ctx, org: Org) {
  if (org.provider_customer_id) return org.provider_customer_id;
  const cid = await ctx.pay.ensureCustomer({ id: org.id, name: org.name, email: org.email });
  await ctx.db.query("update orgs set provider_customer_id=$2 where id=$1", [org.id, cid]);
  org.provider_customer_id = cid;
  return cid;
}
export { customerId as ensureCustomerId };

// ------------------------------------------------------------- subscriptions

const LIVE = ["active", "trialing"];

export async function activeSubscription(db: Db, orgId: string, product: string) {
  const [s] = await db.query(
    `select * from subscriptions where org_id=$1 and product=$2 and status = any($3) order by updated_at desc limit 1`,
    [orgId, product, LIVE]);
  return s ?? null;
}

/** The plan an org is on for a product: its live subscription, else the product's free tier, else none. */
export async function effectivePlan(db: Db, orgId: string, product: ProductId) {
  const sub = await activeSubscription(db, orgId, product);
  if (sub) return { plan: getPlan(sub.plan_id) ?? null, sub, grantedBy: null as string | null };
  const granted = await bundledPlanFor(db, orgId, product);
  if (granted) return { plan: granted.plan, sub: null, grantedBy: granted.by };
  return { plan: freePlanFor(product) ?? null, sub: null, grantedBy: null as string | null };
}

/** Whether the org holds `plan`: a live subscription to it, or it is the free tier of a product the org hasn't subscribed to. */
async function holdsPlan(db: Db, orgId: string, plan: Plan) {
  const sub = await activeSubscription(db, orgId, plan.product);
  return sub ? sub.plan_id === plan.id : freePlanFor(plan.product)?.id === plan.id;
}

/** The best plan for `product` that comes bundled with a plan the org holds (e.g. Cumulus Drive with the $FIRM Suite). */
async function bundledPlanFor(db: Db, orgId: string, product: ProductId) {
  let best: { plan: Plan; by: string } | null = null;
  for (const holder of PLANS) {
    if (!holder.bundle?.length || holder.product === product) continue;
    const plan = holder.bundle.map(getPlan).find((p) => p?.product === product);
    if (!plan || (best && best.plan.priceCents >= plan.priceCents)) continue;
    if (await holdsPlan(db, orgId, holder)) best = { plan, by: holder.id };
  }
  return best;
}

export function assertPurchasable(org: Org, plan: Plan) {
  if (!canHold(plan, org.kind)) fail(403, "plan_not_available", `${plan.name} is not available to ${org.kind} accounts`);
  if (plan.channel === "contract") fail(409, "contact_sales", `${plan.name} is sold by contract. Open an engagement or contact sales.`);
  if (plan.channel === "sales_assisted" && !org.approved_products.includes(plan.product))
    fail(403, "approval_required", `${plan.name} requires account approval (KYB / purpose review) before purchase.`);
}

// ------------------------------------------------------------- return URLs

/** True when `origin` (scheme://host[:port]) is an https origin on an allow-listed host. */
export function isAllowedOrigin(ctx: Ctx, origin: string | undefined): boolean {
  if (!origin) return false;
  try {
    const u = new URL(origin);
    const host = u.hostname.toLowerCase();
    return u.protocol === "https:" && (ctx.config.returnHosts ?? []).some((h) => host === h || host.endsWith(`.${h}`));
  } catch { return false; }
}

/** Validates an app-supplied return URL; null when absent. Rejects anything off the allow-list (open-redirect guard). */
export function checkReturnUrl(ctx: Ctx, raw: string | undefined): URL | null {
  if (!raw) return null;
  let u: URL;
  try { u = new URL(raw); } catch { return fail(400, "bad_return_url", "returnUrl must be an absolute https URL"); }
  if (u.username || u.password || !isAllowedOrigin(ctx, u.origin))
    fail(400, "return_url_not_allowed", "returnUrl must be an https URL on an Infinitum product domain");
  u.hash = "";
  return u;
}

const withParam = (u: URL, key: string, value: string) => { const x = new URL(u); x.searchParams.set(key, value); return x.toString(); };

/** Success / cancel URLs for Checkout. Stripe substitutes {CHECKOUT_SESSION_ID}, so it is appended unencoded. */
export function checkoutUrls(ctx: Ctx, returnUrl?: string) {
  const app = checkReturnUrl(ctx, returnUrl);
  if (!app) return {
    successUrl: `${ctx.config.publicUrl}/billing/success?session_id={CHECKOUT_SESSION_ID}`,
    cancelUrl: `${ctx.config.publicUrl}/billing/cancelled`,
  };
  return {
    successUrl: `${withParam(app, "billing", "success")}&session_id={CHECKOUT_SESSION_ID}`,
    cancelUrl: withParam(app, "billing", "cancelled"),
  };
}

export const portalReturnUrl = (ctx: Ctx, returnUrl?: string) =>
  checkReturnUrl(ctx, returnUrl)?.toString() ?? `${ctx.config.publicUrl}/billing`;

export async function startSubscriptionCheckout(ctx: Ctx, org: Org, planId: string, quantity = 1, returnUrl?: string) {
  const plan = getPlan(planId) ?? fail(404, "plan_not_found", `Unknown plan ${planId}`);
  const urls = checkoutUrls(ctx, returnUrl);
  assertPurchasable(org, plan);
  if (plan.priceCents === 0 && !plan.overage) fail(400, "free_plan", "Free plans are active by default; no checkout needed.");
  if (!plan.perSeat && quantity !== 1) fail(400, "bad_quantity", "This plan is not sold per seat");
  if (await activeSubscription(ctx.db, org.id, plan.product))
    fail(409, "already_subscribed", "Already subscribed to this product. Use the billing portal to change plans.");
  const cid = await customerId(ctx, org);
  const session = await ctx.pay.subscriptionCheckout({
    customerId: cid, orgId: org.id, planId: plan.id, quantity, trialDays: plan.trialDays, ...urls,
  });
  await audit(ctx.db, org.id, org.id, "subscription.checkout_started", { planId, quantity });
  return session;
}

export async function upsertSubscription(db: Db, e: {
  providerSubscriptionId: string; orgId: string; planId: string; status: string; quantity: number;
  periodStart: Date | null; periodEnd: Date | null; cancelAtPeriodEnd: boolean;
}) {
  const plan = getPlan(e.planId);
  if (!plan || !e.orgId) return; // not one of ours
  await db.query(
    `insert into subscriptions (id, org_id, plan_id, product, status, quantity, provider_subscription_id,
       current_period_start, current_period_end, cancel_at_period_end, updated_at)
     values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10, now())
     on conflict (provider_subscription_id) do update set plan_id=excluded.plan_id, product=excluded.product,
       status=excluded.status, quantity=excluded.quantity, current_period_start=excluded.current_period_start,
       current_period_end=excluded.current_period_end, cancel_at_period_end=excluded.cancel_at_period_end, updated_at=now()`,
    [newId("sub"), e.orgId, plan.id, plan.product, e.status, e.quantity, e.providerSubscriptionId,
     e.periodStart, e.periodEnd, e.cancelAtPeriodEnd]);
}

// ------------------------------------------------------------- usage metering

function periodFor(sub: any | null, now = new Date()) {
  if (sub?.current_period_start && sub?.current_period_end) return { start: new Date(sub.current_period_start), end: new Date(sub.current_period_end) };
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1));
  return { start, end };
}

async function usedInPeriod(db: Db, orgId: string, meter: string, start: Date, end: Date) {
  const [r] = await db.query(`select coalesce(sum(quantity),0)::float8 as used from usage_events
    where org_id=$1 and meter=$2 and occurred_at >= $3 and occurred_at < $4`, [orgId, meter, start, end]);
  return Number(r.used);
}

export async function creditBalance(db: Db, orgId: string) {
  const [r] = await db.query("select coalesce(sum(delta_cents),0)::bigint as b from credit_ledger where org_id=$1", [orgId]);
  return Number(r.b);
}

/**
 * Records usage and decides how it is paid for:
 *   included  → inside the plan's allowance
 *   overage   → billed on the next invoice (paid plans with overage)
 *   credits   → drawn from prepaid credits
 * Otherwise rejects with 402 so the product can prompt an upgrade.
 * Idempotent on (org, idempotencyKey).
 */
export async function recordUsage(ctx: Ctx, org: Org, a: { meter: string; quantity: number; idempotencyKey: string }) {
  const meter = getMeter(a.meter) ?? fail(404, "meter_not_found", `Unknown meter ${a.meter}`);
  if (!(a.quantity > 0)) fail(400, "bad_quantity", "quantity must be positive");

  const result = await ctx.db.tx(async (db) => {
    const [dupe] = await db.query("select * from usage_events where org_id=$1 and idempotency_key=$2", [org.id, a.idempotencyKey]);
    if (dupe) return { event: dupe, replay: true, overageQty: 0 };
    await db.query("select pg_advisory_xact_lock(hashtext($1))", [`${org.id}:${meter.id}`]);

    const { plan, sub } = await effectivePlan(db, org.id, meter.product);
    const { start, end } = periodFor(sub);
    const used = await usedInPeriod(db, org.id, meter.id, start, end);
    const included = plan?.included?.[meter.id] ?? 0;
    const fromIncluded = Math.min(a.quantity, Math.max(0, included - used));
    const extra = a.quantity - fromIncluded;

    let billedAs: "included" | "overage" | "credits" = "included";
    let cost = 0;
    const id = newId("use");
    if (extra > 0) {
      cost = extra * meter.unitPriceCents;
      if (plan?.overage && sub) {
        billedAs = "overage";
      } else {
        const charge = Math.ceil(cost);
        const balance = await creditBalance(db, org.id);
        if (balance < charge)
          fail(402, "quota_exceeded", `${meter.unit} allowance used (${included}). Upgrade the plan or add credits.`);
        billedAs = "credits";
        cost = charge;
        await db.query("insert into credit_ledger (id, org_id, delta_cents, reason, ref) values ($1,$2,$3,'usage',$4)",
          [newId("cred"), org.id, -charge, id]);
      }
    }
    const [event] = await db.query(
      `insert into usage_events (id, org_id, meter, quantity, billed_as, cost_cents, idempotency_key)
       values ($1,$2,$3,$4,$5,$6,$7) returning *`, [id, org.id, meter.id, a.quantity, billedAs, cost, a.idempotencyKey]);
    return { event, replay: false, overageQty: billedAs === "overage" ? extra : 0 };
  });

  if (result.overageQty > 0) await reportOverage(ctx, org, result.event, result.overageQty);
  return { ...result.event, replay: result.replay };
}

async function reportOverage(ctx: Ctx, org: Org, event: any, qty: number) {
  try {
    await ctx.pay.reportUsage({ customerId: await customerId(ctx, org), meter: event.meter, quantity: qty,
      identifier: event.id, timestamp: new Date(event.occurred_at) });
    await ctx.db.query("update usage_events set reported_at=now() where id=$1", [event.id]);
  } catch (err) {
    console.error("usage report failed; will retry on flush", event.id, err);
  }
}

/** Retries overage events that failed to reach the payment provider. Run on a schedule. */
export async function flushUnreportedUsage(ctx: Ctx) {
  const rows = await ctx.db.query(`select u.*, o.provider_customer_id from usage_events u join orgs o on o.id=u.org_id
    where u.billed_as='overage' and u.reported_at is null order by occurred_at limit 500`);
  let sent = 0;
  for (const r of rows) {
    const unit = getMeter(r.meter)!.unitPriceCents;
    const qty = unit ? Number(r.cost_cents) / unit : Number(r.quantity); // overage part only
    try {
      await ctx.pay.reportUsage({ customerId: r.provider_customer_id, meter: r.meter, quantity: qty, identifier: r.id, timestamp: new Date(r.occurred_at) });
      await ctx.db.query("update usage_events set reported_at=now() where id=$1", [r.id]);
      sent++;
    } catch (err) { console.error("flush failed", r.id, err); }
  }
  return { pending: rows.length, sent };
}

export async function entitlements(db: Db, org: Org) {
  const out: any[] = [];
  for (const product of PRODUCTS) {
    const { plan, sub, grantedBy } = await effectivePlan(db, org.id, product);
    if (!plan) continue;
    const { start, end } = periodFor(sub);
    const meters: Record<string, { included: number; used: number }> = {};
    for (const [m, included] of Object.entries(plan.included ?? {})) {
      meters[m] = { included, used: await usedInPeriod(db, org.id, m, start, end) };
    }
    out.push({ product, plan: plan.id, status: sub?.status ?? (grantedBy ? "bundled" : "free"), grantedBy, seats: sub?.quantity ?? 1,
      features: plan.features ?? [], overage: !!plan.overage && !!sub, period: { start, end }, meters });
  }
  return { orgId: org.id, credits_cents: await creditBalance(db, org.id), products: out };
}

// ------------------------------------------------------------- prepaid credits

export async function startCreditTopup(ctx: Ctx, org: Org, packId: string, returnUrl?: string) {
  const pack = CREDIT_PACKS.find((p) => p.id === packId) ?? fail(404, "pack_not_found", "Unknown credit pack");
  const urls = checkoutUrls(ctx, returnUrl);
  return ctx.pay.paymentCheckout({
    customerId: await customerId(ctx, org), orgId: org.id, purpose: "credits", ref: pack.id,
    items: [{ name: `Infinitum credits ($${(pack.creditCents / 100).toFixed(2)})`, amountCents: pack.priceCents, quantity: 1 }],
    ...urls,
  });
}

export async function applyCreditTopup(db: Db, orgId: string, packId: string, sessionId: string) {
  const pack = CREDIT_PACKS.find((p) => p.id === packId);
  if (!pack) return;
  await db.query(`insert into credit_ledger (id, org_id, delta_cents, reason, ref) values ($1,$2,$3,'topup',$4)
    on conflict (reason, ref) do nothing`, [newId("cred"), orgId, pack.creditCents, sessionId]);
}

export const publicPlans = () => PLANS.map(({ id, product, name, channel, priceCents, interval, perSeat, included, overage, features, bundle, trialDays, allowedKinds }) =>
  ({ id, product, name, channel, priceCents, interval, perSeat: !!perSeat, included: included ?? {}, overage: !!overage, features: features ?? [], bundle: bundle ?? [], trialDays, allowedKinds }));
