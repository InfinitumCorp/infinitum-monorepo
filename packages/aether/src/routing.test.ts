import { test } from "node:test";
import assert from "node:assert/strict";
import { findPath } from "./routing.ts";
import type { AetherNode } from "./mesh.ts";

test("finds a path", () => {
  const nodes: AetherNode[] = [
    { id: "a", peers: ["b"] },
    { id: "b", peers: ["a", "c"] },
    { id: "c", peers: ["b"] },
  ];
  const path = findPath(nodes, "a", "c");
  assert.deepEqual(path, ["a", "b", "c"]);
});
