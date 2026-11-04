import { test } from "node:test";
import assert from "node:assert/strict";
import { stripTrackers } from "./privacy.ts";

test("stripTrackers removes utm", () => {
  const clean = stripTrackers("https://example.com/?utm_source=x&id=1");
  assert.equal(clean, "https://example.com/?id=1");
});
