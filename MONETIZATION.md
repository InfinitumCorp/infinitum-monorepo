# Infinitum monetization map

Every revenue stream across the portfolio on infinitumcorporation.ai (infinitum-corporation.vercel.app now redirects there), and where the backend implements it.
Prices are **proposed launch prices** (in `src/catalog.ts`), set to be reviewed, not final decisions.

*Last updated 9 Nov 2026: added the $FIRM Suite and $FIRM Wallet, @conjure live predictions and the Genesis Drop. Also flagged the conflict between the site's data-sharing split and the backend's (§4). Later the same day: added the CryptoPublicOffering™ launch (§5), the REVshare terms (§6) and distribution, hosting and the GCP free-tier policy (§7). Then: DNS for both corporate domains moved to Cloudflare, with the registrar transfer due from 7 Jan 2027 (§7, next move #13). Later still: **Infinitum tokens are Solana-only** (§5 rewritten), and the Genesis Drop status and deploy blockers are updated (§2, §7, next moves #14–#15).*

## 1. The 13 revenue mechanisms

| # | Mechanism | Products | Backend |
|---|-----------|----------|---------|
| 1 | **Subscriptions (flat or per seat)** with free tiers and trials | SCALVS.ai, /CNDCTR/, +SPCTR, METATRON¹⁰⁰, FABRIC Business, Cumulus Drive, [HRVST] Pro, E(O)N Plus, ORBIS Fleet, Academy | `subscriptions/checkout`, Stripe Billing, `entitlements` |
| 2 | **Usage-based billing** (allowance included, then overage) | SCALVS tasks, CNDCTR execution credits and verified outcomes, Spectre agent credits and Deal Rehearsals, METATRON prediction runs, Cumulus storage/egress/vCPU/GPU/inference, ei·det·ic searches, Firmament attestations | `usage` → Stripe Billing Meters |
| 3 | **Prepaid credits** (with volume bonus) for people who don't want a plan | All metered products | `credits/checkout`, credit ledger |
| 4 | **Hardware sales**: pay in full, or reserve with a deposit and get invoiced the balance at ship | #THRONE, E(O)N, ORBIS, POLLEN + Flower, SLUG | `orders`, balance invoice |
| 5 | **Leasing / Robotics-as-a-Service** | #THRONE Book lease, SLUG RaaS, POLLEN Swarm Ops | Lease plans in catalog |
| 6 | **Device software and care plans** (recurring revenue on top of hardware margin) | THRONE Care, Rack on-prem suite, E(O)N Plus, ORBIS/POLLEN fleet software | Subscriptions + signed licences |
| 7 | **On-prem / offline licences** (Ed25519-signed, verifiable without network) | #THRONE Rack, private SCALVS/Spectre, Firmament validators, air-gapped deployments | `admin/licences`, `licences/verify` |
| 8 | **Consented data marketplace** (platform keeps 20–30% of each licence) | [HRVST] packets (70% to contributor), FABRIC aggregates (70%, as published), Cumulus Data Exchange (80%), SCALVS agent templates (80%) | `consents`, `marketplace/assets`, `licence` |
| 9 | **Buyer-funded Missions** (bounty + 15–20% fee) | [HRVST], Cumulus | `missions`, submissions, review |
| 10 | **Enterprise / sales-assisted tiers** (KYB / purpose review before purchase) | ei·det·ic, SCALVS Private Tenant, Spectre Enterprise, Firmament Permissioned, [HRVST] Buyer, Rack | `admin/orgs/:id/approve` |
| 11 | **Contracted services billed by milestone** | IT, security, engineering, PM consulting; narrative intelligence; Cognitive / Reflexive Capital / Irregular Warfare advisory; managed services; implementation | `admin/engagements`, milestone invoices |
| 12 | **Defense contracts** (never self-serve, outside the token economy, defense-verified orgs only) | ODIN° and the 18 MIL editions, DAEMON WARDEN/ZENITH, Cumulus Sovereign | Contract channel + `verify-defense` gate |
| 13 | **Partner referrals** (20% of referred invoices for 12 months) | VEIL as the free funnel into every paid product; resellers; integrators | `referral-codes`, auto commission |

## 2. Product by product

| Product | How it earns |
|---|---|
| **SCALVS.ai** | Free (100 tasks) → Pro $29 → Team $49/seat → Private Tenant $2,500/mo; $0.02/task overage; 20% cut of the agent-template marketplace |
| **/CNDCTR/** | Free → Pro $39 → Business $99/seat; $0.10/execution credit, $0.50 per verified outcome (charge for results, not clicks) |
| **+SPCTR** | $49 / $99 / $159 per seat; Deal Rehearsal $25/run as a premium add-on; agent credits |
| **METATRON¹⁰⁰** | Consumer bundle of the whole suite: Personal $19, Family $39, Business $149, annual discount; prediction runs metered. Your best upsell, since it pulls in every other product. Live **@conjure** forecasts are now wired in behind each product's sign-in (needs `CONJURE_URL` and `CONJURE_PRODUCT_KEY` on the server), so prediction runs have a real backend to meter |
| **FABRIC** | Free personal (growth); Business $8/seat with compliance features; opt-in data revenue share (30% platform) |
| **Cumulus://Cloud** | Drive tiers ($0 / $2.99 / $9.99); pay-as-you-go cloud (storage, egress, vCPU, GPU $2.20/hr, inference); Sovereign regions by contract; 20% of the Data Exchange |
| **ei·det·ic** | Approved organisations only: $2,000/mo platform fee + $0.50 per authorised search. Highest-risk product, so it stays sales-assisted |
| **[HRVST]** | Free to contribute; Pro Contributor $9.99; Buyer Access $499/mo; 30% of licences; 20% on Missions |
| **VEIL</>** | Stays free (as promised). It earns through referral attribution into paid plans, search-partner revenue share and opt-in sponsored placements. Never by selling browsing history, as the site promises |
| **#THRONE** | Hardware margin ($1,999–$8,999), 24-month lease, THRONE Care $199/yr, Rack on-prem suite $1,200/mo |
| **E(O)N / ORBIS / POLLEN / Flower / SLUG** | Hardware with deposits for pre-orders, then recurring fleet software, RaaS ($1,800/unit/mo for SLUG) and maintenance |
| **DAEMON** | Civil WARDEN site packages and ZENITH coverage-as-a-service, quoted by contract |
| **Firmament Network** | Attestation API ($0.001 each, 10k free), permissioned instances $5,000/mo, validator licences |
| **$FIRM Suite™** | **Free** (`firm_suite`, $0). Includes $FIRM Wallet™, any-token swap and $Firmament Network™, and comes with Cumulus://ID (Mail, Drive, sign-in/SSO), Cumulus Drive Free and Firmament Developer. It is a free entry point like VEIL: the money comes from upgrades to paid Cumulus storage and compute, METATRON¹⁰⁰ and Firmament attestations |
| **$FIRM Wallet™** | Built (infinitum-nexus #67, merged). Self-custody on ETH/Base/Arbitrum/Solana, Uniswap and Jupiter swaps, tokenized stocks, Hyperliquid perps and spot, Derive options data, read-only Polymarket and Kalshi, Conjure signals, and $FIRM Mini for Telegram. It defaults to testnet and is not deployed. **Infinitum takes no fee in it today**: it earns indirectly through the Suite and METATRON. Possible later revenue: Hyperliquid builder fees, a swap fee and venue referral programmes. All of these sit behind the counsel review in §3 |
| **$FIRM / product tokens** | See section 3. Not switched on in the backend. The Genesis Drop (1 Jan 2027, snapshot 31 Dec 23:00 UTC) collects Solana wallet registrations only (10 tokens, three early-viewer tiers: Genesis #1–1,000 ×3, Vanguard #1,001–10,000 ×2, Wave ×1; 5 new wallets per IP per day). API in [InfinitumCorp/infinitum-revenue#6](https://github.com/InfinitumCorp/infinitum-revenue/pull/6), open, not merged; its Supabase table is already applied. The /drop page on infinitumcorporation.ai is built but **not deployed**, and pool sizes are still "announced with final terms" |
| **ODIN°** | Multi-year defense contracts, milestone-invoiced, confidential descriptions on invoices |
| **Academy** | $29/mo membership; certifications for partners and integrators (feeds the referral channel) |

## 3. What I deliberately did not switch on

- **Token rewards, DEX swap fees and token payouts.** On the site, users "share in the revenue" paid in product tokens that trade on a DEX. In the US that pattern is likely to be treated as a securities offering and money transmission. The backend keeps every earning in a **USD ledger** and pays out by bank (Stripe Connect). Token payouts are behind `TOKEN_PAYOUTS_ENABLED=false`. Turn it on only after counsel signs off on the token programme. Swap fees are not built, and the wallet charges none. The Genesis Drop backend only records registrations (wallet, early-viewer number). It sends no tokens, so the drop can't run until counsel has signed off.
- **Selling personal data without consent.** Every listing needs an active, purpose-specific consent. Withdrawing consent delists the asset immediately. Buyers can only license an asset for uses the contributor permitted. Individual accounts cannot buy data.
- **ei·det·ic self-serve.** Facial and identity matching falls under biometric laws (BIPA, GDPR Art. 9, the EU AI Act). It is sold only to approved organisations.
- **Military self-serve.** ODIN° and the MIL editions can only be contracted with organisations flagged `verified_defense` by an operator. Export-control (ITAR/EAR) screening still has to happen outside this system.

## 4. Open decision: the data-sharing split

The public site and the wallet promise users **85% of eligible net revenue** from opted-in, de-identified usage data. The examples are "85% … to participating users in $FAB", "User reward pool (85%)" and the wallet's Data exchange card. The backend pays less (`MARKETPLACE_RULES` in `src/catalog.ts`): FABRIC and [HRVST] **70%**, Cumulus and SCALVS **80%**. The FABRIC line is even commented "as published", which is no longer true.

The site's figure is a public promise, so pick one number before any data is sold. Either:
- **Match the site:** set `contributorShareBps` to 8500 where the site promises it, and adjust mechanism #8's 20–30% platform cut.
- **Change the site and wallet copy** to the backend's figures.

Either way, update this table. It also matters whether the 85% is paid in USD (the backend's ledger) or in tokens (the site). Tokens fall under §3.

## 5. Launch: the CryptoPublicOffering™ (CPO)

The $FIRM™ raise is framed as a **CryptoPublicOffering™**: IPO-style structure (a public book, a fixed allocation window, a published roadmap), with the energy of a token drop ("get in early").

- **Where:** firmnetwork.app ($Firmament Network™) takes the waitlist: email plus an optional Solana wallet. firmwallet.app ($FIRM Wallet™) takes early access for the wallet and the $FIRM Suite. They are two separate sites, each about its own product, on one Cloudflare Worker (`InfinitumCorp/firmament-launch`), with a separate KV waitlist for each.
- **Chain plan: Solana only (decided 9 Nov 2026).** $FIRM™ and every product token are issued, raised and traded on **Solana**. There is no Ethereum home, no migration and no Base bridge, so CPO holders keep the same token. **The live site still says the old plan**: firmnetwork.app/network says "Raised on Solana. Home on Ethereum.", shows a Solana → Ethereum migration path and an Ethereum row in the token table, and firmwallet.app describes a wallet "for Solana, Ethereum and Base". The corporate /tokens page also lists Ethereum and Base. All of these must be rewritten before launch (next move #15).
- **Any-token swap:** $FIRM™ and every product token trade on Solana, through the wallet's Jupiter aggregation. The wallet's built EVM support (Uniswap on Ethereum/Base/Arbitrum) can stay for holding other assets, but no Infinitum token lives there. Infinitum charges no swap fee today (§3).
- **Countdown:** `LAUNCH_AT = null` in the network page keeps it "coming soon". Set it only once the raise date is final.
- **Revenue role:** the CPO is a **capital** event, not a revenue line. It funds the build-out. Recurring revenue still comes from §1–§2.
- **Gate (same as §3, but sharper):** a public token sale marketed "like an IPO" is about as close to a securities offering as wording can get. Before money is taken, counsel must clear the sale structure (Reg D/Reg S/Reg CF or a non-US-only offering), KYC/AML, geo-blocking of restricted jurisdictions, and the site copy itself. The sites collect interest only. They take no funds and promise no returns, and each carries a risk disclaimer. Keep it that way until sign-off. This shares a deadline with next move #7.

## 6. REVshare: how users and partners earn

One table, matching firmnetwork.app's REVshare section and `MARKETPLACE_RULES` / referral settings in `src/catalog.ts`:

| Program | Contributor / partner share | Platform | Paid as |
|---|---|---|---|
| [HRVST] data packets | 70% | 30% | USD by bank (Stripe Connect) |
| FABRIC aggregates | 70% (site says 85%, see §4) | 30% | USD |
| Cumulus Data Exchange | 80% | 20% | USD |
| SCALVS agent templates | 80% | 20% | USD |
| [HRVST] / Cumulus Missions | bounty to contributor | 15–20% fee | USD |
| Partner referrals | 20% of referred invoices for 12 months | 80% | USD |

Earnings land in a USD ledger. They are held 7–14 days to cover refunds and disputes (refunds and lost disputes claw back automatically), then paid by bank. **Token payouts come later, if at all.** That needs `TOKEN_PAYOUTS_ENABLED=true` and counsel's sign-off (§3). Until then the site says payouts are USD now, tokens later.

## 7. Distribution, hosting and infrastructure cost

- **Direct downloads.** Every product is listed at infinitumcorporation.ai/downloads with its website. `downloads/products.json` is the single source of truth. `node _tools/build-downloads.mjs` regenerates the page and the permanent `/download/<product>/<platform>` links. Until a product is live, its link goes to the product's website. To launch one, set `status: "live"`, add the signed installer URLs, rebuild and deploy. Then the same links serve the installer. Today all 11 products are "coming soon".
- **Bundles.** A plan's `bundle` field grants plans in other products (for example `firm_suite` → `cumulus_drive_free` + `firmament_dev`). The org's own paid subscription always wins over a bundled grant, so upgrades replace the bundled plan cleanly. Entitlements report `grantedBy` so the apps can show where a plan came from.
- **Hosting.** The revenue API runs on Cloudflare Workers with Supabase Postgres and Stripe. The Firmament sites run on Cloudflare Workers + KV. The corporate site (infinitumcorporation.ai) and the defense site (infinitumdefense.com) are still served by Vercel. All of these sit on free or low tiers while we're pre-revenue. **As of 9 Nov 2026 the revenue Worker answers every request with error 1101**: `STRIPE_WEBHOOK_SECRET` is not set, so startup throws. Only `DATABASE_URL` and `STRIPE_SECRET_KEY` are set. Two attempts to deploy the drop API also failed on network resets during upload.
- **DNS and domains.** On 8 Nov 2026, DNS for infinitumcorporation.ai and infinitumdefense.com moved from Vercel DNS to Cloudflare (free plan, nameservers `cecelia` / `ishaan.ns.cloudflare.com`). The records are DNS-only A records for the apex and `*`, pointing at Vercel, so Vercel keeps serving both sites and issuing their certificates. Manage DNS for these two domains in Cloudflare from now on: edits in Vercel's DNS page no longer take effect. Both domains are still *registered* through Vercel (Name.com). The registrar transfer to Cloudflare Registrar (at-cost renewals) is blocked by the 60-day new-registration lock until about **7 Jan 2027**.
- **Google Cloud: free tiers only.** Use GCP wherever a free tier fits, and spend nothing there yet. Good fits:
  - BigQuery sandbox for revenue analytics (export the ledger, not raw PII)
  - Cloud Run and Pub/Sub for background jobs and event fan-out
  - The e2-micro VM
  - Small Cloud Storage
  - The Gemini API free tier, for prototypes only

  Keep customer data off the free Gemini tier, because Google may use free-tier inputs. Don't move the billing API off Workers/Supabase for the sake of GCP. GPU, Vertex AI after the trial and Marketplace billing are paid, so the Cumulus GPU and inference meters have no GCP capacity behind them. Back them with another provider or keep them off.

## 8. Highest-leverage next moves

1. Ship METATRON¹⁰⁰ as the hero bundle. It sells the whole ecosystem in one subscription.
2. Use VEIL (free) + referral codes as the top-of-funnel; measure conversion to SCALVS / CNDCTR Pro.
3. Open hardware **reservations with deposits** now. They fund production and prove demand before manufacturing.
4. Turn services engagements into managed-services retainers (recurring) at handover.
5. Launch [HRVST] Missions with one or two anchor buyers (municipal accessibility mapping, retail shelf audits). Buyers fund supply, so you don't pay for it.
6. Make the free **$FIRM Suite** the default sign-up. It hands every new person a Cumulus://ID, Drive and Mail account, and Cumulus and METATRON can upsell from there. Track how many Suite users upgrade.
7. **Settle the token programme with counsel before 31 Dec 2026.** The Genesis Drop snapshot is that night, and the site's token rewards, the wallet's product-token rewards and the 85% data share all depend on the answer.
8. Commission an external security audit of the $FIRM Wallet before switching its default to mainnet. Until then it stays a testnet showcase.
9. Resolve the data-sharing split in §4 before the exchange opens.
10. Get counsel's sign-off on the CPO structure and site copy (§5) before taking any funds, then set the launch date (`LAUNCH_AT`).
11. Grow the CPO waitlist by tying REVshare referral codes (§6) to waitlist sign-ups, so early supporters become referral partners.
12. Ship the first live installer through `products.json` (likely the $FIRM Suite or VEIL) to prove the direct-download pipeline end to end.
13. On or after 7 Jan 2027, transfer infinitumcorporation.ai and infinitumdefense.com to Cloudflare Registrar (unlock and get auth codes in Vercel, then pay in Cloudflare). That puts DNS, registration and the Workers stack in one place, and renewals at cost.
14. Set `STRIPE_WEBHOOK_SECRET` (and `ADMIN_API_KEY`) on the revenue Worker with `npx wrangler secret put`, which brings the API back. Then merge #6, deploy the Worker, check `GET /v1/drop`, and only then deploy /drop on infinitumcorporation.ai, so the page never goes live against a broken API.
15. Rewrite every public chain claim to Solana-only before the drop: firmnetwork.app (/network hero, migration path, token table, roadmap phase 02), firmwallet.app copy and the corporate /tokens page. Set pool sizes for the drop's final terms at the same time.
