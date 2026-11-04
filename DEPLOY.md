# Infinitum Deployment Checklist

## Spectre (+SPCTR)
- [x] Restore full `apps/spectre/src/server/app.ts` (see `apps/spectre/src/server/RESTORE-APP.md`)
- [x] Confirm METATRON scoring (default-on via main.ts)
- [x] Research and query agent endpoints wired
- [x] Prospect and execution endpoints + UI buttons
- [x] Search, activities, pipeline stats, filters (amount/industry), contact research, metrics breakdown
- [ ] `pnpm --filter @infinitum/spectre typecheck`
- [ ] Deploy via Docker or Vercel (see apps/spectre/Dockerfile and vercel.json)

## Conductor (/CNDCTR/)
- [ ] `pnpm --filter @infinitum/conductor typecheck`
- [ ] Deploy via Docker or Vercel

## FIRM
- [ ] `pnpm --filter @infinitum/firm compile`
- [ ] Set RPC URL and private key env vars
- [ ] `pnpm --filter @infinitum/firm deploy`
- [ ] `pnpm --filter @infinitum/firm anchor` (if anchoring)

## General
- [ ] All tests pass: `pnpm test`
- [ ] Update PRODUCT-DELIVERY.md status after deploy
