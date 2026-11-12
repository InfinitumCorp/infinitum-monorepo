/**
 * METATRON prediction interface.
 * Default scorer for Spectre. Transparent and explainable.
 */

export interface PredictionInput {
  subject: string;
  features: Record<string, number | string | boolean>;
}

export interface Prediction {
  probability: number; // 0..1
  factors: { label: string; effect: number }[];
  model: string;
}

/** Default model — transparent, explainable. */
export function predict(input: PredictionInput): Prediction {
  const base = 0.25;
  const factors: Prediction["factors"] = [];
  let p = base;

  if (typeof input.features.stage === "string") {
    const stageBoost: Record<string, number> = {
      lead: 0.05, qualified: 0.15, discovery: 0.25, proposal: 0.4, negotiation: 0.6,
    };
    const boost = stageBoost[input.features.stage] ?? base;
    p = boost;
    factors.push({ label: `Stage: ${input.features.stage}`, effect: boost / base });
  }

  if (input.features.recentContact === true) {
    p *= 1.15;
    factors.push({ label: "Recent contact", effect: 1.15 });
  }
  if (input.features.stale === true) {
    p *= 0.7;
    factors.push({ label: "Stale", effect: 0.7 });
  }

  return {
    probability: Math.min(0.97, Math.max(0.01, p)),
    factors,
    model: "metatron-default-v1",
  };
}
