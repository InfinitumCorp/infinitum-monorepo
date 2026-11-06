# VEIL Next Steps

## Done in v0.4.0 (2026-11-06)
- Anonymous and signed-in workspaces with confirmed, data-clearing switches
- $VEIL ledger at the published launch policy (0.1/min, 240 min/day), human-activity only
- Claim receipts tied to a Cumulus://ID
- Cumulus://ID sign-in via the CID origin's `/api/me`
- Tor hardening: WebRTC limited to the proxy; New Identity
- Removed the placeholder wallet address and fake balance
- PNG icons, package.json and a test suite (`node --test test/`)

## Remaining
- FIRM Wallet key management (passkey / hardware-backed) in the extension
- Deploy the $VEIL claim contract, then wire `buildClaim` to FIRM Wallet signing
- Server-side rewards validation (duplicate accounts, emulator farms) before settlement
- New Tor circuit control (needs the Tor control port; extension cannot reach it)
- Chain decision: the product page says $VEIL is on Solana; packages/firm targets Base
- Phase II+: Cumulus://Mail, Cloud and Nexus panels in the signed-in workspace

See root PRODUCT-DELIVERY.md.
