/**
 * Spectre scorer adapter that uses METATRON as the default.
 */
import { predict, type Prediction } from "@infinitum/metatron";

export function scoreWithMetatron(features: Record<string, number | string | boolean>): Prediction {
  return predict({ subject: "deal", features });
}
