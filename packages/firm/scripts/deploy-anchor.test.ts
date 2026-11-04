import { test } from "node:test";
import assert from "node:assert/strict";
import { prepareAnchorCall } from "../src/anchor-client.ts";

test("prepareAnchorCall is usable by deploy script", () => {
  const c = prepareAnchorCall("0xabc", "0xdef");
  assert.equal(c.to, "0xabc");
  assert.ok(c.data.startsWith("0x4e4d9fea"));
});
