import { test } from "node:test";
import assert from "node:assert/strict";
import { sum, avg, window, type Series } from "./analytics.ts";

test("sum and avg", () => {
  const s: Series = { name: "rev", points: [{ t: "2026-01", v: 10 }, { t: "2026-02", v: 20 }] };
  assert.equal(sum(s), 30);
  assert.equal(avg(s), 15);
});

test("window filters", () => {
  const s: Series = { name: "rev", points: [{ t: "2026-01", v: 10 }, { t: "2026-03", v: 30 }] };
  const w = window(s, "2026-01", "2026-02");
  assert.equal(w.points.length, 1);
});
