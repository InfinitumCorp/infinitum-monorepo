import { test } from "node:test";
import assert from "node:assert/strict";
import { scoreDealMetatron } from "./intelligence-metatron.ts";
import type { Deal, Activity } from "./model.ts";

test("scoreDealMetatron falls back without env", () => {
  const deal = {
    id: "d1", stage: "proposal", amount: 1000, closeDate: "2026-12-01",
    stageEnteredAt: new Date().toISOString(), contactIds: [], companyId: "c1", name: "Deal",
  } as Deal;
  const s = scoreDealMetatron(deal, [] as Activity[], new Date());
  assert.ok(s.probability >= 0 && s.probability <= 1);
});
