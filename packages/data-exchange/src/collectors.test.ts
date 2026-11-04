import { test } from "node:test";
import assert from "node:assert/strict";
import { DataExchange } from "./index.js";
import { createCollectors } from "./collectors.js";

test("product collectors package datasets", async () => {
  const dx = new DataExchange();
  const collectors = createCollectors(dx);

  const results = await Promise.all(collectors.map((c) => c.collect("cid_testuser")));
  assert.equal(results.length, 5);
  assert.ok(results.every((r) => r.datasetId));

  const listed = dx.listForSale();
  assert.equal(listed.length, 5);
});
