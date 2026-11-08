import { DB_SCHEMA, migrate, pgliteDb, postgresDb } from "./db.js";
import { FakePayments, StripePayments } from "./payments.js";
import type { Ctx } from "./context.js";
import { createApp } from "./app.js";

/**
 * Builds the app from environment variables. Without DATABASE_URL / STRIPE_SECRET_KEY it runs fully local.
 *
 * `worker: true` (Cloudflare Workers): DATABASE_URL is required, the connection
 * lives for one request, and the schema is assumed migrated (supabase db push).
 */
export async function bootFromEnv(opts: { worker?: boolean } = {}) {
  if (opts.worker && !process.env.DATABASE_URL) throw new Error("DATABASE_URL is required on Workers (Supabase session pooler for the Infinitum Corp project)");
  const db = process.env.DATABASE_URL
    ? await postgresDb(process.env.DATABASE_URL, { perRequest: opts.worker })
    : await pgliteDb(process.env.PGLITE_DIR ?? "./.data");
  if (!opts.worker) {
    if (process.env.DATABASE_URL) await db.exec(`create schema if not exists ${DB_SCHEMA}`);
    await migrate(db);
  }
  const pay = process.env.STRIPE_SECRET_KEY ? StripePayments.fromEnv() : new FakePayments();
  if (process.env.NODE_ENV === "production" && pay instanceof FakePayments) throw new Error("STRIPE_SECRET_KEY is required in production");
  const adminApiKey = process.env.ADMIN_API_KEY ?? "";
  if (adminApiKey.length < 32) console.warn("ADMIN_API_KEY missing or shorter than 32 chars: admin routes are effectively disabled.");
  const ctx: Ctx = {
    db, pay,
    config: {
      publicUrl: process.env.PUBLIC_URL ?? "http://localhost:3000",
      returnHosts: (process.env.RETURN_HOSTS ?? "rchtch.app,scalvs.app,cumuluscloud.app,infinitumcorp.workers.dev")
        .split(",").map((h) => h.trim().toLowerCase()).filter(Boolean),
      adminApiKey: adminApiKey.length >= 32 ? adminApiKey : "",
      licenceSigningKeyPem: process.env.LICENCE_SIGNING_KEY?.replace(/\\n/g, "\n"),
      tokenPayoutsEnabled: process.env.TOKEN_PAYOUTS_ENABLED === "true",
    },
  };
  return { ctx, app: createApp(ctx) };
}
