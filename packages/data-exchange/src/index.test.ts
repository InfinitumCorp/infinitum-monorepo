import { test } from "node:test";
import assert from "node:assert/strict";
import { DataExchange } from "./index.js";

test("package dataset and sell with rev share", () => {
  const dx = new DataExchange();

  const { dataset } = dx.packageDataset({
    cid: "cid_user123",
    product: "scalus",
    title: "Anonymized agent task patterns",
    description: "Aggregated workflow success rates from SCALVS agents",
    schema: "{agentType, successRate, avgDuration}",
    sizeEstimate: 50000,
    privacyLevel: "aggregated",
    licenseTerms: "CDAT-research-1y",
    priceUsd: 250,
    scopes: ["usage_metrics"],
  });

  assert.equal(dataset.status, "draft");
  dx.catalog.updateStatus(dataset.id, "listed");

  const sale = dx.sell(dataset.id, "broker_acme", 250);
  assert.ok(sale);
  assert.equal(sale!.userShareUsd, 212.5); // 85%
  assert.equal(sale!.platformShareUsd, 37.5);

  assert.equal(dx.revshare.pendingForCid("cid_user123"), 212.5);
});
