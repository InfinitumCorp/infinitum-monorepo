/**
 * METATRON analytics data model.
 * Simple time-series and aggregate helpers for dashboards.
 */
export interface Point {
  t: string;
  v: number;
}

export interface Series {
  name: string;
  points: Point[];
}

export function sum(series: Series): number {
  return series.points.reduce((s, p) => s + p.v, 0);
}

export function avg(series: Series): number {
  return series.points.length ? sum(series) / series.points.length : 0;
}

export function window(series: Series, from: string, to: string): Series {
  return {
    name: series.name,
    points: series.points.filter((p) => p.t >= from && p.t <= to),
  };
}
