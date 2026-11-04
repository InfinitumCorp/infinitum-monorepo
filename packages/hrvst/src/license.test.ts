import { test } from "node:test";
import assert from "node:assert/strict";
import { createPacket } from "./packet.ts";
import { licensePacket, keepInSilo } from "./license.ts";

test("license and silo", () => {
  const p = createPacket("cid:user", "image/jpeg", "abc");
  const l = licensePacket(p, "cid:buyer", "non-exclusive");
  assert.equal(l.packetId, p.id);
  const s = keepInSilo(p);
  assert.equal(s.silo, true);
});
