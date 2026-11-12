import { test } from "node:test";
import assert from "node:assert/strict";
import { createEvmWalletScaffold } from "./wallet-evm.ts";

test("scaffold signs a message", async () => {
  const w = createEvmWalletScaffold("0xabc");
  const sig = await w.signMessage("hello");
  assert.ok(sig.startsWith("0xsigned_"));
});
