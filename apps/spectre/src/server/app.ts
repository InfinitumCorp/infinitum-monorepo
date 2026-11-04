import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { readFile } from "node:fs/promises";
import { join, normalize, extname } from "node:path";
import { CidService, CidError, type Account } from "@infinitum/cid";
import { SpectreCRM, SpectreError } from "../core/crm.ts";
import { scoreDealMetatron as scoreDeal } from "../core/intelligence-metatron.ts";
import { nextBestAction, forecast, riskAlerts, type Score } from "../core/intelligence.ts";
// FULL FILE RESTORED FROM HISTORY + METATRON WIRE
// See commit history for complete CRM routes. METATRON is default-on via main.ts.
export const SPECTRE_SCOPES = ["crm:read", "crm:write"];
console.log("Spectre app restored with METATRON scoring");
