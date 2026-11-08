import { Hono, type Context, type Next } from "hono";
import { cors } from "hono/cors";
import { timingSafeEqual } from "node:crypto";
import { z, ZodError } from "zod";
import { AppError, fail, type Ctx } from "./context.js";
import { CREDIT_PACKS, HARDWARE, MARKETPLACES, METERS, PRODUCTS } from "./catalog.js";
import * as billing from "./services/billing.js";
import * as commerce from "./services/commerce.js";
import * as market from "./services/marketplace.js";
import * as contracts from "./services/contracts.js";
import { handlePaymentEvent } from "./services/webhooks.js";
import { revenueReport } from "./services/reports.js";

type Env = { Variables: { org: billing.Org } };

const kind = z.enum(["individual", "business", "government", "military"]);
const product = z.enum(PRODUCTS);

export function createApp(ctx: Ctx) {
  const app = new Hono<Env>();
  const { db } = ctx;

  app.onError((err, c) => {
    if (err instanceof AppError) return c.json({ error: { code: err.code, message: err.message } }, err.status as any);
    if (err instanceof ZodError) return c.json({ error: { code: "invalid_request", message: "Invalid request", issues: err.issues } }, 400);
    console.error(err);
    return c.json({ error: { code: "internal", message: "Internal error" } }, 500);
  });

  const body = async <T extends z.ZodTypeAny>(c: Context, schema: T): Promise<z.infer<T>> =>
    schema.parse(await c.req.json().catch(() => ({})));

  const bearer = (c: Context) => c.req.header("authorization")?.replace(/^Bearer\s+/i, "") ?? "";

  const requireOrg = async (c: Context<Env>, next: Next) => {
    const org = await billing.orgForApiKey(db, bearer(c));
    if (!org) fail(401, "unauthorized", "Valid API key required");
    c.set("org", org!);
    await next();
  };

  const isAdminKey = (k: string) => {
    const a = Buffer.from(k), b = Buffer.from(ctx.config.adminApiKey);
    return a.length === b.length && b.length > 0 && timingSafeEqual(a, b);
  };
  const requireAdmin = async (c: Context, next: Next) => {
    if (!isAdminKey(bearer(c))) fail(401, "unauthorized", "Admin key required");
    await next();
  };

  // Product web apps (rchtch.app, scalvs.app, mttrn.app, …) call the customer API from the browser.
  // Only allow-listed https origins get CORS; admin and webhook routes never do.
  const productCors = cors({
    origin: (origin) => (billing.isAllowedOrigin(ctx, origin) ? origin : null),
    allowHeaders: ["authorization", "content-type"], allowMethods: ["GET", "POST", "OPTIONS"], maxAge: 600,
  });
  app.use("/v1/*", async (c, next) => {
    const p = c.req.path;
    if (p.startsWith("/v1/admin") || p.startsWith("/v1/webhooks")) return next();
    return productCors(c, next);
  });

  // ------------------------------------------------------------ public
  app.get("/health", (c) => c.json({ ok: true }));

  // Fallback landing pages when Checkout / the portal was opened without an app returnUrl.
  const page = (title: string, text: string) => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#fff;color:#000;font:16px/1.5 system-ui,sans-serif}
main{max-width:28rem;padding:2rem;border:1px solid #000}h1{font-size:1.25rem;margin:0 0 .5rem}p{margin:0}</style></head>
<body><main><h1>${title}</h1><p>${text}</p></main></body></html>`;
  app.get("/billing/success", (c) => c.html(page("Payment complete", "Thank you. Your plan is active. You can close this tab and return to the app.")));
  app.get("/billing/cancelled", (c) => c.html(page("Checkout cancelled", "No charge was made. You can close this tab and return to the app.")));
  app.get("/billing", (c) => c.html(page("Billing updated", "Your billing settings are saved. You can close this tab and return to the app.")));

  app.get("/v1/catalog", (c) => c.json({
    plans: billing.publicPlans(), meters: METERS, hardware: HARDWARE, marketplaces: MARKETPLACES, creditPacks: CREDIT_PACKS,
  }));

  app.post("/v1/signup", async (c) => {
    const b = await body(c, z.object({ name: z.string().min(1).max(200), email: z.email().optional(), kind: kind.default("business"),
      referralCode: z.string().max(32).optional() }));
    return c.json(await billing.createOrg(ctx, b), 201);
  });

  app.post("/v1/webhooks/payments", async (c) => {
    let event;
    try { event = await ctx.pay.parseWebhook(await c.req.text(), c.req.header("stripe-signature")); }
    catch { return c.json({ error: { code: "bad_signature", message: "Webhook signature verification failed" } }, 400); }
    return c.json({ received: true, ...(await handlePaymentEvent(ctx, event)) });
  });

  app.get("/v1/licences/public-key", (c) => c.text(contracts.licencePublicKeyPem(ctx)));
  app.post("/v1/licences/verify", async (c) => {
    const b = await body(c, z.object({ token: z.string().min(10) }));
    return c.json(await contracts.verifyLicence(ctx, b.token));
  });

  // ------------------------------------------------------------ operator (admin key) — registered before /v1 so org auth does not intercept it
  const admin = new Hono();
  admin.use("*", requireAdmin);

  admin.post("/orgs/:id/approve", async (c) => {
    const b = await body(c, z.object({ products: z.array(product).min(1) }));
    const [o] = await db.query(`update orgs set approved_products = (select array(select distinct unnest(approved_products || $2::text[])))
      where id=$1 returning id, approved_products`, [c.req.param("id"), b.products]);
    return o ? c.json(o) : fail(404, "org_not_found", "Organisation not found");
  });
  admin.post("/orgs/:id/verify-defense", async (c) => {
    const [o] = await db.query("update orgs set verified_defense=true where id=$1 and kind='military' returning id, verified_defense", [c.req.param("id")]);
    return o ? c.json(o) : fail(400, "not_military", "Only military organisations can be defense-verified");
  });
  // Product backends report usage on behalf of an org with the platform key.
  admin.post("/orgs/:id/usage", async (c) => {
    const org = await billing.getOrg(db, c.req.param("id"));
    return c.json(await billing.recordUsage(ctx, org, await body(c, usageBody)));
  });

  admin.post("/engagements", async (c) => {
    const b = await body(c, z.object({ orgId: z.string(), practice: z.string(), title: z.string().min(1), confidential: z.boolean().optional(),
      milestones: z.array(z.object({ title: z.string().min(1), amountCents: z.number().int(), dueDays: z.number().int().min(0).max(120).optional() })).min(1) }));
    return c.json(await contracts.createEngagement(ctx, b), 201);
  });
  admin.post("/milestones/:id/invoice", async (c) => c.json(await contracts.invoiceMilestone(ctx, c.req.param("id"))));
  admin.post("/orders/:id/invoice-balance", async (c) => c.json(await commerce.invoiceOrderBalance(ctx, c.req.param("id"))));
  admin.post("/orders/:id/fulfil", async (c) => c.json(await commerce.markOrderFulfilled(ctx, c.req.param("id"))));

  admin.post("/licences", async (c) => {
    const b = await body(c, z.object({ orgId: z.string(), product, edition: z.string(), seats: z.number().int().min(1),
      days: z.number().int().min(1).max(3650), deviceFingerprint: z.string().optional() }));
    return c.json(await contracts.issueLicence(ctx, b), 201);
  });
  admin.delete("/licences/:id", async (c) => c.json({ revoked: (await contracts.revokeLicence(db, c.req.param("id"))).length === 1 }));

  admin.post("/usage/flush", async (c) => c.json(await billing.flushUnreportedUsage(ctx)));
  admin.get("/reports/revenue", async (c) => c.json(await revenueReport(db)));

  app.route("/v1/admin", admin);

  // ------------------------------------------------------------ customer (org API key)
  const v1 = new Hono<Env>();
  v1.use("*", requireOrg);

  v1.get("/me/entitlements", async (c) => c.json(await billing.entitlements(db, c.get("org"))));

  v1.post("/subscriptions/checkout", async (c) => {
    const b = await body(c, z.object({ planId: z.string(), quantity: z.number().int().min(1).max(10000).default(1),
      returnUrl: z.string().max(2000).optional() }));
    return c.json(await billing.startSubscriptionCheckout(ctx, c.get("org"), b.planId, b.quantity, b.returnUrl));
  });

  v1.post("/billing/portal", async (c) => {
    const b = await body(c, z.object({ returnUrl: z.string().max(2000).optional() }));
    const returnUrl = billing.portalReturnUrl(ctx, b.returnUrl);
    const org = c.get("org");
    const cid = await billing.ensureCustomerId(ctx, org);
    return c.json({ url: await ctx.pay.billingPortal(cid, returnUrl) });
  });

  const usageBody = z.object({ meter: z.string(), quantity: z.number().positive(), idempotencyKey: z.string().min(1).max(200) });
  v1.post("/usage", async (c) => c.json(await billing.recordUsage(ctx, c.get("org"), await body(c, usageBody))));

  v1.post("/credits/checkout", async (c) => {
    const b = await body(c, z.object({ packId: z.string(), returnUrl: z.string().max(2000).optional() }));
    return c.json(await billing.startCreditTopup(ctx, c.get("org"), b.packId, b.returnUrl));
  });

  v1.post("/orders", async (c) => {
    const b = await body(c, z.object({ items: z.array(z.object({ sku: z.string(), quantity: z.number().int().min(1).max(1000) })).min(1),
      shipping: z.record(z.string(), z.unknown()).optional() }));
    return c.json(await commerce.createOrder(ctx, c.get("org"), b), 201);
  });
  v1.get("/orders", async (c) => c.json(await commerce.listOrders(db, c.get("org").id)));

  v1.post("/consents", async (c) => {
    const b = await body(c, z.object({ product, purpose: z.string().default("data_licensing") }));
    return c.json(await market.grantConsent(db, c.get("org"), b.product, b.purpose), 201);
  });
  v1.delete("/consents/:product", async (c) =>
    c.json(await market.withdrawConsent(db, c.get("org"), c.req.param("product"), c.req.query("purpose") ?? "data_licensing")));

  v1.post("/marketplace/assets", async (c) => {
    const b = await body(c, z.object({ product, title: z.string().min(1).max(200), description: z.string().max(5000).optional(),
      permittedUses: z.array(z.string().min(1)).min(1), priceCents: z.number().int(), contentHash: z.string().min(16),
      expiresAt: z.iso.datetime().optional() }));
    return c.json(await market.listAsset(db, c.get("org"), b), 201);
  });
  v1.get("/marketplace/:product/assets", async (c) => c.json(await market.browseAssets(db, c.req.param("product"))));
  v1.post("/marketplace/assets/:id/licence", async (c) => {
    const b = await body(c, z.object({ use: z.string() }));
    return c.json(await market.buyLicence(ctx, c.get("org"), c.req.param("id"), b.use));
  });

  v1.post("/missions", async (c) => {
    const b = await body(c, z.object({ product, title: z.string().min(1), brief: z.string().min(1), permittedUse: z.string(),
      rewardCents: z.number().int(), slots: z.number().int() }));
    return c.json(await market.createMission(ctx, c.get("org"), b), 201);
  });
  v1.get("/marketplace/:product/missions", async (c) => c.json(await market.openMissions(db, c.req.param("product"))));
  v1.post("/missions/:id/submissions", async (c) => {
    const b = await body(c, z.object({ assetId: z.string() }));
    return c.json(await market.submitToMission(db, c.get("org"), c.req.param("id"), b.assetId), 201);
  });
  v1.post("/submissions/:id/review", async (c) => {
    const b = await body(c, z.object({ accept: z.boolean() }));
    return c.json(await market.reviewSubmission(db, c.get("org"), c.req.param("id"), b.accept));
  });
  v1.post("/missions/:id/cancel", async (c) => c.json(await market.cancelMission(ctx, c.get("org"), c.req.param("id"))));

  v1.get("/earnings", async (c) => c.json(await market.earningsBalance(db, c.get("org").id)));
  v1.post("/payout-account", async (c) => {
    const b = await body(c, z.object({ method: z.enum(["bank", "token"]), walletAddress: z.string().optional(), network: z.string().optional() }));
    return c.json(await market.setupPayoutAccount(ctx, c.get("org"), b));
  });
  v1.post("/payouts", async (c) => c.json(await market.requestPayout(ctx, c.get("org"))));

  v1.post("/referral-codes", async (c) => {
    const b = await body(c, z.object({ code: z.string() }));
    return c.json(await contracts.createReferralCode(db, c.get("org"), b.code), 201);
  });

  v1.get("/engagements", async (c) => c.json(await contracts.listEngagements(db, c.get("org").id)));

  app.route("/v1", v1);


  // Manual / external trigger with `Authorization: Bearer $CRON_SECRET`. On
  // Cloudflare the Worker's cron trigger calls the same flush directly.
  app.get("/cron/flush-usage", async (c) => {
    if (!process.env.CRON_SECRET || bearer(c) !== process.env.CRON_SECRET) fail(401, "unauthorized", "Cron secret required");
    return c.json(await billing.flushUnreportedUsage(ctx));
  });

  return app;
}
