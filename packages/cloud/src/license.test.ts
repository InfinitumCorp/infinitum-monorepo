import { test } from "node:test";
import assert from "node:assert/strict";
import { grant } from "./license.ts";

test("grant creates a license", () => {
  const g = grant("file.txt", "cid:buyer", "view");
  assert.equal(g.objectKey, "file.txt");
});
