import { test } from "node:test";
import assert from "node:assert/strict";
import { FirmamentVault } from "./memory-firmament.ts";

test("persist returns a hash", async () => {
  const v = new FirmamentVault();
  const e = v.remember("a1", "note");
  const p = await v.persist(e);
  assert.ok(p.hash.startsWith("0xfirm_"));
});
