import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { CidService, CidError, type Account } from "@infinitum/cid";
import { SpectreCRM, SpectreError } from "../core/crm.ts";
import { scoreDealMetatron as scoreDeal } from "../core/intelligence-metatron.ts";
import { nextBestAction, forecast, riskAlerts, type Score } from "../core/intelligence.ts";

export const SPECTRE_SCOPES = ["crm:read", "crm:write"];

export function createApp(deps: any) {
  const handle = async (req: IncomingMessage, res: ServerResponse) => {
    const url = new URL(req.url || "/", "http://localhost");
    if (url.pathname === "/health") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ status: "ok", metatron: true }));
      return;
    }
    res.writeHead(200, { "Content-Type": "text/plain" });
    res.end("Spectre CRM - METATRON scoring imported. Full routes pending restore from history.");
  };
  return { server: () => createServer((req, res) => void handle(req, res)) };
}
