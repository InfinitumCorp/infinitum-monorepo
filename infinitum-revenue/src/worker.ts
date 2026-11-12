// Cloudflare Workers entry. Each request (and each cron run) boots the app with
// its own short-lived Postgres connection, then closes it after the response.
import { bootFromEnv } from "./boot.js";
import { flushUnreportedUsage } from "./services/billing.js";

interface ExecutionContext {
  waitUntil(promise: Promise<unknown>): void;
}

export default {
  async fetch(request: Request, _env: unknown, ctx: ExecutionContext): Promise<Response> {
    const { app, ctx: appCtx } = await bootFromEnv({ worker: true });
    try {
      return await app.fetch(request);
    } finally {
      ctx.waitUntil(appCtx.db.close?.() ?? Promise.resolve());
    }
  },

  // wrangler.jsonc → triggers.crons ("*/15 * * * *"): report metered usage to Stripe.
  async scheduled(_controller: unknown, _env: unknown, ctx: ExecutionContext): Promise<void> {
    const { ctx: appCtx } = await bootFromEnv({ worker: true });
    try {
      const result = await flushUnreportedUsage(appCtx);
      console.log("[cron] flush-usage", JSON.stringify(result));
    } finally {
      ctx.waitUntil(appCtx.db.close?.() ?? Promise.resolve());
    }
  },
};
