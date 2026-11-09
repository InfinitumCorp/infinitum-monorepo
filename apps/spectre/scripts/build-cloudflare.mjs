#!/usr/bin/env node
/**
 * Static assets for the Cloudflare Worker (wrangler.jsonc): public/ plus a _headers file with the
 * same security headers the app and the Vercel build send. public/ itself is not changed.
 *   node apps/spectre/scripts/build-cloudflare.mjs && (cd apps/spectre && npx wrangler deploy)
 */
import { cpSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const app = join(dirname(fileURLToPath(import.meta.url)), "..");
const out = join(app, "dist/assets");
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
cpSync(join(app, "public"), out, { recursive: true });

const csp = (frame) => `default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors ${frame}`;
const common = [
  "  X-Content-Type-Options: nosniff",
  "  Referrer-Policy: strict-origin-when-cross-origin",
  "  Strict-Transport-Security: max-age=63072000; includeSubDomains",
];
writeFileSync(join(out, "_headers"), [
  "/*", ...common, "  X-Frame-Options: DENY", `  Content-Security-Policy: ${csp("'none'")}`, "",
  // Public form and booking pages may be embedded on other sites.
  "/form.html", "  ! X-Frame-Options", "  ! Content-Security-Policy", `  Content-Security-Policy: ${csp("*")}`, "",
  "/book.html", "  ! X-Frame-Options", "  ! Content-Security-Policy", `  Content-Security-Policy: ${csp("*")}`, "",
  "/", "  Cache-Control: no-cache", "",
  "/index.html", "  Cache-Control: no-cache", "",
].join("\n"));
console.log(`Built ${out}`);
