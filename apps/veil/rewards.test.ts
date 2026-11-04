import { test } from "node:test";
import assert from "node:assert/strict";
import { accrue } from "./rewards.ts";

test("accrue computes veil", () => {
  const e = accrue("cid:user", 1000);
  assert.equal(e.veil, "1.000000");
});
