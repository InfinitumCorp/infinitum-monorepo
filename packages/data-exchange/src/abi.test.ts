import { test } from "node:test";
import assert from "node:assert/strict";
import { CDAT_MINT_SELECTOR, encodeCdatBurn, encodeCdatMint } from "./abi.ts";

test("mint calldata uses the CdatLicense selector and carries both strings", () => {
  const data = encodeCdatMint({
    licensee: "0x0000000000000000000000000000000000000001",
    datasetId: "ds_1",
    terms: "CDAT-commercial-1y",
    expiresAt: 1_800_000_000,
    amountUsdCents: 25000,
  });
  assert.ok(data.startsWith("0x" + CDAT_MINT_SELECTOR));
  const body = data.slice(2 + 8);
  assert.equal(body.length % 64, 0);
  // address is the first word
  assert.ok(body.startsWith("0000000000000000000000000000000000000000000000000000000000000001"));
  assert.ok(body.includes(Buffer.from("ds_1").toString("hex")));
  assert.ok(body.includes(Buffer.from("CDAT-commercial-1y").toString("hex")));
});

test("burn calldata is selector plus the token id", () => {
  assert.equal(
    encodeCdatBurn(7),
    "0x42966c680000000000000000000000000000000000000000000000000000000000000007",
  );
});

test("mint rejects a non-address licensee", () => {
  assert.throws(() =>
    encodeCdatMint({
      licensee: "broker_acme",
      datasetId: "ds",
      terms: "1y",
      expiresAt: 1,
      amountUsdCents: 1,
    }),
  );
});
