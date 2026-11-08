/**
 * Payment provider boundary. Services speak only this interface; Stripe is the
 * production implementation and FakePayments backs tests and local dev.
 */
import Stripe from "stripe";
import { METERS, PLANS, HARDWARE, getPlan } from "./catalog.js";

export interface LineItem { name: string; amountCents: number; quantity: number }

export type PaymentEvent =
  | { kind: "subscription.updated"; eventId: string; providerSubscriptionId: string; orgId: string; planId: string;
      status: string; quantity: number; periodStart: Date | null; periodEnd: Date | null; cancelAtPeriodEnd: boolean }
  | { kind: "checkout.completed"; eventId: string; sessionId: string; paymentIntentId?: string; purpose: string; ref: string; orgId: string; amountCents: number }
  | { kind: "invoice.paid"; eventId: string; invoiceId: string; customerId: string; amountCents: number; metadata: Record<string, string> }
  | { kind: "invoice.failed"; eventId: string; invoiceId: string; customerId: string; providerSubscriptionId?: string }
  /** amountCents is the CUMULATIVE amount refunded on the payment so far. */
  | { kind: "charge.refunded"; eventId: string; paymentIntentId: string; amountCents: number }
  /** A chargeback the merchant lost; amountCents is the disputed amount. Treated like a refund. */
  | { kind: "dispute.lost"; eventId: string; paymentIntentId: string; amountCents: number }
  | { kind: "ignored"; eventId: string; type: string };

export interface PaymentProvider {
  ensureCustomer(org: { id: string; name: string; email?: string | null; providerCustomerId?: string | null }): Promise<string>;
  subscriptionCheckout(a: { customerId: string; orgId: string; planId: string; quantity: number; trialDays?: number;
    successUrl: string; cancelUrl: string }): Promise<{ id: string; url: string }>;
  paymentCheckout(a: { customerId: string; orgId: string; purpose: string; ref: string; items: LineItem[];
    successUrl: string; cancelUrl: string }): Promise<{ id: string; url: string }>;
  sendInvoice(a: { customerId: string; description: string; amountCents: number; dueDays: number;
    metadata: Record<string, string> }): Promise<{ id: string; url: string }>;
  reportUsage(a: { customerId: string; meter: string; quantity: number; identifier: string; timestamp: Date }): Promise<void>;
  billingPortal(customerId: string, returnUrl: string): Promise<string>;
  connectOnboarding(a: { existingAccountId?: string | null; email?: string | null; returnUrl: string }): Promise<{ accountId: string; url: string }>;
  transfer(a: { destination: string; amountCents: number; idempotencyKey: string }): Promise<{ id: string }>;
  refund(a: { paymentIntentId: string; amountCents: number; idempotencyKey: string }): Promise<{ id: string }>;
  parseWebhook(rawBody: string, signature: string | undefined): Promise<PaymentEvent>;
}

// ---------------------------------------------------------------- Stripe

export const lookupKey = (id: string) => `inf_${id}`;
export const meterLookupKey = (meterId: string) => `inf_meter_${meterId}`;

export class StripePayments implements PaymentProvider {
  private priceCache = new Map<string, string>();
  constructor(private stripe: Stripe, private webhookSecret: string) {}

  static fromEnv() {
    const key = process.env.STRIPE_SECRET_KEY;
    const wh = process.env.STRIPE_WEBHOOK_SECRET;
    if (!key || !wh) throw new Error("STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET are required");
    // Fetch-based client works on both Node and Cloudflare Workers.
    return new StripePayments(new Stripe(key, { httpClient: Stripe.createFetchHttpClient() }), wh);
  }

  private async priceId(key: string) {
    const hit = this.priceCache.get(key);
    if (hit) return hit;
    const res = await this.stripe.prices.list({ lookup_keys: [key], active: true, limit: 1 });
    const id = res.data[0]?.id;
    if (!id) throw new Error(`Stripe price with lookup key ${key} not found. Run npm run stripe:sync.`);
    this.priceCache.set(key, id);
    return id;
  }

  async ensureCustomer(org: { id: string; name: string; email?: string | null; providerCustomerId?: string | null }) {
    if (org.providerCustomerId) return org.providerCustomerId;
    const c = await this.stripe.customers.create(
      { name: org.name, email: org.email ?? undefined, metadata: { org_id: org.id } },
      { idempotencyKey: `customer_${org.id}` });
    return c.id;
  }

  async subscriptionCheckout(a: Parameters<PaymentProvider["subscriptionCheckout"]>[0]) {
    const plan = getPlan(a.planId)!;
    const lineItems: Stripe.Checkout.SessionCreateParams.LineItem[] = [
      { price: await this.priceId(lookupKey(plan.id)), quantity: a.quantity },
    ];
    // Attach a metered price for every meter the plan can overage on.
    if (plan.overage) {
      for (const m of METERS.filter((m) => m.product === plan.product)) {
        lineItems.push({ price: await this.priceId(meterLookupKey(m.id)) });
      }
    }
    const s = await this.stripe.checkout.sessions.create({
      mode: "subscription",
      customer: a.customerId,
      line_items: lineItems,
      success_url: a.successUrl,
      cancel_url: a.cancelUrl,
      allow_promotion_codes: true,
      automatic_tax: { enabled: process.env.STRIPE_AUTOMATIC_TAX === "true" },
      metadata: { org_id: a.orgId, plan_id: plan.id, purpose: "subscription" },
      subscription_data: {
        metadata: { org_id: a.orgId, plan_id: plan.id },
        ...(a.trialDays ? { trial_period_days: a.trialDays } : {}),
      },
    });
    return { id: s.id, url: s.url! };
  }

