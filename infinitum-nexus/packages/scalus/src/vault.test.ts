import { test } from "node:test";
import assert from "node:assert/strict";
import { ContextVault } from "./vault.ts";

test("vault remembers and recalls", () => {
  const v = new ContextVault();
  v.remember("agent1", "hello", ["greeting"]);
  const rec = v.recall("agent1", "greeting");
  assert.equal(rec.length, 1);
  assert.equal(rec[0].content, "hello");
});

test("vault clear removes agent entries", () => {
  const v = new ContextVault();
  v.remember("agent1", "a");
  v.clear("agent1");
  assert.equal(v.recall("agent1").length, 0);
});
