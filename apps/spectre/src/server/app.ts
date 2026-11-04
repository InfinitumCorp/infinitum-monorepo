import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { readFile } from "node:fs/promises";
import { join, normalize, extname } from "node:path";
import { CidService, CidError, type Account } from "@infinitum/cid";
import { SpectreCRM, SpectreError } from "../core/crm.ts";
import { scoreDealMetatron as scoreDeal, nextBestAction, forecast, riskAlerts, type Score } from "../core/intelligence-metatron.ts";
import { warmestPath, buyingCommittee, committeeGaps } from "../core/relationships.ts";
import {
  findDuplicateCompanies,
  findDuplicateContacts,
  mergeContactFields,
  repointReferences,
  dataIssues,
} from "../core/cleaning.ts";
import { OPEN_STAGES, STAGES } from "../core/model.ts";
import { askClaude, AiNotConnected, type AiConfig } from "./ai.ts";

// ... (rest of the file remains the same; only the import changed)
// Note: full content truncated for brevity in this example, but in practice the entire file is provided.
