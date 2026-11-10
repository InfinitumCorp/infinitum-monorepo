import { randomBytes } from "node:crypto";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { migrate, pgliteDb, type Db } from "../src/db.js";
import { FakePayments } from "../src/payments.js";
import type { Ctx } from "../src/context.js";
import { isSolanaAddress, MAX_NEW_PER_IP_PER_DAY, register } from "../src/services/drop.js";

const B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
function b58(bytes: Uint8Array) {
  let n = BigInt("0x" + (Buffer.from(bytes).toString("hex") || "0")), s = "";
  while (n > 0n) { s = B58[Number(n % 58n)] + s; n /= 58n; }
  for (const b of bytes) { if (b === 0) s = "1" + s; else break; }
  return s;
}
const wallet = () => b58(randomBytes(32));

let db: Db, ctx: Ctx, app: ReturnType<typeof createApp>;
beforeEach(async () => {
  db = await pgliteDb();
  await migrate(db);
  ctx = { db, pay: new FakePayments(), config: { publicUrl: "https://infinitum.test", returnHosts: ["infinitumcorporation.ai"], adminApiKey: "a".repeat(40), tokenPayoutsEnabled: false } };
  app = createApp(ctx);
});

async function call(method: string, path: string, body?: unknown, ip = "203.0.113.7") {
  const res = await app.request(path, { method, headers: { "content-type": "application/json", "cf-connecting-ip": ip },
    body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: res.status, body: await res.json() as any };
}

describe("Solana address check", () => {
  it("accepts 32-byte base58 keys and rejects everything else", () => {
    expect(isSolanaAddress("11111111111111111111111111111111")).toBe(true);
    expect(isSolanaAddress("So11111111111111111111111111111111111111112")).toBe(true);
    expect(isSolanaAddress(wallet())).toBe(true);
    expect(isSolanaAddress("0x7a3F9c0000000000000000000000000000c41E")).toBe(false);
    expect(isSolanaAddress("So1111111111111111111111111111111111111111")).toBe(false); // 31 bytes
    expect(isSolanaAddress("O0Il" + "1".repeat(30))).toBe(false);
  });
});

describe("Genesis Drop", () => {
  it("publishes the drop with tiers, Solana-only tokens and a live count", async () => {
    const s = await call("GET", "/v1/drop");
    expect(s.status).toBe(200);
    expect(s.body).toMatchObject({ network: "solana", dropAt: "2027-01-01T00:00:00Z", registered: 0 });
    expect(s.body.tokens).toHaveLength(10);
    expect(s.body.tiers.map((t: any) => t.taken)).toEqual([0, 0, 0]);
  });

  it("numbers wallets in order and keeps a wallet's place when it updates its picks", async () => {
    const a = wallet(), b = wallet();
    const r1 = await call("POST", "/v1/drop/registrations", { wallet: a, picks: ["FIRM", "HRVST"] });
    expect(r1.status).toBe(201);
    expect(r1.body).toMatchObject({ position: 1, tier: { id: "genesis", multiplier: 3 }, created: true });
    const r2 = await call("POST", "/v1/drop/registrations", { wallet: b, picks: ["VEIL"] }, "198.51.100.1");
    expect(r2.body.position).toBe(2);

    const again = await call("POST", "/v1/drop/registrations", { wallet: a, picks: ["FAB"] });
    expect(again.status).toBe(200);
    expect(again.body).toMatchObject({ position: 1, picks: ["FAB"], created: false });

    expect((await call("GET", `/v1/drop/wallets/${b}`)).body.position).toBe(2);
    expect((await call("GET", "/v1/drop")).body.tiers[0].taken).toBe(2);
  });

  it("rejects non-Solana addresses, unknown tokens and unregistered lookups", async () => {
    expect((await call("POST", "/v1/drop/registrations", { wallet: "0x7a3F9c0000000000000000000000000000c41E", picks: ["FIRM"] })).body.error.code)
      .toMatch(/invalid_(wallet|request)/);
    expect((await call("POST", "/v1/drop/registrations", { wallet: wallet(), picks: ["DOGE"] })).status).toBe(400);
    expect((await call("POST", "/v1/drop/registrations", { wallet: wallet(), picks: [] })).status).toBe(400);
    expect((await call("GET", `/v1/drop/wallets/${wallet()}`)).status).toBe(404);
  });

  it("caps new wallets per connection per day", async () => {
    for (let i = 0; i < MAX_NEW_PER_IP_PER_DAY; i++)
      expect((await call("POST", "/v1/drop/registrations", { wallet: wallet(), picks: ["FIRM"] })).status).toBe(201);
    const over = await call("POST", "/v1/drop/registrations", { wallet: wallet(), picks: ["FIRM"] });
    expect(over.status).toBe(429);
    expect((await call("POST", "/v1/drop/registrations", { wallet: wallet(), picks: ["FIRM"] }, "192.0.2.50")).status).toBe(201);
  });

  it("closes at the snapshot", async () => {
    await expect(register(ctx, { wallet: wallet(), picks: ["FIRM"], now: new Date("2026-12-31T23:00:00Z") }))
      .rejects.toMatchObject({ status: 409, code: "drop_closed" });
  });

  it("allows CORS from infinitumcorporation.ai", async () => {
    const res = await app.request("/v1/drop", { headers: { origin: "https://infinitumcorporation.ai" } });
    expect(res.headers.get("access-control-allow-origin")).toBe("https://infinitumcorporation.ai");
  });
});
