import { readFileSync } from "node:fs";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { migrate, pgliteDb, type Db } from "../src/db.js";
import { FakePayments, type PaymentEvent } from "../src/payments.js";
import type { Ctx } from "../src/context.js";

const ADMIN = "a".repeat(40);
let db: Db, pay: FakePayments, ctx: Ctx, app: ReturnType<typeof createApp>, evt = 0;

beforeEach(async () => {
  db = await pgliteDb();
  await migrate(db);
  pay = new FakePayments();
  ctx = { db, pay, config: { publicUrl: "https://infinitum.test", returnHosts: ["rchtch.app", "scalvs.app"], adminApiKey: ADMIN, tokenPayoutsEnabled: false } };
  app = createApp(ctx);
});

async function call(method: string, path: string, key?: string, body?: unknown) {
  const res = await app.request(path, {
    method, headers: { "content-type": "application/json", ...(key ? { authorization: `Bearer ${key}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json: any; try { json = JSON.parse(text); } catch { json = text; }
  return { status: res.status, body: json };
}
const webhook = (e: Omit<PaymentEvent, "eventId"> & { eventId?: string }) =>
  (app.request("/v1/webhooks/payments", { method: "POST", headers: { "stripe-signature": "test" },
    body: JSON.stringify({ eventId: e.eventId ?? `evt_${++evt}`, ...e }) }) as Promise<Response>).then((r) => r.json() as any);
const signup = async (name: string, kind = "business", referralCode?: string) =>
  (await call("POST", "/v1/signup", undefined, { name, kind, email: `${name.toLowerCase()}@x.test`, referralCode })).body as { orgId: string; apiKey: string };
const lastCheckout = () => [...pay.calls].reverse().find((c) => c.op.endsWith("Checkout"))!.args;

describe("catalog & auth", () => {
  it("publishes the catalog and rejects bad keys", async () => {
    const cat = await call("GET", "/v1/catalog");
    expect(cat.body.plans.length).toBeGreaterThan(30);
    expect(cat.body.hardware.some((h: any) => h.id === "throne_book_14")).toBe(true);
    expect((await call("GET", "/v1/me/entitlements", "ik_nope")).status).toBe(401);
    expect((await call("GET", "/v1/admin/reports/revenue", "wrong")).status).toBe(401);
  });
});

describe("$FIRM Suite", () => {
  it("comes with Cumulus://ID, Mail and Drive; a paid Drive plan still takes precedence", async () => {
    const cat = await call("GET", "/v1/catalog");
    const suite = cat.body.plans.find((p: any) => p.id === "firm_suite");
    expect(suite.features).toEqual(expect.arrayContaining(["cumulus_id", "cumulus_mail", "cumulus_drive", "any_token_swap"]));
    expect(suite.bundle).toContain("cumulus_drive_free");

    const { apiKey, orgId } = await signup("Satoshi", "individual");
    const ent = (await call("GET", "/v1/me/entitlements", apiKey)).body.products;
    const firm = ent.find((p: any) => p.product === "firm_wallet");
    expect(firm.plan).toBe("firm_suite");
    expect(firm.features).toEqual(expect.arrayContaining(["cumulus_id", "cumulus_mail", "cumulus_drive"]));
    const drive = ent.find((p: any) => p.product === "cumulus");
    expect(drive).toMatchObject({ plan: "cumulus_drive_free", grantedBy: "firm_suite", status: "bundled" });

    const co = await call("POST", "/v1/subscriptions/checkout", apiKey, { planId: "cumulus_drive_200" });
    expect(co.status).toBe(200);
    await webhook({ kind: "subscription.updated", orgId, planId: "cumulus_drive_200", status: "active", quantity: 1,
      providerSubscriptionId: "sub_drive", periodStart: new Date(), periodEnd: new Date(Date.now() + 864e5), cancelAtPeriodEnd: false } as any);
    const after = (await call("GET", "/v1/me/entitlements", apiKey)).body.products.find((p: any) => p.product === "cumulus");
    expect(after).toMatchObject({ plan: "cumulus_drive_200", grantedBy: null });
  });
});

describe("usage, free tiers and prepaid credits", () => {
  it("allows free allowance, blocks beyond it, then draws on credits; idempotent", async () => {
    const { apiKey, orgId } = await signup("Ada", "individual");
    const ok = await call("POST", "/v1/usage", apiKey, { meter: "scalvs_agent_task", quantity: 100, idempotencyKey: "u1" });
    expect(ok.body.billed_as).toBe("included");
    const blocked = await call("POST", "/v1/usage", apiKey, { meter: "scalvs_agent_task", quantity: 10, idempotencyKey: "u2" });
    expect(blocked.status).toBe(402);

    const co = await call("POST", "/v1/credits/checkout", apiKey, { packId: "credits_25" });
    expect(co.body.url).toContain("https://pay.test/");
    await webhook({ kind: "checkout.completed", sessionId: co.body.id, purpose: "credits", ref: "credits_25", orgId, amountCents: 2500 } as any);

    const paid = await call("POST", "/v1/usage", apiKey, { meter: "scalvs_agent_task", quantity: 10, idempotencyKey: "u2" });
    expect(paid.body.billed_as).toBe("credits");
    expect(Number(paid.body.cost_cents)).toBe(20);
    const replay = await call("POST", "/v1/usage", apiKey, { meter: "scalvs_agent_task", quantity: 10, idempotencyKey: "u2" });
    expect(replay.body.replay).toBe(true);
    const ent = await call("GET", "/v1/me/entitlements", apiKey);
    expect(ent.body.credits_cents).toBe(2480);
  });
});

describe("subscriptions", () => {
  it("checks out, activates via webhook, bills overage to the provider", async () => {
    const { apiKey, orgId } = await signup("Acme");
    const co = await call("POST", "/v1/subscriptions/checkout", apiKey, { planId: "scalvs_pro" });
    expect(co.status).toBe(200);
    expect(lastCheckout().trialDays).toBe(14);

    const sub = { kind: "subscription.updated", providerSubscriptionId: "sub_1", orgId, planId: "scalvs_pro", status: "active",
      quantity: 1, periodStart: new Date(Date.now() - 86400000), periodEnd: new Date(Date.now() + 86400000 * 29), cancelAtPeriodEnd: false };
    await webhook({ ...sub, eventId: "evt_sub" } as any);
    expect((await webhook({ ...sub, eventId: "evt_sub" } as any)).duplicate).toBe(true);

    const dup = await call("POST", "/v1/subscriptions/checkout", apiKey, { planId: "scalvs_pro" });
    expect(dup.status).toBe(409);

    await call("POST", "/v1/usage", apiKey, { meter: "scalvs_agent_task", quantity: 1990, idempotencyKey: "a" });
    const over = await call("POST", "/v1/usage", apiKey, { meter: "scalvs_agent_task", quantity: 30, idempotencyKey: "b" });
    expect(over.body.billed_as).toBe("overage");
    const reported = pay.calls.filter((c) => c.op === "reportUsage");
    expect(reported).toHaveLength(1);
    expect(reported[0].args.quantity).toBe(20); // only the part beyond the 2,000 allowance

    const report = await call("GET", "/v1/admin/reports/revenue", ADMIN);
    expect(report.body.recurring.byProduct.scalvs).toBe(2900);
  });

  it("enforces channels: sales-assisted needs approval, contract plans never self-serve, kinds are respected", async () => {
    const { apiKey, orgId } = await signup("Bank");
    expect((await call("POST", "/v1/subscriptions/checkout", apiKey, { planId: "eidetic_enterprise" })).body.error.code).toBe("approval_required");
    await call("POST", `/v1/admin/orgs/${orgId}/approve`, ADMIN, { products: ["eidetic"] });
    expect((await call("POST", "/v1/subscriptions/checkout", apiKey, { planId: "eidetic_enterprise" })).status).toBe(200);
    expect((await call("POST", "/v1/subscriptions/checkout", apiKey, { planId: "odin_suite" })).body.error.code).toBe("plan_not_available");
    const person = await signup("Bob", "individual");
    expect((await call("POST", "/v1/subscriptions/checkout", person.apiKey, { planId: "spectre_starter" })).status).toBe(403);
  });
});

describe("hardware", () => {
  it("takes a deposit, invoices the balance, then fulfils", async () => {
    const { apiKey, orgId } = await signup("Ops");
    const o = await call("POST", "/v1/orders", apiKey, { items: [{ sku: "orbis_kit", quantity: 2 }] });
    expect(o.body).toMatchObject({ totalCents: 499800, dueNowCents: 99960, balanceCents: 399840 });
    await webhook({ kind: "checkout.completed", sessionId: "cs", purpose: "order", ref: o.body.orderId, orgId, amountCents: 99960 } as any);
    expect((await call("GET", "/v1/orders", apiKey)).body[0].status).toBe("deposit_paid");
    expect((await call("POST", `/v1/admin/orders/${o.body.orderId}/fulfil`, ADMIN)).status).toBe(409);

    await call("POST", `/v1/admin/orders/${o.body.orderId}/invoice-balance`, ADMIN);
    await webhook({ kind: "invoice.paid", invoiceId: "in_1", customerId: `cus_${orgId}`, amountCents: 399840, metadata: { order_id: o.body.orderId } } as any);
    expect((await call("POST", `/v1/admin/orders/${o.body.orderId}/fulfil`, ADMIN)).body.status).toBe("fulfilled");
    expect((await call("POST", "/v1/orders", apiKey, { items: [{ sku: "daemon_warden_site", quantity: 1 }] })).body.error.code).toBe("contact_sales");
  });
});

describe("data marketplace", () => {
  it("requires consent, splits revenue, holds earnings, delists on withdrawal", async () => {
    const seller = await signup("Seller", "individual");
    const buyer = await signup("Retailer");
    const asset = { product: "hrvst", title: "Shelf gaps, 40 stores", permittedUses: ["retail_analytics"], priceCents: 10000, contentHash: "sha256:" + "f".repeat(64) };
    expect((await call("POST", "/v1/marketplace/assets", seller.apiKey, asset)).body.error.code).toBe("consent_required");
    await call("POST", "/v1/consents", seller.apiKey, { product: "hrvst" });
    const a = await call("POST", "/v1/marketplace/assets", seller.apiKey, asset);
    expect(a.status).toBe(201);

    expect((await call("POST", `/v1/marketplace/assets/${a.body.id}/licence`, buyer.apiKey, { use: "ad_targeting" })).body.error.code).toBe("use_not_permitted");
    const lic = await call("POST", `/v1/marketplace/assets/${a.body.id}/licence`, buyer.apiKey, { use: "retail_analytics" });
    await webhook({ kind: "checkout.completed", sessionId: "cs", purpose: "licence", ref: lic.body.licenceId, orgId: buyer.orgId, amountCents: 10000 } as any);
    expect((await call("GET", "/v1/earnings", seller.apiKey)).body).toEqual({ availableCents: 0, pendingCents: 7000 });

    const w = await call("DELETE", "/v1/consents/hrvst", seller.apiKey);
    expect(w.body.delisted).toBe(1);
    expect((await call("GET", "/v1/marketplace/hrvst/assets", buyer.apiKey)).body).toHaveLength(0);

    const report = await call("GET", "/v1/admin/reports/revenue", ADMIN);
    expect(report.body.marketplace).toMatchObject({ gmv: 10000, licenceTake: 3000 });
  });

  it("runs a funded mission end to end and pays out after the hold", async () => {
    const buyer = await signup("City", "government");
    const seller = await signup("Walker", "individual");
    const m = await call("POST", "/v1/missions", buyer.apiKey, { product: "hrvst", title: "Accessible entrances", brief: "Photograph ramps on Main St",
      permittedUse: "accessibility_mapping", rewardCents: 1500, slots: 2 });
    expect(m.body).toMatchObject({ budgetCents: 3000, feeCents: 600 });
    await webhook({ kind: "checkout.completed", sessionId: "cs", paymentIntentId: "pi_mission", purpose: "mission", ref: m.body.missionId, orgId: buyer.orgId, amountCents: 3600 } as any);

    await call("POST", "/v1/consents", seller.apiKey, { product: "hrvst" });
    const a = await call("POST", "/v1/marketplace/assets", seller.apiKey, { product: "hrvst", title: "Ramp at 12 Main", permittedUses: ["accessibility_mapping"], priceCents: 500, contentHash: "sha256:" + "a".repeat(64) });
    const s = await call("POST", `/v1/missions/${m.body.missionId}/submissions`, seller.apiKey, { assetId: a.body.id });
    expect((await call("POST", `/v1/submissions/${s.body.id}/review`, buyer.apiKey, { accept: true })).body.status).toBe("accepted");

    expect((await call("POST", "/v1/payout-account", seller.apiKey, { method: "token", walletAddress: "x", network: "solana" })).body.error.code).toBe("token_payouts_disabled");
    await call("POST", "/v1/payout-account", seller.apiKey, { method: "bank" });
    expect((await call("POST", "/v1/payouts", seller.apiKey)).body.error.code).toBe("below_minimum");
    await db.query("update earnings_ledger set available_at = now() - interval '1 day'"); // hold period passes
    const p = await call("POST", "/v1/payouts", seller.apiKey);
    expect(p.body).toMatchObject({ amountCents: 1500, status: "sent" });
    expect((await call("GET", "/v1/earnings", seller.apiKey)).body.availableCents).toBe(0);

    const c = await call("POST", `/v1/missions/${m.body.missionId}/cancel`, buyer.apiKey);
    expect(c.body.refundDueCents).toBe(1500 + 300);
    expect(c.body.refundIssued).toBe(true);
    expect(pay.calls.find((x) => x.op === "refund")!.args).toMatchObject({ paymentIntentId: "pi_mission", amountCents: 1800 });
  });

  it("does not pay the same earnings twice when recording a successful transfer fails", async () => {
    const seller = await signup("Payee", "individual");
    await call("POST", "/v1/payout-account", seller.apiKey, { method: "bank" });
    await db.query(
      "insert into earnings_ledger (id, org_id, delta_cents, source, ref, available_at) values ($1,$2,$3,'mission',$4, now() - interval '1 day')",
      ["ern_seed", seller.orgId, 5000, "seed"],
    );
    const original = ctx.db.query.bind(ctx.db);
    let tripped = false;
    ctx.db.query = ((sql: string, params?: unknown[]) => {
      if (!tripped && sql.includes("status='sent'")) {
        tripped = true;
        throw new Error("db blip");
      }
      return original(sql, params);
    }) as typeof ctx.db.query;
    const res = await call("POST", "/v1/payouts", seller.apiKey);
    ctx.db.query = original;
    expect(res.status).toBe(500);
    expect(pay.calls.filter((c) => c.op === "transfer")).toHaveLength(1);
    expect((await call("GET", "/v1/earnings", seller.apiKey)).body.availableCents).toBe(0);
    const [row] = await db.query<{ status: string }>("select status from payouts where org_id=$1", [seller.orgId]);
    expect(row.status).toBe("sent");
  });

  it("restores earnings when the bank transfer itself fails", async () => {
    const seller = await signup("Payee", "individual");
    await call("POST", "/v1/payout-account", seller.apiKey, { method: "bank" });
    await db.query(
      "insert into earnings_ledger (id, org_id, delta_cents, source, ref, available_at) values ($1,$2,$3,'mission',$4, now() - interval '1 day')",
      ["ern_seed", seller.orgId, 5000, "seed"],
    );
    pay.transfer = async () => { throw new Error("stripe down"); };
    const res = await call("POST", "/v1/payouts", seller.apiKey);
    expect(res.status).toBe(500);
    expect((await call("GET", "/v1/earnings", seller.apiKey)).body.availableCents).toBe(5000);
    const [row] = await db.query<{ status: string }>("select status from payouts where org_id=$1", [seller.orgId]);
    expect(row.status).toBe("failed");
  });
});

describe("contracts, licences, referrals", () => {
  it("restricts ODIN° to verified defense orgs and bills milestones", async () => {
    const corp = await signup("Corp");
    const r = await call("POST", "/v1/admin/engagements", ADMIN, { orgId: corp.orgId, practice: "odin", title: "x", milestones: [{ title: "m", amountCents: 100 }] });
    expect(r.body.error.code).toBe("defense_only");

    const army = await signup("Army", "military");
    await call("POST", `/v1/admin/orgs/${army.orgId}/verify-defense`, ADMIN);
    const e = await call("POST", "/v1/admin/engagements", ADMIN, { orgId: army.orgId, practice: "odin", title: "ODIN° pilot",
      milestones: [{ title: "01 Assessment", amountCents: 25000000 }] });
    expect(e.body.confidential).toBe(true);
    const ms = e.body.milestones[0];
    await call("POST", `/v1/admin/milestones/${ms.id}/invoice`, ADMIN);
    expect(pay.calls.find((c) => c.op === "sendInvoice")!.args.description).not.toContain("ODIN");
    await webhook({ kind: "invoice.paid", invoiceId: "in_m", customerId: `cus_${army.orgId}`, amountCents: 25000000, metadata: { milestone_id: ms.id } } as any);
    expect((await call("GET", "/v1/engagements", army.apiKey)).body[0].milestones[0].status).toBe("paid");
  });

  it("issues verifiable licences and detects tampering and revocation", async () => {
    const o = await signup("DC");
    const l = await call("POST", "/v1/admin/licences", ADMIN, { orgId: o.orgId, product: "throne", edition: "rack_onprem", seats: 50, days: 365 });
    expect((await call("POST", "/v1/licences/verify", undefined, { token: l.body.token })).body.valid).toBe(true);
    const tampered = l.body.token.replace(/^./, (c: string) => (c === "e" ? "f" : "e"));
    expect((await call("POST", "/v1/licences/verify", undefined, { token: tampered })).body.valid).toBe(false);
    await call("DELETE", `/v1/admin/licences/${l.body.licenceId}`, ADMIN);
    expect((await call("POST", "/v1/licences/verify", undefined, { token: l.body.token })).body.reason).toBe("revoked");
  });

  it("pays referral commission on referred invoices", async () => {
    const partner = await signup("Partner");
    await call("POST", "/v1/referral-codes", partner.apiKey, { code: "veil-launch" });
    const referred = await signup("NewCo", "business", "VEIL-LAUNCH");
    await call("POST", "/v1/billing/portal", referred.apiKey); // creates the customer
    await webhook({ kind: "invoice.paid", invoiceId: "in_r", customerId: `cus_${referred.orgId}`, amountCents: 9900, metadata: {} } as any);
    expect((await call("GET", "/v1/earnings", partner.apiKey)).body.pendingCents).toBe(1980);
  });
});

describe("refunds and disputes", () => {
  it("reverses the contributor share and ends the licence on a full refund, exactly once", async () => {
    const seller = await signup("S", "individual");
    const buyer = await signup("B");
    await call("POST", "/v1/consents", seller.apiKey, { product: "hrvst" });
    const a = await call("POST", "/v1/marketplace/assets", seller.apiKey, { product: "hrvst", title: "t", permittedUses: ["research"], priceCents: 10000, contentHash: "sha256:" + "b".repeat(64) });
    const lic = await call("POST", `/v1/marketplace/assets/${a.body.id}/licence`, buyer.apiKey, { use: "research" });
    await webhook({ kind: "checkout.completed", sessionId: "cs", paymentIntentId: "pi_l", purpose: "licence", ref: lic.body.licenceId, orgId: buyer.orgId, amountCents: 10000 } as any);

    const r = await webhook({ kind: "charge.refunded", eventId: "evt_ref1", paymentIntentId: "pi_l", amountCents: 10000 } as any);
    expect(r.refund).toMatchObject({ applied: true, contributorReversedCents: 7000, full: true });
    expect((await webhook({ kind: "charge.refunded", eventId: "evt_ref1", paymentIntentId: "pi_l", amountCents: 10000 } as any)).duplicate).toBe(true);
    expect((await webhook({ kind: "charge.refunded", paymentIntentId: "pi_l", amountCents: 10000 } as any)).refund.applied).toBe(false);

    const e = (await call("GET", "/v1/earnings", seller.apiKey)).body;
    expect(e.availableCents + e.pendingCents).toBe(0); // the held 7,000 is cancelled out by the reversal
    const report = await call("GET", "/v1/admin/reports/revenue", ADMIN);
    expect(report.body.marketplace.gmv).toBe(0);
  });

  it("claws back credits pro rata on a partial refund, blocking paid usage when negative", async () => {
    const { apiKey, orgId } = await signup("C", "individual");
    await webhook({ kind: "checkout.completed", sessionId: "cs_c", paymentIntentId: "pi_c", purpose: "credits", ref: "credits_100", orgId, amountCents: 10000 } as any);
    expect((await call("GET", "/v1/me/entitlements", apiKey)).body.credits_cents).toBe(10500);
    await webhook({ kind: "charge.refunded", paymentIntentId: "pi_c", amountCents: 5000 } as any);
    expect((await call("GET", "/v1/me/entitlements", apiKey)).body.credits_cents).toBe(5250);
    await webhook({ kind: "charge.refunded", paymentIntentId: "pi_c", amountCents: 10000 } as any);
    expect((await call("GET", "/v1/me/entitlements", apiKey)).body.credits_cents).toBe(0);
  });

  it("marks an order refunded when a chargeback is lost", async () => {
    const { apiKey, orgId } = await signup("D");
    const o = await call("POST", "/v1/orders", apiKey, { items: [{ sku: "eon_wearable", quantity: 1 }] });
    await webhook({ kind: "checkout.completed", sessionId: "cs", paymentIntentId: "pi_o", purpose: "order", ref: o.body.orderId, orgId, amountCents: o.body.dueNowCents } as any);
    await webhook({ kind: "dispute.lost", paymentIntentId: "pi_o", amountCents: o.body.dueNowCents } as any);
    expect((await call("GET", "/v1/orders", apiKey)).body[0].status).toBe("refunded");
  });
});

describe("app return URLs and CORS", () => {
  it("sends Checkout back to an allow-listed product URL", async () => {
    const { apiKey } = await signup("Ret");
    const co = await call("POST", "/v1/subscriptions/checkout", apiKey, { planId: "scalvs_pro", returnUrl: "https://scalvs.app/account?tab=plan#x" });
    expect(co.status).toBe(200);
    expect(lastCheckout().successUrl).toBe("https://scalvs.app/account?tab=plan&billing=success&session_id={CHECKOUT_SESSION_ID}");
    expect(lastCheckout().cancelUrl).toBe("https://scalvs.app/account?tab=plan&billing=cancelled");

    await call("POST", "/v1/credits/checkout", apiKey, { packId: "credits_25", returnUrl: "https://www.rchtch.app/" });
    expect(lastCheckout().successUrl).toBe("https://www.rchtch.app/?billing=success&session_id={CHECKOUT_SESSION_ID}");

    await call("POST", "/v1/billing/portal", apiKey, { returnUrl: "https://rchtch.app/settings" });
    expect([...pay.calls].reverse().find((c) => c.op === "billingPortal")!.args.returnUrl).toBe("https://rchtch.app/settings");
  });

  it("falls back to the API's own pages and rejects foreign or insecure return URLs", async () => {
    const { apiKey } = await signup("Ret2");
    await call("POST", "/v1/credits/checkout", apiKey, { packId: "credits_25" });
    expect(lastCheckout().successUrl).toBe("https://infinitum.test/billing/success?session_id={CHECKOUT_SESSION_ID}");
    for (const returnUrl of ["https://evil.example/", "http://scalvs.app/", "https://scalvs.app.evil.example/", "https://u:p@scalvs.app/", "nope"]) {
      const r = await call("POST", "/v1/subscriptions/checkout", apiKey, { planId: "scalvs_pro", returnUrl });
      expect(r.status, returnUrl).toBe(400);
    }
    const page = await app.request("/billing/success");
    expect(page.status).toBe(200);
    expect(await page.text()).toContain("Payment complete");
  });

  it("grants CORS only to allow-listed origins and never on admin routes", async () => {
    const pre = (path: string, origin: string) => app.request(path, { method: "OPTIONS",
      headers: { origin, "access-control-request-method": "POST" } });
    expect((await pre("/v1/subscriptions/checkout", "https://scalvs.app")).headers.get("access-control-allow-origin")).toBe("https://scalvs.app");
    expect((await pre("/v1/subscriptions/checkout", "https://evil.example")).headers.get("access-control-allow-origin")).toBeNull();
    expect((await pre("/v1/admin/reports/revenue", "https://scalvs.app")).headers.get("access-control-allow-origin")).toBeNull();
  });

  it("allow-lists METATRON¹⁰⁰ at mttrn.app in the deployed RETURN_HOSTS", async () => {
    const vars = readFileSync(new URL("../wrangler.jsonc", import.meta.url), "utf8").match(/"RETURN_HOSTS":\s*"([^"]+)"/);
    const returnHosts = vars![1].split(",");
    expect(returnHosts).toContain("mttrn.app");
    const mttrn = createApp({ ...ctx, config: { ...ctx.config, returnHosts } });
    const pre = await mttrn.request("/v1/subscriptions/checkout", { method: "OPTIONS",
      headers: { origin: "https://mttrn.app", "access-control-request-method": "POST" } });
    expect(pre.headers.get("access-control-allow-origin")).toBe("https://mttrn.app");
  });
});
