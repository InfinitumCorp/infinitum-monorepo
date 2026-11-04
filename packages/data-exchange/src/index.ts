export * from "./types.js";
export * from "./consent.js";
export * from "./catalog.js";
export * from "./marketplace.js";
export * from "./revshare.js";
export * from "./collectors.js";

import { ConsentManager } from "./consent.js";
import { DatasetCatalog } from "./catalog.js";
import { DataMarketplace } from "./marketplace.js";
import { RevShareEngine } from "./revshare.js";
import type { Ledger, Signer } from "@infinitum/firmament";
import type { ProductId } from "./types.js";

/**
 * Main Data Exchange service.
 * Products call collectors that feed into catalog after consent.
 * Optionally backed by Firmament ledger for audit of grants, licenses, and payments.
 */
export class DataExchange {
  consent: ConsentManager;
  catalog = new DatasetCatalog();
  marketplace: DataMarketplace;
  revshare = new RevShareEngine();

  constructor(ledger?: Ledger, signer?: Signer) {
    this.consent = new ConsentManager(ledger, signer);
    this.marketplace = new DataMarketplace(this.catalog);
  }

  /**
   * Package a dataset from a product after user consent.
   * Records the grant on Firmament when ledger is configured.
   */
  async packageDataset(params: {
    cid: string;
    product: ProductId;
    title: string;
    description: string;
    schema: string;
    sizeEstimate: number;
    privacyLevel: "anonymized" | "pseudonymized" | "aggregated";
    licenseTerms: string;
    priceUsd: number;
    scopes: string[];
  }) {
    // Provisional id so consent can reference the future dataset
    const provisionalId = `ds_pending_${Date.now()}`;

    const consent = await this.consent.grant({
      cid: params.cid,
      product: params.product,
      datasetId: provisionalId,
      scopes: params.scopes,
      purpose: "data_licensing",
    });

    const ds = this.catalog.create({
      ownerCid: params.cid,
      product: params.product,
      title: params.title,
      description: params.description,
      schema: params.schema,
      sizeEstimate: params.sizeEstimate,
      privacyLevel: params.privacyLevel,
      licenseTerms: params.licenseTerms,
      priceUsd: params.priceUsd,
      consentId: consent.datasetId,
    });

    // Align the consent subject with the real dataset id
    consent.datasetId = ds.id;

    return { dataset: ds, consent };
  }

  listForSale() {
    return this.catalog.listListed();
  }

  async sell(datasetId: string, buyerId: string, amountUsd: number) {
    const sale = this.marketplace.purchase({ datasetId, buyerId, amountUsd });
    if (sale) {
      const ds = this.catalog.get(datasetId);
      if (ds) {
        this.revshare.recordPayout(sale, ds.ownerCid);
        // License + payment records can be appended here when ledger is present
      }
    }
    return sale;
  }
}
