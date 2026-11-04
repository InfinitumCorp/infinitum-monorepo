import { existsSync, readFileSync, writeFileSync, mkdirSync, chmodSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { Ledger, FileStore, generateSigner, signerFromPrivateKey } from "@infinitum/firmament";
import { CidService, CidStore } from "@infinitum/cid";
import { SpectreCRM, emptyStore } from "../core/crm.ts";
import { createApp } from "./app.ts";
import { aiConfigFromEnv } from "./ai.ts";
import { saveSnapshot, loadSnapshot } from "./persist.ts";

/**
 * Starts +SPCTR.
 *   PORT                 default 3000
 *   SPECTRE_ORIGIN       public origin for passkeys, default http://localhost:PORT
 *   SPECTRE_DATA_DIR     default ./data
 *   ANTHROPIC_API_KEY    enables AI features
 */

const here = dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT ?? 3000);
const origin = process.env.SPECTRE_ORIGIN ?? `http://localhost:${port}`;
const rpId = new URL(origin).hostname;
const dataDir = process.env.SPECTRE_DATA_DIR ?? join(here, "../../data");
mkdirSync(dataDir, { recursive: true });

// The ledger signing key persists so the Firmament record stays verifiable across restarts.
const keyFile = join(dataDir, "firmament-signer.key");
let signer;
if (existsSync(keyFile)) signer = signerFromPrivateKey(readFileSync(keyFile, "utf8").trim());
else {
  signer = generateSigner();
  writeFileSync(keyFile, signer.exportPrivateKey());
  chmodSync(keyFile, 0o600);
}

const ledger = new Ledger(new FileStore(join(dataDir, "firmament.jsonl")), { authorizedSigners: [signer.publicKey] });
const verified = await ledger.verify();
if (!verified.ok) {
  console.error(`Firmament ledger failed verification at entry ${verified.brokenAt}: ${verified.reason}. Refusing to start.`);
  process.exit(1);
}

const cidStore = loadSnapshot(join(dataDir, "cid.json"), new CidStore());
const spectreStore = loadSnapshot(join(dataDir, "spectre.json"), emptyStore());
const cid = new CidService({ ledger, signer, rpId, origins: [origin], store: cidStore });
const crm = new SpectreCRM({ ledger, signer, store: spectreStore });

const ai = aiConfigFromEnv();
const app = createApp({
  cid,
  crm,
  ai,
  publicDir: join(here, "../../public"),
  secureCookies: origin.startsWith("https://"),
  onChange: () => {
    saveSnapshot(join(dataDir, "cid.json"), cidStore);
    saveSnapshot(join(dataDir, "spectre.json"), spectreStore);
  },
});

app.server().listen(port, () => {
  console.log(`+SPCTR running at ${origin}  (ledger: ${verified.length} verified entries, AI: ${ai.apiKey ? "connected" : "not connected"})`);
});
