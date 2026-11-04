import { test } from "node:test";
import assert from "node:assert/strict";
import { Team } from "./team.ts";
import type { Agent } from "./agent.ts";

test("team runs all agents", async () => {
  const a: Agent = { id: "a1", name: "A", role: "r", tools: [] };
  const b: Agent = { id: "b1", name: "B", role: "r", tools: [] };
  const t = new Team([a, b]);
  const r = await t.run("hello");
  assert.equal(r.length, 2);
});
