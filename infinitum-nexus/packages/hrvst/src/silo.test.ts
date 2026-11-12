import { test } from "node:test";
import assert from "node:assert/strict";
import { PersonalSilo } from "./silo.ts";
import { createPacket } from "./packet.ts";

test("silo keeps packets", () => {
  const s = new PersonalSilo();
  const p = createPacket("cid:user", "image/jpeg", "abc");
  s.keep(p);
  assert.equal(s.list("cid:user").length, 1);
});
