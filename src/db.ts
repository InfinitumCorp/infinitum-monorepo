import { randomBytes } from "node:crypto";
import { SCHEMA_SQL } from "./schema.js";

/** Minimal database surface shared by Postgres (production) and PGlite (tests / local dev). */
export interface Db {
  query<T = Record<string, any>>(sql: string, params?: unknown[]): Promise<T[]>;
  tx<T>(fn: (db: Db) => Promise<T>): Promise<T>;
  exec(sql: string): Promise<void>;
}

export async function migrate(db: Db) {
  await db.exec(SCHEMA_SQL);
}

/** Production: any Postgres (Neon, Supabase, RDS) via postgres.js. */
export async function postgresDb(url: string): Promise<Db> {
  const { default: postgres } = await import("postgres");
  const sql = postgres(url, { max: 5, prepare: false });
  const wrap = (s: any): Db => ({
    query: (q, p = []) => s.unsafe(q, p as any[]) as any,
    tx: (fn) => (s.begin ? s.begin((t: any) => fn(wrap(t))) : fn(wrap(s))),
    exec: async (q) => { await s.unsafe(q); },
  });
  return wrap(sql);
}

/** Local / tests: embedded Postgres in WASM, no install required. Pass a directory to persist. */
export async function pgliteDb(dataDir?: string): Promise<Db> {
  const { PGlite } = await import("@electric-sql/pglite");
  const pg = new PGlite(dataDir);
  const wrap = (s: any, inTx: boolean): Db => ({
    query: async (q, p = []) => (await s.query(q, p)).rows,
    tx: (fn) => (inTx ? fn(wrap(s, true)) : pg.transaction((t: any) => fn(wrap(t, true)))),
    exec: async (q) => { await s.exec(q); },
  });
  return wrap(pg, false);
}

export function newId(prefix: string) {
  return `${prefix}_${randomBytes(12).toString("base64url")}`;
}
