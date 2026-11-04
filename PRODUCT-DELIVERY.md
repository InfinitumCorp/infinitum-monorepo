# Product delivery requirements

Source: https://infinitum-corporation.vercel.app/ and its linked product pages, reviewed 2026-11-04.

The acceptance scope is every advertised product and feature. Milestones below are implementation ordering, not reductions in scope. A passing core-library test does not establish that a product works end to end. No product below is verified complete.

## Evidence available

At commit 09db5c605f22031a1acd3873656e7a6d81118f13 this repository contains Firmament ledger primitives, CID authentication primitives and Spectre CRM core. There are no web servers, shipped client apps, deployed token contracts or device implementations in that tree. Spectre's start script refers to a missing server entry point. Other accessible account repositories contain marketing sites and unrelated projects; their presence does not establish implementation of the ecosystem.

## Full delivery matrix

| Product / specification | Required behavior | Existing evidence / remaining delivery |
|---|---|---|
| [Firmament](https://infinitum-corporation.vercel.app/products/firmament-network) | Identity, provenance, licenses, settlement, permissioned deployments, EVM APIs, bridges, contract analysis, anomaly monitoring, nodes | Signed local ledger and Merkle proofs exist. Network consensus, actual anchoring, licensing, settlement, monitoring and operational deployment unverified. A hash chain is not a deployed blockchain. |
| CID, within [Cumulus](https://infinitum-corporation.vercel.app/products/cumulus-exchange#cid) | Shared sign-in, passkeys, keys, MFA, session revocation, federation, app permission dashboard | Core library exists; durable identity service, provisioning and integrated clients unverified. Allocating a mail address is not provisioning an inbox. |
| [+SPCTR](https://infinitum-corporation.vercel.app/products/spectre) | CRM, automatic account research and capture, relationship intelligence, forecasting, conversation intelligence, natural-language queries, six agent roles, deal rehearsal, customer-owned profiles, co-signed records, self-executing deals, competitive simulations | CRM, scoring, graph and cleaning functions exist. Web app, durable backend, live connectors, AI, customer signatures, payments and full advertised workflows remain. Existing scores need calibration evidence before predictive accuracy claims. |
| [SCALVS.ai](https://infinitum-corporation.vercel.app/products/scalus) | Agent studio, tools/accounts, teams, schedules, approvals, cost controls, web/mobile web, extensions, private runtime, ads, licensing and verified capped rewards | No product implementation in this tree. Requires actual model/tool providers, tenant isolation and the shared Context Vault. |
| [/CNDCTR/](https://infinitum-corporation.vercel.app/products/cndctr) | Executable Scores, integrations/visual control, three autonomy modes, Podium, verification evidence, recovery, reruns, handoffs, triggers, budgets, private execution and marketplace | No implementation. Requires durable execution state, permission checks, approval-bound actions, idempotency and real outcome verification. |
| [Cumulus://Cloud](https://infinitum-corporation.vercel.app/products/cumulus-exchange) | Drive, Mail, compute, storage, databases, AI, networking, encryption, customer keys, regions, hybrid nodes, opt-in licensing with 85% contributor share | CID core only. Requires provisioned infrastructure, mail routing/domains, metering, backups, tenant isolation and real service adapters. |
| [VEIL](https://infinitum-corporation.vercel.app/products/veil) | Tor Browser-derived desktop engine, Android and iOS editions, isolated anonymous/authenticated workspaces, leak defenses, native services, signed updates, free access, private verified browsing rewards | No browser source/builds. An embedded webview or Tor proxy alone does not meet the advertised browser protections. Requires upstream source, platform builds and privacy/security verification. |
| [FIRM and Wallet](https://infinitum-corporation.vercel.app/products/firm) | Self-custody Ethereum/Base/Solana wallet, recovery, hardware-backed keys, approval UI, rewards inbox, built-in DEX, VEIL and mobile editions | No wallet implementation or verified deployed contracts. Requires chain adapters, audited key handling, actual liquidity/routes and device tests. |
| [Product tokens](https://infinitum-corporation.vercel.app/products/project-tokens) | Fixed supplies, published emissions, verified contribution rewards, FIRM/USDC routes and cross-product spending | No contracts in this tree. Supply/emission decisions must be explicit per token; no invented addresses, prices, liquidity or simulated payouts presented as real. |
| [FABRIC](https://infinitum-corporation.vercel.app/products/fabric) | Device-to-device encrypted text/voice/video/files, on-device AI and translation, CID addresses, mesh transport, approved CRM logging, opt-in licensing with 70% eligible net revenue sharing | No implementation. Needs reviewed messaging protocol, client key lifecycle, call transport and local inference. |
| [METATRON](https://infinitum-corporation.vercel.app/products/metatron) | Consumer command center, integrated products, forecasts, scenarios, agents, permissions, desktop/browser clients and open-source delivery | No implementation. The advertised 10^100 futures claim needs a defined computational interpretation and evidence; it must not be asserted as verified capability. |
| [HRVST](https://infinitum-corporation.vercel.app/products/hrvst) | Compatible glasses capture, privacy filtering, reviewed Packets, private archive, missions, co-ops, provenance, licenses and payouts | No implementation. Requires real device input, redaction quality tests, restricted-location handling and licensed buyer workflow. |
| [ei·det·ic](https://infinitum-corporation.vercel.app/products/eidetic) | Consent enrollment/withdrawal, authorized purpose and dataset checks, matching evidence/confidence, mandatory review, accuracy/bias reporting and isolated deployment | No implementation. Requires consented evaluation data, measured matching performance and isolated processing; no biometric source data on public ledgers. |
| [ARGUS](https://infinitum-corporation.vercel.app/products/argus) | Authorized live feed ingestion, temporal/spatial fusion, detection/tracking, evidence retrieval, forecasts and edge operation | No implementation. Requires actual sensor adapters, calibrated models, performance measurements and disconnected operation tests. |
| [ÆTHER](https://infinitum-corporation.vercel.app/products/aether) | Hardened communications, mesh networking and advertised edition-specific connectivity | No implementation. Requires radio/network hardware, transport integration and physical connectivity tests. |
| [E(O)N](https://infinitum-corporation.vercel.app/products/eon) | Secure wearable communications, sensing, spectrum awareness, offline voice assistant, hardware identity and battery management | No device/firmware implementation. Requires wearable hardware, secure element and measured field tests. |
| [#THRONE](https://infinitum-corporation.vercel.app/products/throne) | Advertised laptops, towers, rack and rugged editions, hardware key, local AI and cloud/node runtime | No hardware implementation. Requires designs, vendor/prototype access, firmware and physical acceptance tests. |
| [ORBIS](https://infinitum-corporation.vercel.app/products/orbis) | 220 mm collision-tolerant spherical aircraft, indoor navigation, 3D maps, imagery and cradle charging | No hardware/flight code. Requires prototype, sensor suite, mechanical validation and supervised flight tests. |
| [POLLEN](https://infinitum-corporation.vercel.app/products/pollen) | Palm-sized coordinated aircraft, resilient distributed mapping/mesh, limits/no-go zones, Flower integration | No hardware/flight code. Requires aircraft and a supervised civil inspection test environment; simulation alone does not satisfy physical operation. |
| [Flower](https://infinitum-corporation.vercel.app/products/flower) | Autonomous launch/recovery, charging, inspections, firmware attestations, data offload and network backhaul | No station hardware/firmware. Requires actual dock and aircraft interoperability tests. |
| [NEMESIS](https://infinitum-corporation.vercel.app/products/nemesis) | Civil inspection/reconnaissance FPV platform, flight assist, obstacle awareness, return-home, telemetry and fleet records | No aircraft implementation. Requires hardware, flight controller integration and supervised non-weaponized testing. |
| [ODIN](https://infinitum-corporation.vercel.app/products/odin) | Integrated defense edition, private permissioned audit, classification isolation, human approval and no public token/data-sale path | No integrated implementation. Hardware, accredited deployment and authorized operating environment are unavailable here. Consumer integration tests do not establish defense suitability. |

## Shared delivery order

1. Stabilize and verify existing identity, ledger and CRM code. Add durable storage and authenticated service boundaries.
2. Implement the permissioned Context Vault, product registry, app permissions and event contracts. Verify tenant and purpose isolation, expiry and revocation across products.
3. Add durable SCALVS execution and Conductor approvals/recovery with real providers, then wire Spectre and METATRON workflows.
4. Provision Cumulus services and integrate consent, licensing, metering and payments. Keep token issuance and live settlement distinct from internal accounting.
5. Build native VEIL, wallet and communications clients and verify their advertised privacy properties on target platforms.
6. Integrate capture/sensor services and physical devices against actual hardware. Complete device, interoperability and deployment validation before calling those products delivered.

## Acceptance evidence required per advertised capability

- Link the requirement to implementation, a runnable scenario and captured results.
- Test actual connected services; label fixtures, simulations and unavailable integrations explicitly.
- Verify cross-product identity, consent boundaries, revocation, isolation, failure recovery and audit integrity.
- Verify financial flows on test networks before any authorized production settlement; a displayed balance is not a transfer.
- Record hardware/provisioning/provider dependencies as outstanding until actually available and tested.
- Keep the website's full requirements visible until every capability has evidence. This document is a baseline delivery map, not proof of completion.
