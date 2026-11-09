import { createHmac } from "node:crypto";
import { fail, type Ctx } from "../context.js";
import type { Db } from "../db.js";

/**
 * Genesis Drop: every public Infinitum token goes live on Solana at 00:00 UTC on
 * 1 January 2027. Early viewers register a Solana address; their place in line
 * sets their tier. Pool sizes and multipliers are drafts until final terms.
 */
export const DROP = {
  network: "solana",
  dropAt: "2027-01-01T00:00:00Z",
  snapshotAt: "2026-12-31T23:00:00Z",
  claimClosesAt: "2027-03-31T23:59:59Z",
  tiers: [
    { id: "genesis", name: "Genesis", from: 1, to: 1000, multiplier: 3 },
    { id: "vanguard", name: "Vanguard", from: 1001, to: 10000, multiplier: 2 },
    { id: "wave", name: "Wave", from: 10001, to: null, multiplier: 1 },
  ],
  tokens: [
    { ticker: "FIRM", product: "$Firmament Network™" },
    { ticker: "VEIL", product: "VEIL</>™" },
    { ticker: "CMLS", product: "Cumulus://Cloud™" },
    { ticker: "SCLV", product: "SCALVS.ai™" },
    { ticker: "ARCT", product: ":\\ARCHITECT\\™" },
    { ticker: "HRVST", product: "[HRVST]™" },
    { ticker: "EIDT", product: "ei·det·ic™" },
    { ticker: "SPCTR", product: "+SPCTR™" },
    { ticker: "MTRN", product: "METATRON¹⁰⁰™" },
    { ticker: "FAB", product: "FabriC++™" },
  ],
} as const;

export const TICKERS = DROP.tokens.map((t) => t.ticker) as unknown as [string, ...string[]];

/** New registrations allowed per client IP in a rolling 24 hours. */
export const MAX_NEW_PER_IP_PER_DAY = 5;

const B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

/** True when `s` is base58 that decodes to exactly 32 bytes (a Solana public key). */
export function isSolanaAddress(s: string): boolean {
  if (!/^[1-9A-HJ-NP-Za-km-z]{32,44}$/.test(s)) return false;
  let n = 0n;
  for (const ch of s) n = n * 58n + BigInt(B58.indexOf(ch));
  let bytes = 0;
  for (let x = n; x > 0n; x >>= 8n) bytes++;
  for (const ch of s) { if (ch === "1") bytes++; else break; }
  return bytes === 32;
}

export function tierFor(position: number) {
  return DROP.tiers.find((t) => position >= t.from && (t.to === null || position <= t.to)) ?? DROP.tiers[DROP.tiers.length - 1];
}

async function count(db: Db): Promise<number> {
  const [r] = await db.query<{ n: string }>("select count(*)::text as n from drop_registrations");
  return Number(r?.n ?? 0);
}

/** Public summary: dates, tiers with how many spots are taken, tokens, total registered. */
export async function dropStatus(db: Db, now = new Date()) {
  const registered = await count(db);
  return {
    ...DROP,
    open: now < new Date(DROP.snapshotAt),
    registered,
    tiers: DROP.tiers.map((t) => ({
      ...t,
      taken: t.to === null ? Math.max(0, registered - t.from + 1) : Math.max(0, Math.min(t.to, registered) - t.from + 1),
    })),
  };
}

async function entry(db: Db, wallet: string) {
  const [row] = await db.query<{ wallet: string; picks: string[]; created_at: string; position: string }>(
    `select r.wallet, r.picks, r.created_at,
       (select count(*) from drop_registrations x where x.seq <= r.seq)::text as position
     from drop_registrations r where r.wallet = $1`, [wallet]);
  if (!row) return null;
  const position = Number(row.position);
  const tier = tierFor(position);
  return { wallet: row.wallet, picks: row.picks, position, tier: { id: tier.id, name: tier.name, multiplier: tier.multiplier }, registeredAt: row.created_at };
}

export async function lookup(db: Db, wallet: string) {
  if (!isSolanaAddress(wallet)) fail(400, "invalid_wallet", "That isn't a Solana address.");
  return (await entry(db, wallet)) ?? fail(404, "not_registered", "This wallet isn't registered for the drop yet.");
}

const ipHash = (ctx: Ctx, ip: string) => createHmac("sha256", ctx.config.adminApiKey || "infinitum-drop-dev").update(ip).digest("hex").slice(0, 32);

/**
 * Register a wallet, or update its picks. Re-registering keeps the original
 * place in line. Closed after the snapshot.
 */
export async function register(ctx: Ctx, input: { wallet: string; picks: string[]; ip?: string; now?: Date }) {
  const { db } = ctx;
  const now = input.now ?? new Date();
  if (now >= new Date(DROP.snapshotAt)) fail(409, "drop_closed", "Registration closed at the snapshot on 31 Dec 2026, 23:00 UTC.");
  const wallet = input.wallet.trim();
  if (!isSolanaAddress(wallet)) fail(400, "invalid_wallet", "That isn't a Solana address. It should be 32 to 44 base58 characters.");
  const picks = [...new Set(input.picks)].filter((p) => (TICKERS as string[]).includes(p));
  if (!picks.length) fail(400, "no_tokens", "Pick at least one token.");

  const existing = await entry(db, wallet);
  if (existing) {
    await db.query("update drop_registrations set picks = $2, updated_at = now() where wallet = $1", [wallet, picks]);
    return { ...(await entry(db, wallet))!, created: false };
  }

  const hash = input.ip ? ipHash(ctx, input.ip) : null;
  if (hash) {
    const [r] = await db.query<{ n: string }>(
      "select count(*)::text as n from drop_registrations where ip_hash = $1 and created_at > now() - interval '24 hours'", [hash]);
    if (Number(r?.n ?? 0) >= MAX_NEW_PER_IP_PER_DAY) fail(429, "too_many_wallets", "Too many wallets registered from this connection today. Try again tomorrow.");
  }
  await db.query("insert into drop_registrations (wallet, picks, ip_hash) values ($1, $2, $3) on conflict (wallet) do nothing", [wallet, picks, hash]);
  return { ...(await entry(db, wallet))!, created: true };
}
