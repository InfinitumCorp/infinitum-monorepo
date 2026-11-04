export * from "./types.js";
export * from "./consent.js";
export * from "./catalog.js";
export * from "./marketplace.js";
export * from "./revshare.js";

import { ConsentManager } from "./consent.js";
import { DatasetCatalog } from "./catalog.js";
import { DataMarketplace } from "./marketplace.js";
import { RevShareEngine } from "./revshare.js";

/**
 * Main Data Exchange service.
 * Products call collectors that feed into catalog after consent.
 */
export class DataExchange {
  consent = new ConsentManager();
  catalog = new DatasetCatalog();
  marketplace: DataMarketplace;
  revshare = new RevShareEngine();

  constructor() {
    this.marketplace = new DataMarketplace(this.catalog);
  }

  /**
   * Package a dataset from a product after user consent.
   */
  packageDataset(params: {
    cid: string;
    product: import("./types.js").ProductId;
    title: string;
    description: string;
    schema: string;
    sizeEstimate: number;
    privacyLevel: "anonymized" | "pseudonymized" | "aggregated";
    licenseTerms: string;
    priceUsd: number;
    scopes: string[];
  }) {
    const consent = this.consent.grant({
      cid: params.cid,
      product: params.product,
      datasetId: "pending",
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
      consentId: consent.datasetId === "pending" ? undefined : consent.datasetId,
    });

    // Fix consent datasetId
    consent.datasetId = ds.id;

    return { dataset: ds, consent };
  }

  listForSale() {
    return this.catalog.listListed();
  }

  sell(datasetId: string, buyerId: string, amountUsd: number) {
    const sale = this.marketplace.purchase({ datasetId, buyerId, amountUsd });
    if (sale) {
      const ds = this.catalog.get(datasetId);
      if (ds) this.revshare.recordPayout(sale, ds.ownerCid);
    }
    return sale;
  }
}
