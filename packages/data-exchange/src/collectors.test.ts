import { test } from "node:test";
import assert from "node:assert/strict";
import { DataExchange } from "./index.js";
import { createCollectors } from "./collectors.js";

test("product collectors package datasets", () => {
  const dx = new DataExchange();
  const collectors = createCollectors(dx);

  const results = collectors.map((c) => c.collect("cid_testuser"));
  assert.equal(results.length, 3);
  assert.ok(results.every((r) => r.datasetId));

  const listed = dx.listForSale();
  assert.equal(listed.length, 3);
});
