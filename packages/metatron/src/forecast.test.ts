import { test } from "node:test";
import assert from "node:assert/strict";
import { project } from "./forecast.ts";
import type { Series } from "./analytics.ts";

test("projects forward", () => {
  const s: Series = { name: "rev", points: [{ t: "1", v: 10 }, { t: "2", v: 20 }] };
  const p = project(s, 2);
  assert.deepEqual(p, [30, 40]);
});
