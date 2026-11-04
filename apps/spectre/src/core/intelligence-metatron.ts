/**
 * METATRON-backed scorer for Spectre.
 * Uses the default METATRON model when USE_METATRON=1, otherwise falls back to the baseline.
 */
import { scoreDeal as baselineScore, type Score } from "./intelligence.ts";
import type { Activity, Deal } from "./model.ts";
import { scoreWithMetatron } from "./score-metatron.ts";

export function scoreDealMetatron(deal: Deal, activities: Activity[], now: Date): Score {
  if (process.env.USE_METATRON !== "1") return baselineScore(deal, activities, now);
  const recent = activities.some((a) => a.dealId === deal.id && now.getTime() - Date.parse(a.at) < 7 * 86_400_000);
  const stale = activities.filter((a) => a.dealId === deal.id).length === 0;
  const pred = scoreWithMetatron({
    stage: deal.stage,
    recentContact: recent,
    stale,
    amount: deal.amount,
  });
  return {
    dealId: deal.id,
    probability: pred.probability,
    base: 0.25,
    factors: pred.factors.map((f) => ({ label: f.label, effect: f.effect })),
    risk: pred.probability < 0.3 ? "high" : pred.probability < 0.6 ? "medium" : "low",
  };
}
