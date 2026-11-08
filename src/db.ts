import { randomBytes } from "node:crypto";
import { SCHEMA_SQL } from "./schema.js";

/** Minimal database surface shared by Postgres (production) and PGlite (tests / local dev). */
export interface Db {
  query<T = Record<string, any>>(sql: string, params?: unknown[]): Promise<T[]>;
  tx<T>(fn: (db: Db) => Promise<T>): Promise<T>;
  exec(sql: string): Promise<void>;
  /** Close the underlying connection(s). The Worker calls this after each request. */
  close?(): Promise<void>;
}

/** This product's schema in the shared "Infinitum Corp" Supabase project. */
export const DB_SCHEMA = "infinitum_revenue";

/**
 * Apply the schema in place (local PGlite and the Node server). On Supabase the
 * same SQL ships as supabase/migrations/*_infinitum_revenue.sql and is applied
 * with `supabase db push`; the Worker never migrates at request time.
 */
export async function migrate(db: Db) {
  await db.exec(SCHEMA_SQL);
}

/**
 * Production: any Postgres via postgres.js, scoped to the `infinitum_revenue`
 * schema (search_path is set on the session before the first query).
 *
 * `perRequest` (Cloudflare Workers): one connection that lives for a single
 * request, because a Worker can't reuse a socket across requests. Point
 * DATABASE_URL at Supabase's *session* pooler (port 5432) or a direct
 * connection so the session-level search_path holds for the whole request.
 */
export async function postgresDb(url: string, opts: { perRequest?: boolean } = {}): Promise<Db> {
  const { default: postgres } = await import("postgres");
  const sql = postgres(url, {
    max: opts.perRequest ? 1 : 5,
    prepare: false,
    fetch_types: false,
    // Startup parameter: covers every connection in the Node pool.
    connection: { search_path: `${DB_SCHEMA}, public` },
  });
  // Poolers may drop startup parameters, so with a single connection also SET
  // it explicitly before the first query.
  const ready = opts.perRequest
    ? sql.unsafe(`set search_path to ${DB_SCHEMA}, public`).then(() => undefined)
    : Promise.resolve();
  const wrap = (s: any): Db => ({
    query: async (q, p = []) => { await ready; return s.unsafe(q, p as any[]) as any; },
    tx: async (fn) => { await ready; return s.begin ? s.begin(async (t: any) => { await t.unsafe(`set local search_path to ${DB_SCHEMA}, public`); return fn(wrap(t)); }) : fn(wrap(s)); },
    exec: async (q) => { await ready; await s.unsafe(q); },
    close: async () => { await sql.end({ timeout: 5 }); },
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
