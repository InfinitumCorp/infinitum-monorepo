import { test } from "node:test";
import assert from "node:assert/strict";
import { AetherMesh } from "./mesh.ts";

test("nodes join and connect", () => {
  const m = new AetherMesh();
  m.join("a");
  m.join("b");
  m.connect("a", "b");
  assert.equal(m.list().length, 2);
});
