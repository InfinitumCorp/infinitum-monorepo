import { test } from "node:test";
import assert from "node:assert/strict";
import { encryptAead, decryptAead } from "./aead.ts";

test("AEAD round-trip", () => {
  const enc = encryptAead("secret", "password");
  assert.equal(decryptAead(enc, "password"), "secret");
});
