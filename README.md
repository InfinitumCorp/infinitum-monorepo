# Infinitum Nexus

Shared foundation for every Infinitum product.

| Package | What it is |
|---|---|
| `packages/firmament` | Firmament Network core: append-only, hash-chained, Ed25519-signed ledger for identity, provenance, licensing, payments and audit. Merkle roots for anchoring to Base. |
| `packages/cid` | Cumulus://ID (CID): one account for every product. Passkeys and security keys (WebAuthn), authenticator-app MFA, session revocation, app-by-app permissions recorded on Firmament, organization SSO (OIDC). |

Coming next: `$FIRM` and product token contracts, then `apps/spectre` (+SPCTR).

## Requirements

Node.js 22.18 or newer (runs TypeScript directly; no build step).

## Run the tests

```sh
pnpm test        # every package
pnpm typecheck
```
