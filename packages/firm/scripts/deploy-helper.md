# FIRM Deploy Helper

1. Install solc if needed.
2. `pnpm --filter @infinitum/firm compile`
3. Set env:
   - FIRM_RPC_URL
   - FIRM_PRIVATE_KEY
4. `pnpm --filter @infinitum/firm deploy`
5. Optional: `pnpm --filter @infinitum/firm anchor`

Contracts: FirmToken.sol, ProjectToken.sol, Anchor.sol
