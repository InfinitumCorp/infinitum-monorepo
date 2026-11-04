# Infinitum Nexus

Shared foundation for every Infinitum product.

| Package | What it is |
|---|---|
| `packages/firmament` | Firmament Network core: append-only, hash-chained, Ed25519-signed ledger for identity, provenance, licensing, payments and audit. Merkle roots for anchoring to Base. |
| `packages/cid` | Cumulus://ID (CID): one account for every product. Passkeys and security keys (WebAuthn), authenticator-app MFA, session revocation, app-by-app permissions recorded on Firmament, organization SSO (OIDC). |

| `apps/spectre` | +SPCTR (Spectre) intelligent CRM. Core so far: companies, contacts, deals, activities and relationships with a Firmament audit trail and source provenance; opt-out handling; predictive pipeline with explained win probability, risk alerts, next best action and monthly forecast; warmest-path relationship map and buying-committee gaps; duplicate detection, merging and stale-data flags (17 passing tests). |

Coming next: the +SPCTR web app (clean, minimal, white), AI account research and plain-language questions, the Spectre agent team, then `$FIRM` and product token contracts.

## Requirements

Node.js 22.18 or newer (runs TypeScript directly; no build step).

## Run the tests

```sh
pnpm test        # every package
pnpm typecheck
```
