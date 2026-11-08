# Infinitum revenue API

Billing and monetization backend for every Infinitum product: subscriptions, usage metering, prepaid credits,
hardware orders and deposits, a consented data marketplace with missions and payouts, milestone-billed
engagements (including ODIN°), signed on-prem licences, referrals and a revenue report.

See [MONETIZATION.md](MONETIZATION.md) for the strategy behind it.

**Stack:** TypeScript · Hono · Postgres (Supabase) · Stripe (Checkout, Billing Meters, Invoicing, Connect) · Cloudflare Workers

## Run locally (no accounts needed)

```bash
npm install
npm test          # 13 end-to-end API tests on embedded Postgres + fake payments
npm run dev       # http://localhost:8787, data in ./.data, fake payments
```

## Hosting: Cloudflare Workers

`src/worker.ts` is the Worker entry, configured in `wrangler.jsonc`. Each request boots the Hono app with its own short-lived Postgres connection and closes it once the response is sent. The cron trigger (`*/15 * * * *`) runs the Stripe usage flush that used to be a Vercel Cron.

```bash
npm run dev:worker       # wrangler dev (needs DATABASE_URL in .dev.vars)
npm run build            # bundle check: wrangler deploy --dry-run --outdir dist
npm run deploy:preview   # upload a preview version (does not go live)
npm run deploy           # production deploy, which needs sign-off first
```

`npm run dev` still runs the plain Node server on embedded PGlite, with no accounts needed.

## Database: Supabase

Data lives in the shared **Infinitum Corp** Supabase project (ref `ivqiskeziohbbtkcvlun`) in the `infinitum_revenue` schema. Every table has RLS on with no policies, and `anon` and `authenticated` have no grants, so the Data API keys can't reach billing data. Only the Worker can, over `DATABASE_URL`. No Supabase key goes to any client.

- `supabase/migrations/20261107202600_infinitum_revenue.sql` is generated from `src/schema.ts` with `npm run db:gen`, and a test fails if the two drift. To change the schema, edit `src/schema.ts`, run `db:gen`, then add the change as a **new** migration file. Don't edit an applied one.
- Apply it with `npm run db:push` (`supabase db push`, after `supabase link --project-ref ivqiskeziohbbtkcvlun`). Because the project is shared, the CLI will report migration versions that other products pushed. If it does, apply this file in the SQL editor or with `psql "$DATABASE_URL" -f <file>` instead.
- The Worker never migrates at request time. The Node server (`npm start` with `DATABASE_URL`) creates the schema and applies `src/schema.ts` on boot, as before.

## Environment variables

Set secrets with `npx wrangler secret put <NAME>` (or in the Cloudflare dashboard). For `wrangler dev`, put them in `.dev.vars`, which is git-ignored. Never commit them.

| Variable | Kind | Purpose |
|---|---|---|
| `DATABASE_URL` | secret, **required** | Supabase Postgres via the **session pooler** (port 5432) or a direct connection, for the Infinitum Corp project. The session-level `search_path` has to hold for the whole request, so don't use the transaction pooler (6543). |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | secret, **required** | Real payments. Production refuses to start with the fake provider (`NODE_ENV=production` is set in `wrangler.jsonc`). |
| `ADMIN_API_KEY` | secret | At least 32 chars. Operator key for `/v1/admin/*` and for product backends reporting usage. |
| `LICENCE_SIGNING_KEY` | secret | Ed25519 PKCS8 PEM (`openssl genpkey -algorithm ed25519`). Needed for licences to stay valid across requests. Without it each request signs with a throwaway key. |
| `CRON_SECRET` | secret | Protects the manual `GET /cron/flush-usage` route. The Worker's own cron trigger doesn't need it. |
| `PUBLIC_URL` | var | Where Checkout sends customers back to. |
| `STRIPE_AUTOMATIC_TAX` | var | `true` to enable Stripe Tax (default `false` in `wrangler.jsonc`). |
| `TOKEN_PAYOUTS_ENABLED` | var | Leave `false` until the token programme has had legal review. |

