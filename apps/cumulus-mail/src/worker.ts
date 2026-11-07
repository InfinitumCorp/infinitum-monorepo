import { createServer } from "node:http";
import { httpServerHandler } from "cloudflare:node";
import handler from "./vercel.ts";

/**
 * Cumulus://Mail on Cloudflare Workers. Static files (public/) are served as Workers static assets;
 * everything else reaches the same hosted handler as the Vercel build, through Workers' Node.js
 * HTTP server support. State lives in Supabase behind the nexus-store function.
 * Configuration comes from the Worker's variables and secrets (see cloudflare.config.ts).
 */
const PORT = 8080;
createServer((req, res) => void handler(req, res)).listen(PORT);

export default httpServerHandler({ port: PORT });
