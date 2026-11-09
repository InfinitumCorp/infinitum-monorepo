# Infinitum monetization map

Every revenue stream across the portfolio on infinitumcorporation.ai, and where the backend implements it.
Prices are **proposed launch prices** (in `src/catalog.ts`), set to be reviewed, not final decisions.

**Where products sell.** Each product sells on its own domain. All of these domains are Cloudflare zones. They are also in `RETURN_HOSTS`, so the product can pass a Stripe `returnUrl` and call the customer API from the browser:

| Product | Domain |
|---|---|
| SCALVS.ai | scalvs.app |
| :\ARCHITECT\ (/CNDCTR/) | rchtch.app |
| METATRON¹⁰⁰ | mttrn.app |
| Cumulus://Cloud, Cumulus://Mail, Cumulus://ID | cumuluscloud.app |

The domains below are Cloudflare zones too, but they are not in `RETURN_HOSTS` yet. Add each one before its product starts checkout:

| Product | Domain |
|---|---|
| FABRIC | infinitumfabric.app |
| VEIL</> | infinitumveil.app |
| VEIL</> VPN | infinitumveilvpn.app |
| FIRM Wallet | firmwallet.app |
| Firmament Network | firmnetwork.app |

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
| **METATRON¹⁰⁰** | Consumer bundle of the whole suite: Personal $19, Family $39, Business $149, annual discount; prediction runs metered. Your best upsell, since it pulls in every other product |
| **FABRIC** | Free personal (growth); Business $8/seat with compliance features; opt-in data revenue share (30% platform) |
| **Cumulus://Cloud** | Drive tiers ($0 / $2.99 / $9.99); pay-as-you-go cloud (storage, egress, vCPU, GPU $2.20/hr, inference); Sovereign regions by contract; 20% of the Data Exchange |
| **ei·det·ic** | Approved organisations only: $2,000/mo platform fee + $0.50 per authorised search. Highest-risk product, so it stays sales-assisted |
| **[HRVST]** | Free to contribute; Pro Contributor $9.99; Buyer Access $499/mo; 30% of licences; 20% on Missions |
| **VEIL</>** | Stays free (as promised). It earns through referral attribution into paid plans, search-partner revenue share and opt-in sponsored placements. Never by selling browsing history, as the site promises |
| **#THRONE** | Hardware margin ($1,999–$8,999), 24-month lease, THRONE Care $199/yr, Rack on-prem suite $1,200/mo |
| **E(O)N / ORBIS / POLLEN / Flower / SLUG** | Hardware with deposits for pre-orders, then recurring fleet software, RaaS ($1,800/unit/mo for SLUG) and maintenance |
| **DAEMON** | Civil WARDEN site packages and ZENITH coverage-as-a-service, quoted by contract |
| **Firmament Network** | Attestation API ($0.001 each, 10k free), permissioned instances $5,000/mo, validator licences |
| **FIRM / FIRM Wallet / product tokens** | See section 3. Not switched on in the backend |
| **ODIN°** | Multi-year defense contracts, milestone-invoiced, confidential descriptions on invoices |
| **Academy** | $29/mo membership; certifications for partners and integrators (feeds the referral channel) |

## 3. What I deliberately did not switch on

- **Token rewards, DEX swap fees and token payouts.** On the site, users "share in the revenue" paid in product tokens that trade on a DEX. In the US that pattern is likely to be treated as a securities offering and money transmission. The backend keeps every earning in a **USD ledger** and pays out by bank (Stripe Connect). Token payouts are behind `TOKEN_PAYOUTS_ENABLED=false`. Turn it on only after counsel signs off on the token programme. Swap fees are not built.
- **Selling personal data without consent.** Every listing needs an active, purpose-specific consent. Withdrawing consent delists the asset immediately. Buyers can only license an asset for uses the contributor permitted. Individual accounts cannot buy data.
- **ei·det·ic self-serve.** Facial and identity matching falls under biometric laws (BIPA, GDPR Art. 9, the EU AI Act). It is sold only to approved organisations.
- **Military self-serve.** ODIN° and the MIL editions can only be contracted with organisations flagged `verified_defense` by an operator. Export-control (ITAR/EAR) screening still has to happen outside this system.

## 4. Highest-leverage next moves

1. Ship METATRON¹⁰⁰ as the hero bundle at **mttrn.app**. It sells the whole ecosystem in one subscription. Done so far:
   - The domain is an active Cloudflare zone.
   - It is in `RETURN_HOSTS`.
   - It is in the Cumulus://ID CORS allowlist, with sign-in callbacks at `https://mttrn.app/api/auth/oauth/{provider}/callback`.

   Still to do: put a site or Worker on the domain that shows the `metatron_personal`, `metatron_family` and `metatron_business` checkout. The Personal plan has a 30-day trial.
2. Use VEIL (free) + referral codes as the top-of-funnel; measure conversion to SCALVS / CNDCTR Pro.
3. Open hardware **reservations with deposits** now. They fund production and prove demand before manufacturing.
4. Turn services engagements into managed-services retainers (recurring) at handover.
5. Launch [HRVST] Missions with one or two anchor buyers (municipal accessibility mapping, retail shelf audits). Buyers fund supply, so you don't pay for it.