  async paymentCheckout(a: Parameters<PaymentProvider["paymentCheckout"]>[0]) {
    const s = await this.stripe.checkout.sessions.create({
      mode: "payment",
      customer: a.customerId,
      line_items: a.items.map((i) => ({
        quantity: i.quantity,
        price_data: { currency: "usd", unit_amount: i.amountCents, product_data: { name: i.name } },
      })),
      success_url: a.successUrl,
      cancel_url: a.cancelUrl,
      automatic_tax: { enabled: process.env.STRIPE_AUTOMATIC_TAX === "true" },
      metadata: { org_id: a.orgId, purpose: a.purpose, ref: a.ref },
      payment_intent_data: { metadata: { org_id: a.orgId, purpose: a.purpose, ref: a.ref } },
    });
    return { id: s.id, url: s.url! };
  }

  async sendInvoice(a: Parameters<PaymentProvider["sendInvoice"]>[0]) {
    const inv = await this.stripe.invoices.create({
      customer: a.customerId, collection_method: "send_invoice", days_until_due: a.dueDays,
      metadata: a.metadata, auto_advance: true,
    });
    await this.stripe.invoiceItems.create({ customer: a.customerId, invoice: inv.id, amount: a.amountCents, currency: "usd", description: a.description });
    const sent = await this.stripe.invoices.finalizeInvoice(inv.id!);
    await this.stripe.invoices.sendInvoice(sent.id!);
    return { id: sent.id!, url: sent.hosted_invoice_url ?? "" };
  }

  async reportUsage(a: Parameters<PaymentProvider["reportUsage"]>[0]) {
    await this.stripe.billing.meterEvents.create({
      event_name: a.meter,
      identifier: a.identifier,
      timestamp: Math.floor(a.timestamp.getTime() / 1000),
      payload: { stripe_customer_id: a.customerId, value: String(a.quantity) },
    });
  }

  async billingPortal(customerId: string, returnUrl: string) {
    const s = await this.stripe.billingPortal.sessions.create({ customer: customerId, return_url: returnUrl });
    return s.url;
  }

  async connectOnboarding(a: Parameters<PaymentProvider["connectOnboarding"]>[0]) {
    const accountId = a.existingAccountId ?? (await this.stripe.accounts.create({
      type: "express", email: a.email ?? undefined,
      capabilities: { transfers: { requested: true } },
    })).id;
    const link = await this.stripe.accountLinks.create({
      account: accountId, type: "account_onboarding", refresh_url: a.returnUrl, return_url: a.returnUrl,
    });
    return { accountId, url: link.url };
  }

  async transfer(a: Parameters<PaymentProvider["transfer"]>[0]) {
    const t = await this.stripe.transfers.create(
      { amount: a.amountCents, currency: "usd", destination: a.destination },
      { idempotencyKey: a.idempotencyKey });
    return { id: t.id };
  }

  async refund(a: Parameters<PaymentProvider["refund"]>[0]) {
    const r = await this.stripe.refunds.create(
      { payment_intent: a.paymentIntentId, amount: a.amountCents },
      { idempotencyKey: a.idempotencyKey });
    return { id: r.id };
  }

  async parseWebhook(rawBody: string, signature: string | undefined): Promise<PaymentEvent> {
    if (!signature) throw new Error("missing stripe-signature");
    const e = await this.stripe.webhooks.constructEventAsync(rawBody, signature, this.webhookSecret);
    const o: any = e.data.object;
    switch (e.type) {
      case "customer.subscription.created":
      case "customer.subscription.updated":
      case "customer.subscription.deleted": {
        const planItem = o.items?.data?.find((i: any) => !i.price?.recurring?.meter) ?? o.items?.data?.[0];
        const ps = planItem?.current_period_start ?? o.current_period_start;
        const pe = planItem?.current_period_end ?? o.current_period_end;
        return {
          kind: "subscription.updated", eventId: e.id, providerSubscriptionId: o.id,
          orgId: o.metadata?.org_id, planId: o.metadata?.plan_id,
          status: e.type === "customer.subscription.deleted" ? "canceled" : o.status,
          quantity: planItem?.quantity ?? 1,
          periodStart: ps ? new Date(ps * 1000) : null, periodEnd: pe ? new Date(pe * 1000) : null,
          cancelAtPeriodEnd: !!o.cancel_at_period_end,
        };
      }
      case "checkout.session.completed":
        if (o.mode !== "payment" || o.payment_status !== "paid") return { kind: "ignored", eventId: e.id, type: e.type };
        return { kind: "checkout.completed", eventId: e.id, sessionId: o.id, paymentIntentId: o.payment_intent ?? undefined, purpose: o.metadata?.purpose,
          ref: o.metadata?.ref, orgId: o.metadata?.org_id, amountCents: o.amount_total ?? 0 };
      case "invoice.paid":
        return { kind: "invoice.paid", eventId: e.id, invoiceId: o.id, customerId: o.customer,
          amountCents: o.amount_paid ?? 0, metadata: o.metadata ?? {} };
      case "invoice.payment_failed":
        return { kind: "invoice.failed", eventId: e.id, invoiceId: o.id, customerId: o.customer,
          providerSubscriptionId: o.parent?.subscription_details?.subscription ?? o.subscription };
      case "charge.refunded":
        return { kind: "charge.refunded", eventId: e.id, paymentIntentId: o.payment_intent, amountCents: o.amount_refunded };
      case "charge.dispute.closed":
        if (o.status !== "lost") return { kind: "ignored", eventId: e.id, type: e.type };
        return { kind: "dispute.lost", eventId: e.id, paymentIntentId: o.payment_intent, amountCents: o.amount };
      default:
        return { kind: "ignored", eventId: e.id, type: e.type };
    }
  }
}

