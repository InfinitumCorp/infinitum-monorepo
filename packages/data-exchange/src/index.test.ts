import { test } from "node:test";
import assert from "node:assert/strict";
import { DataExchange } from "./index.js";
import { Ledger, generateSigner } from "@infinitum/firmament";

test("package dataset and sell with rev share", async () => {
  const dx = new DataExchange();

  const { dataset } = await dx.packageDataset({
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

  const sale = await dx.sell(dataset.id, "broker_acme", 250);
  assert.ok(sale);
  assert.equal(sale!.userShareUsd, 212.5); // 85%
  assert.equal(sale!.platformShareUsd, 37.5);

  assert.equal(dx.revshare.pendingForCid("cid_user123"), 212.5);
});

test("Firmament records consent grant", async () => {
  const ledger = new Ledger();
  const signer = generateSigner();
  ledger.authorize(signer.publicKey);

  const dx = new DataExchange(ledger, signer);

  const { dataset, consent } = await dx.packageDataset({
    cid: "cid_firmament",
    product: "cumulus",
    title: "Test dataset",
    description: "Test",
    schema: "x",
    sizeEstimate: 1,
    privacyLevel: "aggregated",
    licenseTerms: "CDAT-test",
    priceUsd: 10,
    scopes: ["usage_metrics"],
  });

  assert.ok(consent.firmamentTx);
  const entries = await ledger.entries();
  assert.equal(entries.length, 1);
  assert.equal(entries[0].type, "grant");
  assert.equal(entries[0].subject, dataset.id);
  const verified = await ledger.verify();
  assert.equal(verified.ok, true);
});
