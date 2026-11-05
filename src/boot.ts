import { migrate, pgliteDb, postgresDb } from "./db.js";
import { FakePayments, StripePayments } from "./payments.js";
import type { Ctx } from "./context.js";
import { createApp } from "./app.js";

/** Builds the app from environment variables. Without DATABASE_URL / STRIPE_SECRET_KEY it runs fully local. */
export async function bootFromEnv() {
  if (process.env.VERCEL && !process.env.DATABASE_URL) throw new Error("DATABASE_URL is required on Vercel (add Neon from the Marketplace)");
  const db = process.env.DATABASE_URL ? await postgresDb(process.env.DATABASE_URL) : await pgliteDb(process.env.PGLITE_DIR ?? "./.data");
  await migrate(db);
  const pay = process.env.STRIPE_SECRET_KEY ? StripePayments.fromEnv() : new FakePayments();
  if (process.env.NODE_ENV === "production" && pay instanceof FakePayments) throw new Error("STRIPE_SECRET_KEY is required in production");
  const adminApiKey = process.env.ADMIN_API_KEY ?? "";
  if (adminApiKey.length < 32) console.warn("ADMIN_API_KEY missing or shorter than 32 chars: admin routes are effectively disabled.");
  const ctx: Ctx = {
    db, pay,
    config: {
      publicUrl: process.env.PUBLIC_URL ?? "http://localhost:3000",
      adminApiKey: adminApiKey.length >= 32 ? adminApiKey : "",
      licenceSigningKeyPem: process.env.LICENCE_SIGNING_KEY?.replace(/\\n/g, "\n"),
      tokenPayoutsEnabled: process.env.TOKEN_PAYOUTS_ENABLED === "true",
    },
  };
  return { ctx, app: createApp(ctx) };
}
