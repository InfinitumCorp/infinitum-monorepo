# Infinitum Deployment Checklist

## Spectre (+SPCTR)
- [ ] Restore full `apps/spectre/src/server/app.ts` (see `apps/spectre/src/server/RESTORE-APP.md`)
- [ ] Confirm METATRON scoring (default-on via main.ts)
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
