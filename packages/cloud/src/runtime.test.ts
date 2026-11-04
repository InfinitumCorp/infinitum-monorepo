import { test } from "node:test";
import assert from "node:assert/strict";
import { AgentRuntime } from "./runtime.ts";

test("runtime enqueues tasks", () => {
  const r = new AgentRuntime();
  r.enqueue("a1", "do something");
  assert.equal(r.list().length, 1);
});
