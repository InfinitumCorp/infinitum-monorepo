import { test } from "node:test";
import assert from "node:assert/strict";
import { DataExchange } from "./index.js";
import { createCollectors } from "./collectors.js";

test("product collectors package datasets for all software products", async () => {
  const dx = new DataExchange();
  const collectors = createCollectors(dx);

  const results = await Promise.all(collectors.map((c) => c.collect("cid_testuser")));
  assert.equal(results.length, 14);
  assert.ok(results.every((r) => r.datasetId));

  const listed = dx.listForSale();
  assert.equal(listed.length, 14);

  const products = new Set(listed.map((d) => d.product));
  assert.ok(products.has("scalus"));
  assert.ok(products.has("veil"));
  assert.ok(products.has("fabric"));
  assert.ok(products.has("aether"));
  assert.ok(products.has("hrvst"));
  assert.ok(products.has("firm"));
});
