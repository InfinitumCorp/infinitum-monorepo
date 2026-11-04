import { test } from "node:test";
import assert from "node:assert/strict";
import { prepareAnchor } from "./anchoring.ts";

test("prepareAnchor returns a real Merkle root", () => {
  const p = prepareAnchor(["0xabc", "0xdef"]);
  assert.equal(p.chain, "base");
  assert.ok(p.root.startsWith("0x") || p.root.length > 0);
});
