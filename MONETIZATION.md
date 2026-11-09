# Infinitum monetization map

Every revenue stream across the portfolio on [infinitumcorporation.ai](https://infinitumcorporation.ai), and where the backend implements it.
Prices are **proposed launch prices** (in `src/catalog.ts`), set to be reviewed, not final decisions.

**Last reviewed:** 2026-11-09. The revenue API runs on Cloudflare Workers with the shared Supabase Postgres.

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
| **METATRON¹⁰⁰** | Consumer bundle of the whole suite: Personal $19, Family $39, Business $149, annual discount; CONJURE™ prediction runs metered ($0.25 over the allowance). Your best upsell, since it pulls in every other product |
| **FABRIC** | Free personal (growth); Business $8/seat with compliance features; opt-in data revenue share (85% to users in $FAB once token payouts are approved, USD until then; 15% platform) |
| **Cumulus://Cloud** | Drive tiers ($0 / $2.99 / $9.99); pay-as-you-go cloud (storage, egress, vCPU, GPU $2.20/hr, inference); Sovereign regions by contract; 15% of the Data Exchange |
| **$FIRM Suite** | Free plan that bundles $FIRM Wallet, any-token swap and Firmament Network with a Cumulus://ID (Mail, Drive, single sign-on). It's an acquisition product: it earns through the Drive and Firmament plans it leads into |
| **ei·det·ic** | Approved organisations only: $2,000/mo platform fee + $0.50 per authorised search. Highest-risk product, so it stays sales-assisted |
| **[HRVST]** | Free to contribute; Pro Contributor $9.99; Buyer Access $499/mo; 15% of licences; 20% on Missions |
| **VEIL</>** | Stays free (as promised). It earns through referral attribution into paid plans, search-partner revenue share and opt-in sponsored placements. Never by selling browsing history, as the site promises |
| **#THRONE** | **Dropped.** `infinitum-nexus/PRODUCT-DELIVERY.md` says it will not be built. The catalog still has its plans (hardware $1,999–$8,999, lease, THRONE Care, Rack suite). Remove them, or don't run `stripe:sync` for them, so nothing sells a product that won't ship |
| **E(O)N / ORBIS / POLLEN / Flower / SLUG** | Hardware with deposits for pre-orders, then recurring fleet software, RaaS ($1,800/unit/mo for SLUG) and maintenance |
| **DAEMON** | Civil WARDEN site packages and ZENITH coverage-as-a-service, quoted by contract |
| **Firmament Network** | Attestation API ($0.001 each, 10k free), permissioned instances $5,000/mo, validator licences |
| **FIRM / product tokens** | See section 3. Not switched on in the backend |
| **ODIN°** | Multi-year defense contracts, milestone-invoiced, confidential descriptions on invoices |
| **Academy** | $29/mo membership; certifications for partners and integrators (feeds the referral channel) |

## 3. What I deliberately did not switch on

- **Token rewards, DEX swap fees and token payouts.** On the site, users "share in the revenue" paid in product tokens that trade on a DEX. In the US that pattern is likely to be treated as a securities offering and money transmission. The backend keeps every earning in a **USD ledger** and pays out by bank (Stripe Connect). Token payouts are behind `TOKEN_PAYOUTS_ENABLED=false`. Turn it on only after counsel signs off on the token programme. Swap fees are not built.
- **Selling personal data without consent.** Every listing needs an active, purpose-specific consent. Withdrawing consent delists the asset immediately. Buyers can only license an asset for uses the contributor permitted. Individual accounts cannot buy data.
- **ei·det·ic self-serve.** Facial and identity matching falls under biometric laws (BIPA, GDPR Art. 9, the EU AI Act). It is sold only to approved organisations.
- **Military self-serve.** ODIN° and the MIL editions can only be contracted with organisations flagged `verified_defense` by an operator. Export-control (ITAR/EAR) screening still has to happen outside this system.

## 4. Highest-leverage next moves

1. **Charge for what is already live.** SCALVS.ai is public and its compute is paid by Infinitum (Workers AI). Add the free 100-task allowance and Pro Checkout before usage grows, using `subscriptions/checkout` with `scalvs.app` as an allowed return host. Do the same for :\ARCHITECT\ on rchtch.app.
2. **Watch the cost of free.** Workers AI usage and the per-IP rate limit on scalvs.app are the only spend controls today. Set a Cloudflare usage alert, and meter tasks through `usage` from day one so the free tier is enforceable.
3. **Turn landing pages into a waitlist.** cumuluscloud.app and infinitumfabric.app collect early access by email link only. A real sign-up form (Cumulus://ID or a Supabase table) gives a measurable launch list.
4. Ship METATRON¹⁰⁰ on mttrn.app as the hero bundle. It sells the whole ecosystem in one subscription, and CONJURE™ prediction runs are already metered. Done so far: the domain is an active Cloudflare zone, it's in `RETURN_HOSTS`, and it's in the Cumulus://ID CORS allowlist, with sign-in callbacks at `https://mttrn.app/api/auth/oauth/{provider}/callback`. Still to do: put a site or Worker on the domain that shows the `metatron_personal`, `metatron_family` and `metatron_business` checkout. The Personal plan has a 30-day trial.
5. Use VEIL (free, live) + referral codes as the top of the funnel; measure conversion to SCALVS and :\ARCHITECT\ Pro.
6. Open hardware **reservations with deposits**. They fund production and prove demand before manufacturing.
7. Turn services engagements into managed-services retainers (recurring) at handover.
8. Launch [HRVST] Missions with one or two anchor buyers (municipal accessibility mapping, retail shelf audits). Buyers fund supply, so you don't pay for it. At 85% to contributors, the 20% mission fee is the main margin on data, so keep it.
