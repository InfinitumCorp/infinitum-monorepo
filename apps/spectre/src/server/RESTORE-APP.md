# Restore full Spectre app.ts

The current `app.ts` is a short stub. The full CRM server (auth, routes, scoring, etc.) is in git history.

To restore:

```bash
git show 0d564d553035c96301a055fa067832b58df81b24:apps/spectre/src/server/app.ts > apps/spectre/src/server/app.ts
# Then apply the METATRON import change:
# Replace the intelligence.ts scoreDeal import with:
# import { scoreDealMetatron as scoreDeal } from "../core/intelligence-metatron.ts";
# import { nextBestAction, forecast, riskAlerts, type Score } from "../core/intelligence.ts";
```

Then commit. METATRON remains default-on via `main.ts`.
