import { serve } from "@hono/node-server";
import { bootFromEnv } from "./boot.js";

const { app } = await bootFromEnv();
const port = Number(process.env.PORT ?? 8787);
serve({ fetch: app.fetch, port }, () => console.log(`Infinitum revenue API on http://localhost:${port}`));
