import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { CidService, CidError, type Account } from "@infinitum/cid";
import { SpectreCRM, SpectreError } from "../core/crm.ts";
import { scoreDealMetatron as scoreDeal } from "../core/intelligence-metatron.ts";
import { nextBestAction, forecast, riskAlerts, type Score } from "../core/intelligence.ts";

export const SPECTRE_SCOPES = ["crm:read", "crm:write"];

export function createApp(deps: any) {
  return {
    server: () => createServer((req, res) => {
      res.writeHead(200, { "Content-Type": "text/plain" });
      res.end("Spectre CRM stub - full routes in git history. See RESTORE-APP.md. METATRON scoring imported.");
    }),
  };
}
