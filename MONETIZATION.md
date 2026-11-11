# Infinitum monetization map

Every revenue stream across the portfolio on [infinitumcorporation.ai](https://infinitumcorporation.ai), and where the backend implements it.
Prices are **proposed launch prices** (in `src/catalog.ts`), set to be reviewed, not final decisions.

**Last reviewed:** 2026-11-09. The revenue API runs on Cloudflare Workers with the shared Supabase Postgres.

*Last updated 9 Nov 2026: added the $FIRM Suite and $FIRM Wallet, @conjure live predictions and the Genesis Drop. Also flagged the conflict between the site's data-sharing split and the backend's (§4). Later the same day: added the CryptoPublicOffering™ launch (§5), the REVshare terms (§6) and distribution, hosting and the GCP free-tier policy (§7). Then: DNS for both corporate domains moved to Cloudflare, with the registrar transfer due from 7 Jan 2027 (§7, next move #16). Later still: **Infinitum tokens are Solana-only** (§5 rewritten), and the Genesis Drop status and deploy blockers are updated (§2, §7, next moves #17–#18). Last: added the AWS project and its Free-plan policy (§7, next moves #20–#21). Then: corporate email on Cloudflare Email Routing (§7), and corrections from a check against the code: one shared waitlist KV (§5), per-program earnings holds including 30 days for referrals (§6), the missing downloads build script and Workers Paid (§7, next move #22). 10 Nov 2026: merged with main (§0 status table, 85% data share everywhere); §4 now records that decision, §6 shows 85/15, and next moves are renumbered.*

## 0. Where things stand (2026-11-09)

| Product | Domain | State | Can it take money today? | In `RETURN_HOSTS`? |
|---|---|---|---|---|
| SCALVS.ai™ | scalvs.app | **Live and public.** Agents and CONJURE™ forecasts run on Workers AI. No provider key is needed. POSTs are rate-limited to 60 per minute per IP. | Not yet. No Checkout wired in the app. | yes |
| :\ARCHITECT\ (catalog id `cndctr`) | rchtch.app | Live and public | Plans in the catalog. Stripe shows the :\ARCHITECT\ name. | yes |
| Firmament Network™ / $FIRM Wallet™ | firmnetwork.app, firmwallet.app | Live and public | $FIRM Suite™ plan (free, bundles Cumulus://ID™, Mail and Drive) | no, add before checkout |
| VEIL</> | infinitumveil.app, api.infinitumveilvpn.app | Live and public. Browser APK and extension downloads. | Free by design. Referral funnel (mechanism 13). | no, add before checkout |
| Cumulus://Cloud™ | cumuluscloud.app | **Landing page only.** "In development", early access by email. | No. Mail (mail.cumuluscloud.app) stays behind sign-in. | yes |
| FABRIC™ | infinitumfabric.app | **Landing page only.** "In development". Apps are mobile-only and not released. | No | no, add before checkout |
| METATRON¹⁰⁰ | mttrn.app | Not deployed. The zone is active and it's in the Cumulus://ID CORS allowlist | No | yes |
| Everything else | — | Scaffolded or partial (see `infinitum-nexus/PRODUCT-DELIVERY.md`) | No | — |

`RETURN_HOSTS` (in `wrangler.jsonc`) lists the domains allowed to pass a Stripe `returnUrl` and call the customer API from the browser. Add a product's domain before it starts checkout.

The products with users today (SCALVS.ai, :\ARCHITECT\, VEIL) are free. **No live product collects revenue yet.** The first job is to put Checkout in front of the products that are already live (section 4).

## 1. The 13 revenue mechanisms

| # | Mechanism | Products | Backend |
|---|-----------|----------|---------|
| 1 | **Subscriptions (flat or per seat)** with free tiers and trials | SCALVS.ai, :\ARCHITECT\, +SPCTR, METATRON¹⁰⁰, FABRIC Business, Cumulus Drive, [HRVST] Pro, E(O)N Plus, ORBIS Fleet, Academy, $FIRM Suite | `subscriptions/checkout`, Stripe Billing, `entitlements`, plan `bundle`s |
| 2 | **Usage-based billing** (allowance included, then overage) | SCALVS tasks, :\ARCHITECT\ execution credits and verified outcomes, Spectre agent credits and Deal Rehearsals, CONJURE™ prediction runs (METATRON¹⁰⁰ and `@conjure` in each product), Cumulus storage/egress/vCPU/GPU/inference, ei·det·ic searches, Firmament attestations | `usage` → Stripe Billing Meters |
| 3 | **Prepaid credits** (with volume bonus) for people who don't want a plan | All metered products | `credits/checkout`, credit ledger |
| 4 | **Hardware sales**: pay in full, or reserve with a deposit and get invoiced the balance at ship | #THRONE, E(O)N, ORBIS, POLLEN + Flower, SLUG | `orders`, balance invoice |
| 5 | **Leasing / Robotics-as-a-Service** | #THRONE Book lease, SLUG RaaS, POLLEN Swarm Ops | Lease plans in catalog |
| 6 | **Device software and care plans** (recurring revenue on top of hardware margin) | THRONE Care, Rack on-prem suite, E(O)N Plus, ORBIS/POLLEN fleet software | Subscriptions + signed licences |
| 7 | **On-prem / offline licences** (Ed25519-signed, verifiable without network) | #THRONE Rack, private SCALVS/Spectre, Firmament validators, air-gapped deployments | `admin/licences`, `licences/verify` |
| 8 | **Consented data marketplace**: **85% of net licence revenue to the contributor, 15% to the platform**, on every marketplace, as published on every product page and in `infinitum-nexus` `packages/data-exchange` | [HRVST] packets, FABRIC aggregates, Cumulus Data Exchange, SCALVS agent templates | `consents`, `marketplace/assets`, `licence` (`MARKETPLACES` in `src/catalog.ts`) |
| 9 | **Buyer-funded Missions** (bounty + 15–20% fee) | [HRVST], Cumulus | `missions`, submissions, review |
| 10 | **Enterprise / sales-assisted tiers** (KYB / purpose review before purchase) | ei·det·ic, SCALVS Private Tenant, Spectre Enterprise, Firmament Permissioned, [HRVST] Buyer, Rack | `admin/orgs/:id/approve` |
| 11 | **Contracted services billed by milestone** | IT, security, engineering, PM consulting; narrative intelligence; Cognitive / Reflexive Capital / Irregular Warfare advisory; managed services; implementation | `admin/engagements`, milestone invoices |
| 12 | **Defense contracts** (never self-serve, outside the token economy, defense-verified orgs only) | ODIN° and the 18 MIL editions, DAEMON WARDEN/ZENITH, Cumulus Sovereign | Contract channel + `verify-defense` gate |
| 13 | **Partner referrals** (20% of referred invoices for 12 months) | VEIL as the free funnel into every paid product; resellers; integrators | `referral-codes`, auto commission |

## 2. Product by product

| Product | How it earns |
|---|---|
| **SCALVS.ai** | Free (100 tasks) → Pro $29 → Team $49/seat → Private Tenant $2,500/mo; $0.02/task overage; 15% of the agent-template marketplace |
| **:\ARCHITECT\** | Free → Pro $39 → Business $99/seat; $0.10/execution credit, $0.50 per verified outcome (charge for results, not clicks) |
| **+SPCTR** | $49 / $99 / $159 per seat; Deal Rehearsal $25/run as a premium add-on; agent credits |
| **METATRON¹⁰⁰** | Consumer bundle of the whole suite: Personal $19, Family $39, Business $149, annual discount; CONJURE™ prediction runs metered ($0.25 over the allowance). Your best upsell, since it pulls in every other product. Live **@conjure** forecasts are wired in behind each product's sign-in (needs `CONJURE_URL` and `CONJURE_PRODUCT_KEY` on the server), so prediction runs have a real backend to meter |
| **FABRIC** | Free personal (growth); Business $8/seat with compliance features; opt-in data revenue share (85% to users in $FAB once token payouts are approved, USD until then; 15% platform) |
| **Cumulus://Cloud** | Drive tiers ($0 / $2.99 / $9.99); pay-as-you-go cloud (storage, egress, vCPU, GPU $2.20/hr, inference); Sovereign regions by contract; 15% of the Data Exchange |
| **ei·det·ic** | Approved organisations only: $2,000/mo platform fee + $0.50 per authorised search. Highest-risk product, so it stays sales-assisted |
| **[HRVST]** | Free to contribute; Pro Contributor $9.99; Buyer Access $499/mo; 15% of licences; 20% on Missions |
| **VEIL</>** | Stays free (as promised). It earns through referral attribution into paid plans, search-partner revenue share and opt-in sponsored placements. Never by selling browsing history, as the site promises |
| **#THRONE** | **Dropped.** `infinitum-nexus/PRODUCT-DELIVERY.md` says it will not be built. The catalog still has its plans (hardware $1,999–$8,999, lease, THRONE Care, Rack suite). Remove them, or don't run `stripe:sync` for them, so nothing sells a product that won't ship |
| **E(O)N / ORBIS / POLLEN / Flower / SLUG** | Hardware with deposits for pre-orders, then recurring fleet software, RaaS ($1,800/unit/mo for SLUG) and maintenance |
| **DAEMON** | Civil WARDEN site packages and ZENITH coverage-as-a-service, quoted by contract |
| **Firmament Network** | Attestation API ($0.001 each, 10k free), permissioned instances $5,000/mo, validator licences |
| **$FIRM Suite™** | **Free** (`firm_suite`, $0). Includes $FIRM Wallet™, any-token swap and $Firmament Network™, and comes with Cumulus://ID (Mail, Drive, sign-in/SSO), Cumulus Drive Free and Firmament Developer. It is a free entry point like VEIL: the money comes from upgrades to paid Cumulus storage and compute, METATRON¹⁰⁰ and Firmament attestations |
| **$FIRM Wallet™** | Built (infinitum-nexus #67, merged). Self-custody on ETH/Base/Arbitrum/Solana, Uniswap and Jupiter swaps, tokenized stocks, Hyperliquid perps and spot, Derive options data, read-only Polymarket and Kalshi, Conjure signals, and $FIRM Mini for Telegram. It defaults to testnet and is not deployed. **Infinitum takes no fee in it today**: it earns indirectly through the Suite and METATRON. Possible later revenue: Hyperliquid builder fees, a swap fee and venue referral programmes. All of these sit behind the counsel review in §3 |
| **$FIRM / product tokens** | See section 3. Not switched on in the backend. The Genesis Drop (1 Jan 2027, snapshot 31 Dec 23:00 UTC) collects Solana wallet registrations only (10 tokens, three early-viewer tiers: Genesis #1–1,000 ×3, Vanguard #1,001–10,000 ×2, Wave ×1; 5 new wallets per IP per day). API merged in [InfinitumCorp/infinitum-revenue#6](https://github.com/InfinitumCorp/infinitum-revenue/pull/6) and its Supabase table is applied, but the Worker is not deployed. The /drop page on infinitumcorporation.ai is built but **not deployed**, and pool sizes are still "announced with final terms" |
| **ODIN°** | Multi-year defense contracts, milestone-invoiced, confidential descriptions on invoices |
| **Academy** | $29/mo membership; certifications for partners and integrators (feeds the referral channel) |

## 3. What I deliberately did not switch on

- **Token rewards, DEX swap fees and token payouts.** On the site, users "share in the revenue" paid in product tokens that trade on a DEX. In the US that pattern is likely to be treated as a securities offering and money transmission. The backend keeps every earning in a **USD ledger** and pays out by bank (Stripe Connect). Token payouts are behind `TOKEN_PAYOUTS_ENABLED=false`. Turn it on only after counsel signs off on the token programme. Swap fees are not built, and the wallet charges none. The Genesis Drop backend only records registrations (wallet, early-viewer number). It sends no tokens, so the drop can't run until counsel has signed off.
- **Selling personal data without consent.** Every listing needs an active, purpose-specific consent. Withdrawing consent delists the asset immediately. Buyers can only license an asset for uses the contributor permitted. Individual accounts cannot buy data.
- **ei·det·ic self-serve.** Facial and identity matching falls under biometric laws (BIPA, GDPR Art. 9, the EU AI Act). It is sold only to approved organisations.
- **Military self-serve.** ODIN° and the MIL editions can only be contracted with organisations flagged `verified_defense` by an operator. Export-control (ITAR/EAR) screening still has to happen outside this system.

## 4. Decided: the data-sharing split is 85%

Settled in #11: every marketplace pays **85% of net licence revenue to the contributor and keeps 15%** (`contributorShareBps: 8500` in `MARKETPLACE_RULES`), matching the site and the wallet. Missions keep their separate 15–20% fee, which is now the main margin on data. Still open: the site promises the 85% in tokens ($FAB etc.), while the backend pays USD. Tokens fall under §3, so it stays USD until counsel signs off.

## 5. Launch: the CryptoPublicOffering™ (CPO)

The $FIRM™ raise is framed as a **CryptoPublicOffering™**: IPO-style structure (a public book, a fixed allocation window, a published roadmap), with the energy of a token drop ("get in early").

- **Where:** firmnetwork.app ($Firmament Network™) takes the waitlist: email plus an optional Solana wallet. firmwallet.app ($FIRM Wallet™) takes early access for the wallet and the $FIRM Suite. They are two separate sites, each about its own product, on one Cloudflare Worker (`InfinitumCorp/firmament-launch`). They share one KV namespace (`WAITLIST`), with every key prefixed by site (`<site>:email:<address>`, `<site>:meta:count`), so the two lists and their counts stay separate.
- **Chain plan: Solana only (decided 9 Nov 2026).** $FIRM™ and every product token are issued, raised and traded on **Solana**. There is no Ethereum home, no migration and no Base bridge, so CPO holders keep the same token. **The live site still says the old plan**: firmnetwork.app/network says "Raised on Solana. Home on Ethereum.", shows a Solana → Ethereum migration path and an Ethereum row in the token table, and firmwallet.app describes a wallet "for Solana, Ethereum and Base". The corporate /tokens page also lists Ethereum and Base. All of these must be rewritten before launch (next move #18).
- **Any-token swap:** $FIRM™ and every product token trade on Solana, through the wallet's Jupiter aggregation. The wallet's built EVM support (Uniswap on Ethereum/Base/Arbitrum) can stay for holding other assets, but no Infinitum token lives there. Infinitum charges no swap fee today (§3).
- **Countdown:** `LAUNCH_AT = null` in the network page keeps it "coming soon". Set it only once the raise date is final.
- **Revenue role:** the CPO is a **capital** event, not a revenue line. It funds the build-out. Recurring revenue still comes from §1–§2.
- **Gate (same as §3, but sharper):** a public token sale marketed "like an IPO" is about as close to a securities offering as wording can get. Before money is taken, counsel must clear the sale structure (Reg D/Reg S/Reg CF or a non-US-only offering), KYC/AML, geo-blocking of restricted jurisdictions, and the site copy itself. The sites collect interest only. They take no funds and promise no returns, and each carries a risk disclaimer. Keep it that way until sign-off. This shares a deadline with next move #10.

## 6. REVshare: how users and partners earn

One table, matching firmnetwork.app's REVshare section and `MARKETPLACE_RULES` / referral settings in `src/catalog.ts`:

| Program | Contributor / partner share | Platform | Paid as |
|---|---|---|---|
| [HRVST] data packets | 85% | 15% | USD by bank (Stripe Connect) |
| FABRIC aggregates | 85% | 15% | USD |
| Cumulus Data Exchange | 85% | 15% | USD |
| SCALVS agent templates | 85% | 15% | USD |
| [HRVST] / Cumulus Missions | bounty to contributor | 15–20% fee | USD |
| Partner referrals | 20% of referred invoices for 12 months | 80% | USD |

Earnings land in a USD ledger. They are held to cover refunds and disputes: 14 days for FABRIC, [HRVST] and Cumulus, 7 days for SCALVS templates and 30 days for referral commissions (`holdDays` in `MARKETPLACE_RULES`; the 30 is hard-coded in `applyReferralCommission`) (refunds and lost disputes claw back automatically), then paid by bank. **Token payouts come later, if at all.** That needs `TOKEN_PAYOUTS_ENABLED=true` and counsel's sign-off (§3). Until then the site says payouts are USD now, tokens later.

## 7. Distribution, hosting and infrastructure cost

- **Direct downloads.** Every product is listed at infinitumcorporation.ai/downloads with its website. `downloads/products.json` is the single source of truth. The page and the permanent `/download/<product>/<platform>` links are meant to be regenerated by `node _tools/build-downloads.mjs`, **but that script is not in the `infinitum-corporation-site` repo** (checked 9 Nov 2026): commit it or the launch steps below can't run (next move #22). Until a product is live, its link goes to the product's website. To launch one, set `status: "live"`, add the signed installer URLs, rebuild and deploy. Then the same links serve the installer. Today all 11 products are "coming soon".
- **Bundles.** A plan's `bundle` field grants plans in other products (for example `firm_suite` → `cumulus_drive_free` + `firmament_dev`). The org's own paid subscription always wins over a bundled grant, so upgrades replace the bundled plan cleanly. Entitlements report `grantedBy` so the apps can show where a plan came from.
- **Hosting.** The revenue API runs on Cloudflare Workers with Supabase Postgres and Stripe. The Firmament sites run on Cloudflare Workers + KV. The corporate site (infinitumcorporation.ai) and the defense site (infinitumdefense.com) are still served by Vercel. All of these sit on free or low tiers while we're pre-revenue. The one paid line: the Cloudflare account was upgraded to **Workers Paid ($5/mo)** on 9 Nov 2026 for Email Sending, which isn't set up yet. Use it (§7, email) or cancel it. **As of 9 Nov 2026 the revenue Worker answers every request with error 1101**: `STRIPE_WEBHOOK_SECRET` is not set, so startup throws. Only `DATABASE_URL` and `STRIPE_SECRET_KEY` are set. Two attempts to deploy the drop API also failed on network resets during upload.
- **DNS and domains.** On 8 Nov 2026, DNS for infinitumcorporation.ai and infinitumdefense.com moved from Vercel DNS to Cloudflare (free plan, nameservers `cecelia` / `ishaan.ns.cloudflare.com`). The records are DNS-only A records for the apex and `*` plus a `www` CNAME to `cname.vercel-dns.com`, all pointing at Vercel, so Vercel keeps serving both sites and issuing their certificates. Manage DNS for these two domains in Cloudflare from now on: edits in Vercel's DNS page no longer take effect. Both domains are still *registered* through Vercel (Name.com). The registrar transfer to Cloudflare Registrar (at-cost renewals) is blocked by the 60-day new-registration lock until about **7 Jan 2027**. Check that Cloudflare Registrar accepts `.ai` before then. If it doesn't, keep infinitumcorporation.ai where it is and transfer only infinitumdefense.com.
- **Corporate email.** Both domains receive mail through Cloudflare Email Routing (free). `info@`, `contact@`, `support@`, `admin@`, `billing@`, `security@`, `careers@`, `press@` and `sales@`, plus a catch-all on each domain, all forward to theinfinitumcorp@gmail.com. Delivery to both domains was tested on 8 Nov 2026. SPF allows Cloudflare and Google. DMARC is report-only (`p=none`) until sending works, then it moves to `p=quarantine`. **Sending as these addresses isn't set up**: Gmail's "Send mail as" through Gmail SMTP failed on the App Password, and Cloudflare SMTP (`smtp.mx.cloudflare.net:465`, an Email Sending API token as the password) was stopped before the domains were onboarded for sending.
- **Google Cloud: free tiers only.** Use GCP wherever a free tier fits, and spend nothing there yet. Good fits:
  - BigQuery sandbox for revenue analytics (export the ledger, not raw PII)
  - Cloud Run and Pub/Sub for background jobs and event fan-out
  - The e2-micro VM
  - Small Cloud Storage
  - The Gemini API free tier, for prototypes only

  Keep customer data off the free Gemini tier, because Google may use free-tier inputs. Don't move the billing API off Workers/Supabase for the sake of GCP. GPU, Vertex AI after the trial and Marketplace billing are paid, so the Cumulus GPU and inference meters have no GCP capacity behind them. Back them with another provider or keep them off.
- **Russia: the apps must work there without a VPN, but most of the stack is blocked.** These results were measured on 9 Nov 2026 on a phone on Russian home Wi-Fi with no VPN.
  - **Blocked or throttled:**
    - Cloudflare-proxied sites: most time out. scalvs.app's HTML loads, but its JavaScript stalls for about 30 s, so the page never leaves "Loading…".
    - Vercel: times out.
    - Supabase: times out. So any sign-in or data that goes through Supabase fails in Russia.
    - `*.workers.dev`: SCALVS billing calls `infinitum-revenue.infinitumcorp.workers.dev`, so checkout fails there too.
  - **Reachable in about 1 s:** GitHub Pages and Gcore CDN.
  - **Live fix:** https://ru.scalvs.app is a free static mirror of scalvs.app on GitHub Pages (repo `InfinitumCorp/scalvs-ru`, a DNS-only CNAME, HTTPS enforced). It runs in its labelled demo mode, because the API isn't mirrored. Live AI there needs CORS for `ru.scalvs.app` on the `scalvs-ai` Worker.
  - **Pattern for the other products:**
    - Static front ends go on GitHub Pages at `ru.<domain>`, at $0.
    - Dynamic apps need a Russia-reachable origin or CDN, for example Gcore's free CDN tier.
    - Supabase sign-in needs a reachable proxy.
  - **Revenue gate:** Stripe doesn't serve Russia, and marketing or selling there needs a sanctions review first. Plan for **zero Russian revenue** until counsel advises. Keep military editions out of every Russia-facing page.
- **AWS: Free plan, Always Free services only.** Infinitum has an AWS project (new AWS experience) with the selected Region **us-east-2**. On 9 Nov 2026 it was on the **Free plan** with **$99.84** of credit left, and the plan ends on **7 May 2027**. When the plan ends or the credit runs out, the project has to upgrade to paid or it closes. Local agent access uses the CLI profile `infinitum` through the AWS Agent Toolkit. Draft PR #7 is the first use: SNS notices after Stripe webhooks, and three CloudWatch custom metrics (`WebhookProcessed`, `WebhookFailed`, `CheckoutCreated`). Both stay inside Always Free (1M SNS publishes, 10 custom metrics). Rules before merging it:
  - Create the SNS topic in us-east-2. The project can't create Regional resources in other Regions. Give the topic no SMS or email subscriptions, because those are billed.
  - The Worker can't use `aws login`, so it needs a dedicated access key. Scope it to `sns:Publish` on that one topic and `cloudwatch:PutMetricData` limited to the namespace, store it only as a Worker secret, and rotate it.
  - Don't add metric dimensions, because each dimension combination counts as another custom metric.
  - Put the drop registration route (`POST /v1/drop/registrations`, PR #6) behind the same free reCAPTCHA check before the drop is announced. A token drop without bot checks invites one person registering many wallets.
  - Whether AWS is reachable from Russia hasn't been tested. Keep AWS out of every user-facing path for the Russia mirrors until it is measured.

## 8. Highest-leverage next moves

1. **Charge for what is already live.** SCALVS.ai is public and its compute is paid by Infinitum (Workers AI). Add the free 100-task allowance and Pro Checkout before usage grows, using `subscriptions/checkout` with `scalvs.app` as an allowed return host. Do the same for :\ARCHITECT\ on rchtch.app.
2. **Watch the cost of free.** Workers AI usage and the per-IP rate limit on scalvs.app are the only spend controls today. Set a Cloudflare usage alert, and meter tasks through `usage` from day one so the free tier is enforceable.
3. **Turn landing pages into a waitlist.** cumuluscloud.app and infinitumfabric.app collect early access by email link only. A real sign-up form (Cumulus://ID or a Supabase table) gives a measurable launch list.
4. Ship METATRON¹⁰⁰ on mttrn.app as the hero bundle. It sells the whole ecosystem in one subscription, and CONJURE™ prediction runs are already metered. Done so far: the domain is an active Cloudflare zone, it's in `RETURN_HOSTS`, and it's in the Cumulus://ID CORS allowlist, with sign-in callbacks at `https://mttrn.app/api/auth/oauth/{provider}/callback`. Still to do: put a site or Worker on the domain that shows the `metatron_personal`, `metatron_family` and `metatron_business` checkout. The Personal plan has a 30-day trial.
5. Use VEIL (free, live) + referral codes as the top of the funnel; measure conversion to SCALVS and :\ARCHITECT\ Pro.
6. Open hardware **reservations with deposits**. They fund production and prove demand before manufacturing.
7. Turn services engagements into managed-services retainers (recurring) at handover.
8. Launch [HRVST] Missions with the prospective buyers in `ANCHOR_BUYERS`: **Premise** ([premise.com](https://premise.com)), **Field Agent** ([fieldagent.net](https://www.fieldagent.net)), then **Roamler** ([roamler.com](https://www.roamler.com)). They already pay for retail-shelf and public-place observations. For opted-in web traffic, approach `TRAFFIC_BUYERS` in order: **Similarweb**, **Comscore**, then **Nielsen**. Status is prospective: no contract, no org, and no licence until a person signs one. A restricted place or a traffic packet can be licensed only when the owner opts in. Traffic packets are aggregated (site host and referral, no query string). `VEIL</>` VPN is not a source. Buyers fund supply, so you don't pay for it. At 85% to contributors, the 20% mission fee is the main margin on data, so keep it.
9. Make the free **$FIRM Suite** the default sign-up. It hands every new person a Cumulus://ID, Drive and Mail account, and Cumulus and METATRON can upsell from there. Track how many Suite users upgrade.
10. **Settle the token programme with counsel before 31 Dec 2026.** The Genesis Drop snapshot is that night, and the site's token rewards, the wallet's product-token rewards and the 85% data share all depend on the answer.
11. Commission an external security audit of the $FIRM Wallet before switching its default to mainnet. Until then it stays a testnet showcase.
12. ~~Resolve the data-sharing split~~ Done in #11: 85% everywhere (§4).
13. Get counsel's sign-off on the CPO structure and site copy (§5) before taking any funds, then set the launch date (`LAUNCH_AT`).
14. Grow the CPO waitlist by tying REVshare referral codes (§6) to waitlist sign-ups, so early supporters become referral partners.
15. Ship the first live installer through `products.json` (likely the $FIRM Suite or VEIL) to prove the direct-download pipeline end to end.
16. On or after 7 Jan 2027, transfer infinitumcorporation.ai and infinitumdefense.com to Cloudflare Registrar (unlock and get auth codes in Vercel, then pay in Cloudflare). That puts DNS, registration and the Workers stack in one place, and renewals at cost.
17. Set `STRIPE_WEBHOOK_SECRET` (and `ADMIN_API_KEY`) on the revenue Worker with `npx wrangler secret put`, which brings the API back. Then (#6 is merged) deploy the Worker, check `GET /v1/drop`, and only then deploy /drop on infinitumcorporation.ai, so the page never goes live against a broken API.
18. Rewrite every public chain claim to Solana-only before the drop: firmnetwork.app (/network hero, migration path, token table, roadmap phase 02), firmwallet.app copy and the corporate /tokens page. Set pool sizes for the drop's final terms at the same time.
19. Make every product reachable in Russia without a VPN (§7). Mirror the static sites (FABRIC, VEIL, Cumulus) to GitHub Pages as `ru.<domain>`, as already done for SCALVS. Add CORS for the mirrors on the product Workers. Choose a Russia-reachable path for Supabase sign-in. Get the sanctions review before any Russian marketing or payments.
20. Finish PR #7 (reCAPTCHA, SNS and CloudWatch) under the AWS rules in §7, then add `verifyRecaptcha` to the drop registration route now that #6 has landed. Both should ship before the drop is announced.
21. Before **7 May 2027**, decide whether AWS stays: upgrade the project to the paid plan with a budget alert, or move the SNS and CloudWatch work to Cloudflare Queues and Workers Analytics Engine, which are already in the stack.
22. Clean up the loose ends in §7. Commit `_tools/build-downloads.mjs` to `infinitum-corporation-site`, or rewrite the downloads steps. Then either finish sending email from the corporate addresses (onboard both domains for Cloudflare Email Sending and add the Gmail Send-as identities) or cancel Workers Paid.
