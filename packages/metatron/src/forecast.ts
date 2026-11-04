/**
 * METATRON forecast helper.
 * Simple linear projection from a series.
 */
import type { Series } from "./analytics.ts";

export function project(series: Series, periods: number): number[] {
  if (series.points.length < 2) return [];
  const last = series.points[series.points.length - 1].v;
  const prev = series.points[series.points.length - 2].v;
  const delta = last - prev;
  const out: number[] = [];
  for (let i = 1; i <= periods; i++) out.push(last + delta * i);
  return out;
}