## Go live

1. `STRIPE_SECRET_KEY=sk_test_... npm run stripe:sync` creates products, prices (lookup keys `inf_<plan>`) and billing meters. Run it again whenever you change `src/catalog.ts`.
2. In Stripe, add a webhook to `https://<api>/v1/webhooks/payments` for `checkout.session.completed`, `customer.subscription.*`, `invoice.paid`, `invoice.payment_failed`, `charge.refunded` and `charge.dispute.closed`. Enable Connect (Express) for contributor payouts.
3. Set the secrets above, apply the Supabase migration, then deploy with `npm run deploy` (production, which needs sign-off first).

## API

Auth: `Authorization: Bearer <org API key>` (from signup) or the admin key for `/v1/admin/*`.

| Method & path | What it does |
|---|---|
| `GET /v1/catalog` | Plans, meters, hardware, marketplace terms, credit packs |
| `POST /v1/signup` | Create an org (+ optional `referralCode`) → API key |
| `GET /v1/me/entitlements` | Plan, features, allowance used per product, credit balance |
| `POST /v1/subscriptions/checkout` | Checkout URL for a plan (`planId`, `quantity` for seats) |
| `POST /v1/billing/portal` | Stripe customer portal (upgrade, cancel, payment method) |
| `POST /v1/usage` | Record usage (`meter`, `quantity`, `idempotencyKey`) → included / overage / credits, or 402 |
| `POST /v1/credits/checkout` | Buy a prepaid credit pack |
| `POST /v1/orders`, `GET /v1/orders` | Hardware order (full payment or deposit) |
| `POST /v1/consents`, `DELETE /v1/consents/:product` | Grant / withdraw data-licensing consent |
| `POST /v1/marketplace/assets` | List a consented data asset |
| `GET /v1/marketplace/:product/assets` | Browse live assets |
| `POST /v1/marketplace/assets/:id/licence` | Buy a licence for a permitted use |
| `POST /v1/missions`, `GET /v1/marketplace/:product/missions` | Post / browse buyer-funded missions |
| `POST /v1/missions/:id/submissions`, `POST /v1/submissions/:id/review`, `POST /v1/missions/:id/cancel` | Mission workflow |
| `GET /v1/earnings`, `POST /v1/payout-account`, `POST /v1/payouts` | Contributor / partner earnings and payouts |
| `POST /v1/referral-codes` | Create a partner code |
| `GET /v1/engagements` | Client view of engagements and milestones |
| `POST /v1/licences/verify`, `GET /v1/licences/public-key` | Verify a signed licence (devices can verify offline with the public key) |
| `POST /v1/webhooks/payments` | Stripe webhook (signature-verified, processed once) |
| **Admin** | `orgs/:id/approve`, `orgs/:id/verify-defense`, `orgs/:id/usage`, `engagements`, `milestones/:id/invoice`, `orders/:id/invoice-balance`, `orders/:id/fulfil`, `licences` (issue/revoke), `usage/flush`, `reports/revenue` |

Product backends (SCALVS.ai, Cumulus, etc.) report usage with `POST /v1/admin/orgs/:orgId/usage` using the admin key, and gate
features with `GET /v1/me/entitlements` (or a 402 from `/usage`).

## Known gaps

- Refunds and lost disputes are reversed automatically (credits clawed back, contributor share reversed, licence/order/mission closed on a full refund). Money already paid out to a contributor is not pulled back from their bank; it becomes a negative balance recovered from future earnings.
- Cancelling a mission refunds the unspent budget automatically; if the original payment can't be matched, the response says `refundIssued: false` and the refund must be issued manually.
- Token settlement (on-chain payouts via FIRM Wallet) is not implemented; token payouts stay `pending`.
- No end-user UI; this is the API the site and products call.
- Not yet run against live Stripe; the Stripe adapter is typechecked, but only the fake provider is exercised by tests.
