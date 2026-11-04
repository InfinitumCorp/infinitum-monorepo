import { test } from "node:test";
import assert from "node:assert/strict";
import { task, telemetry } from "./interface.ts";

test("task and telemetry", () => {
  const t = task("ORBIS", "scan");
  const tel = telemetry(t.id, "ok", { range: 100 });
  assert.equal(tel.taskId, t.id);
});
