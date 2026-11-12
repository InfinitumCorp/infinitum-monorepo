import { test } from "node:test";
import assert from "node:assert/strict";
import { scoreWithMetatron } from "./score-metatron.ts";

test("scoreWithMetatron returns a probability", () => {
  const p = scoreWithMetatron({ stage: "proposal", recentContact: true });
  assert.ok(p.probability > 0 && p.probability <= 1);
  assert.equal(p.model, "metatron-default-v1");
});