/** Creates/updates Stripe products, prices and billing meters from the catalog. Idempotent via lookup keys. */
export async function syncCatalogToStripe(stripe: Stripe, log = console.log) {
  const existingMeters = await stripe.billing.meters.list({ limit: 100 });
  for (const m of METERS) {
    let meter = existingMeters.data.find((x) => x.event_name === m.id);
    if (!meter) {
      meter = await stripe.billing.meters.create({
        display_name: `${m.product} · ${m.unit}`, event_name: m.id,
        default_aggregation: { formula: "sum" },
        customer_mapping: { type: "by_id", event_payload_key: "stripe_customer_id" },
        value_settings: { event_payload_key: "value" },
      });
      log(`meter created ${m.id}`);
    }
    const key = meterLookupKey(m.id);
    const found = await stripe.prices.list({ lookup_keys: [key], limit: 1 });
    if (!found.data.length) {
      await stripe.prices.create({
        lookup_key: key, currency: "usd", unit_amount_decimal: Stripe.Decimal.from(String(m.unitPriceCents)),
        recurring: { interval: "month", usage_type: "metered", meter: meter.id },
        product_data: { name: `${m.product} overage · ${m.unit}`, metadata: { meter: m.id } },
      });
      log(`metered price created ${key}`);
    }
  }
  for (const p of PLANS.filter((p) => p.channel !== "contract")) {
    const key = lookupKey(p.id);
    const found = await stripe.prices.list({ lookup_keys: [key], limit: 1 });
    if (found.data.length && found.data[0].unit_amount === p.priceCents) continue;
    await stripe.prices.create({
      lookup_key: key, transfer_lookup_key: true, currency: "usd", unit_amount: p.priceCents,
      recurring: { interval: p.interval },
      product_data: { name: p.name, metadata: { plan_id: p.id, product: p.product } },
    });
    log(`price ${found.data.length ? "replaced" : "created"} ${key}`);
  }
  log(`hardware SKUs (${HARDWARE.length}) use inline price_data at checkout; nothing to sync.`);
}

// ---------------------------------------------------------------- Fake (tests / local dev)

export class FakePayments implements PaymentProvider {
  calls: { op: string; args: any }[] = [];
  private n = 0;
  private id(p: string) { return `${p}_fake_${++this.n}`; }
  private rec<T>(op: string, args: any, out: T): T { this.calls.push({ op, args }); return out; }

  async ensureCustomer(org: any) { return org.providerCustomerId ?? this.rec("ensureCustomer", org, `cus_${org.id}`); }
  async subscriptionCheckout(a: any) { const id = this.id("cs"); return this.rec("subscriptionCheckout", a, { id, url: `https://pay.test/${id}` }); }
  async paymentCheckout(a: any) { const id = this.id("cs"); return this.rec("paymentCheckout", a, { id, url: `https://pay.test/${id}` }); }
  async sendInvoice(a: any) { const id = this.id("in"); return this.rec("sendInvoice", a, { id, url: `https://pay.test/${id}` }); }
  async reportUsage(a: any) { this.rec("reportUsage", a, undefined); }
  async billingPortal(c: string) { return this.rec("billingPortal", c, `https://pay.test/portal/${c}`); }
  async connectOnboarding(a: any) { const accountId = a.existingAccountId ?? this.id("acct"); return this.rec("connectOnboarding", a, { accountId, url: `https://pay.test/connect/${accountId}` }); }
  async transfer(a: any) { return this.rec("transfer", a, { id: this.id("tr") }); }
  async refund(a: any) { return this.rec("refund", a, { id: this.id("re") }); }
  /** Test webhooks are plain JSON PaymentEvents; the signature must equal "test". */
  async parseWebhook(raw: string, sig: string | undefined) {
    if (sig !== "test") throw new Error("bad signature");
    return JSON.parse(raw) as PaymentEvent;
  }
}
