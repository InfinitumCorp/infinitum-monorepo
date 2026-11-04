/**
 * Product-specific data collectors.
 * Each product implements a collector that extracts eligible, consented data
 * and feeds it into the Data Exchange.
 *
 * Rules:
 * - Always check consent first
 * - Never collect military/ODIN° data for public exchange
 * - Prefer aggregated / anonymized
 * - Record provenance on Firmament
 */
import type { ProductId } from "./types.js";
import type { DataExchange } from "./index.js";

export interface CollectorResult {
  datasetId?: string;
  skippedReason?: string;
}

export abstract class ProductCollector {
  abstract product: ProductId;
  constructor(protected dx: DataExchange) {}

  abstract collect(cid: string, opts?: Record<string, unknown>): Promise<CollectorResult> | CollectorResult;
}

/** SCALVS.ai: agent usage metrics, workflow patterns */
export class ScalusCollector extends ProductCollector {
  product: ProductId = "scalus";

  collect(cid: string): CollectorResult {
    // In real impl: query agent runtime for anonymized stats
    const { dataset } = this.dx.packageDataset({
      cid,
      product: this.product,
      title: "SCALVS agent activity aggregates",
      description: "Anonymized success rates and tool usage from AI workforce agents",
      schema: "agentType,taskCount,successRate,avgTokens",
      sizeEstimate: 100_000,
      privacyLevel: "aggregated",
      licenseTerms: "CDAT-commercial-1y",
      priceUsd: 150,
      scopes: ["usage_metrics", "workflow_stats"],
    });
    this.dx.catalog.updateStatus(dataset.id, "listed");
    return { datasetId: dataset.id };
  }
}

/** Cumulus://Cloud: storage usage, file type distributions (not content) */
export class CumulusCollector extends ProductCollector {
  product: ProductId = "cumulus";

  collect(cid: string): CollectorResult {
    const { dataset } = this.dx.packageDataset({
      cid,
      product: this.product,
      title: "Cumulus storage & compute usage",
      description: "Aggregated storage categories and compute hours (no file contents)",
      schema: "category,gbStored,computeHours",
      sizeEstimate: 20_000,
      privacyLevel: "aggregated",
      licenseTerms: "CDAT-research-1y",
      priceUsd: 80,
      scopes: ["usage_metrics"],
    });
    this.dx.catalog.updateStatus(dataset.id, "listed");
    return { datasetId: dataset.id };
  }
}

/** +SPCTR Spectre: anonymized CRM insights (opt-in accounts only) */
export class SpectreCollector extends ProductCollector {
  product: ProductId = "spectre";

  collect(cid: string): CollectorResult {
    const { dataset } = this.dx.packageDataset({
      cid,
      product: this.product,
      title: "Anonymized pipeline patterns",
      description: "Industry-level deal velocity and win rates (no company names)",
      schema: "industry,avgDealSize,winRate,cycleDays",
      sizeEstimate: 30_000,
      privacyLevel: "aggregated",
      licenseTerms: "CDAT-commercial-1y",
      priceUsd: 400,
      scopes: ["anonymized_insights"],
    });
    this.dx.catalog.updateStatus(dataset.id, "listed");
    return { datasetId: dataset.id };
  }
}

/** Factory for all product collectors */
export function createCollectors(dx: DataExchange): ProductCollector[] {
  return [
    new ScalusCollector(dx),
    new CumulusCollector(dx),
    new SpectreCollector(dx),
    // Add more: Conductor, VEIL, etc. as needed
  ];
}
