import { test } from "node:test";
import assert from "node:assert/strict";
import { toEvidence } from "./evidence.ts";
import type { HardwareTelemetry } from "./interface.ts";

test("toEvidence creates a hash", () => {
  const t: HardwareTelemetry = { taskId: "hw_1", status: "ok", data: {}, recordedAt: "2026-01-01" };
  const e = toEvidence(t);
  assert.ok(e.hash.startsWith("0xevidence_"));
});
