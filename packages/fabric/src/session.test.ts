import { test } from "node:test";
import assert from "node:assert/strict";
import { FabricSession } from "./session.ts";

test("session stores messages", () => {
  const s = new FabricSession("a", "b", "key");
  s.send("a", "b", "hello");
  assert.equal(s.history().length, 1);
});
