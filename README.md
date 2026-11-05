# Infinitum revenue API

Billing and monetization backend for every Infinitum product: subscriptions, usage metering, prepaid credits,
hardware orders and deposits, a consented data marketplace with missions and payouts, milestone-billed
engagements (including ODIN°), signed on-prem licences, referrals and a revenue report.

See [MONETIZATION.md](MONETIZATION.md) for the strategy behind it.

**Stack:** TypeScript · Hono · Postgres (Neon/any) · Stripe (Checkout, Billing Meters, Invoicing, Connect) · Vercel

## Run locally (no accounts needed)

```bash
npm install
npm test          # 13 end-to-end API tests on embedded Postgres + fake payments
npm run dev       # http://localhost:8787, data in ./.data, fake payments
```

## Environment

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | Postgres connection string (required on Vercel; e.g. Neon from the Marketplace) |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Real payments. Without them the fake provider is used (refused when `NODE_ENV=production`) |
| `ADMIN_API_KEY` | ≥32 chars. Operator key for `/v1/admin/*` and for product backends reporting usage |
| `PUBLIC_URL` | Where Checkout returns customers |
| `LICENCE_SIGNING_KEY` | Ed25519 PKCS8 PEM (`openssl genpkey -algorithm ed25519`). Required for licences to survive restarts |
| `CRON_SECRET` | Protects `/cron/flush-usage` (Vercel Cron, every 15 min) |
| `STRIPE_AUTOMATIC_TAX` | `true` to enable Stripe Tax |
| `TOKEN_PAYOUTS_ENABLED` | Leave `false` until legal review of the token programme |

## Go live

1. `STRIPE_SECRET_KEY=sk_test_... npm run stripe:sync` creates products, prices (lookup keys `inf_<plan>`) and billing meters. Run it again whenever you change `src/catalog.ts`.
2. In Stripe, add a webhook to `https://<api>/v1/webhooks/payments` for `checkout.session.completed`, `customer.subscription.*`, `invoice.paid`, `invoice.payment_failed`, `charge.refunded` and `charge.dispute.closed`. Enable Connect (Express) for contributor payouts.
3. Deploy: `vercel deploy` with the env vars above.

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
