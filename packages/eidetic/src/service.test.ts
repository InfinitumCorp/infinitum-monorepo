import { test } from "node:test";
import assert from "node:assert/strict";
import { EideticService } from "./service.ts";

test("match returns empty scaffold", async () => {
  const s = new EideticService();
  const r = await s.match("test");
  assert.equal(r.length, 0);
});
