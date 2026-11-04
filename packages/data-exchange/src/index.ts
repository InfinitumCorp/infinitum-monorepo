export * from "./types.ts";
export * from "./consent.ts";
export * from "./catalog.ts";
export * from "./marketplace.ts";
export * from "./revshare.ts";
export * from "./collectors.ts";
export * from "./cdat.ts";

import { ConsentManager } from "./consent.ts";
import { DatasetCatalog } from "./catalog.ts";
import { DataMarketplace } from "./marketplace.ts";
import { RevShareEngine } from "./revshare.ts";
import { CdatIssuer } from "./cdat.ts";
import type { Ledger, Signer } from "@infinitum/firmament";
import type { ProductId } from "./types.ts";

/**
 * Main Data Exchange service.
 * Products call collectors that feed into catalog after consent.
 * A completed sale mints one soulbound CDAT license and, when a ledger is
 * attached, records the license and the 85/15 payment split on Firmament.
 */
export class DataExchange {
  consent: ConsentManager;
  catalog = new DatasetCatalog();
  marketplace: DataMarketplace;
  revshare = new RevShareEngine();
  cdat: CdatIssuer;

  constructor(ledger?: Ledger, signer?: Signer) {
    this.consent = new ConsentManager(ledger, signer);
    this.marketplace = new DataMarketplace(this.catalog);
    this.cdat = new CdatIssuer(ledger, signer);
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
    });

    const consent = await this.consent.grant({
      cid: params.cid,
      product: params.product,
      datasetId: ds.id,
      scopes: params.scopes,
      purpose: "data_licensing",
    });

    return { dataset: ds, consent };
  }

  listForSale() {
    return this.catalog.listListed();
  }

  async sell(datasetId: string, buyerId: string, amountUsd: number) {
    const sale = this.marketplace.purchase({ datasetId, buyerId, amountUsd });
    if (!sale) return null;

    const ds = this.catalog.get(datasetId);
    if (!ds) return sale;

    this.revshare.recordPayout(sale, ds.ownerCid);
    const token = await this.cdat.issue({
      datasetId,
      saleId: sale.id,
      licensee: buyerId,
      ownerCid: ds.ownerCid,
      terms: ds.licenseTerms,
      amountUsd,
      userShareUsd: sale.userShareUsd,
      platformShareUsd: sale.platformShareUsd,
    });
    sale.cdatTokenId = token.tokenId;
    return sale;
  }
}
