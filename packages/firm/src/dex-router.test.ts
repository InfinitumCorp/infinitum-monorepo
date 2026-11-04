import { test } from "node:test";
import assert from "node:assert/strict";
import { encodeGetAmountsOut, encodeSwapExactTokensForTokens } from "./dex-router.ts";

test("encodeGetAmountsOut starts with selector", () => {
  const data = encodeGetAmountsOut("1000", ["0xaaa", "0xbbb"]);
  assert.ok(data.startsWith("0xd06ca61f"));
});

test("encodeSwap starts with selector", () => {
  const data = encodeSwapExactTokensForTokens("1000", "900", ["0xaaa", "0xbbb"], "0xccc", 1_700_000_000);
  assert.ok(data.startsWith("0x38ed1739"));
});
