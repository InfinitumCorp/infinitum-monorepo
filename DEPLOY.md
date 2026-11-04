# Infinitum Deployment Checklist

## Spectre (+SPCTR™)
- [x] Restore full `apps/spectre/src/server/app.ts` (see `apps/spectre/src/server/RESTORE-APP.md`)
- [x] Confirm METATRON scoring (default-on via main.ts)
- [x] Research and query agent endpoints wired
- [x] Prospect and execution endpoints + UI buttons
- [x] Search, activities, pipeline stats, filters (amount/industry/stage/sort), contact research, metrics breakdown
- [x] Deal rehearsal endpoint + UI, fact deduplication, activity kind filter, industry metrics, export button
- [x] Research activity logging, avg probability, activity summary, research coverage
- [x] lastResearch timestamp, closingBefore filter, truncated research titles
- [x] Research confidence scores, search timeout, /api/research/status, improved agent routing, research error handling
- [x] Research snippets as facts, /api/research/history endpoint
- [x] Average research confidence in /api/research/status
- [x] Tightened snippet fact limits in research parser
- [x] Low-confidence fact count in /api/research/status
- [x] High-confidence fact count in /api/research/status
- [x] minConfidence filter on /api/companies
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
