import type { Dataset, LicenseSale, DataBroker } from "./types.js";
import type { DatasetCatalog } from "./catalog.js";

/**
 * Marketplace for data brokers to discover and license datasets.
 * Settlement can be in USDC or product tokens; CDAT license tokens issued on Firmament.
 */
export class DataMarketplace {
  private sales = new Map<string, LicenseSale>();
  private brokers = new Map<string, DataBroker>();

  constructor(private catalog: DatasetCatalog) {}

  registerBroker(broker: Omit<DataBroker, "id">): DataBroker {
    const id = `broker_${Date.now()}`;
    const b: DataBroker = { ...broker, id };
    this.brokers.set(id, b);
    return b;
  }

  listBrokers(): DataBroker[] {
    return [...this.brokers.values()];
  }

  purchase(params: {
    datasetId: string;
    buyerId: string;
    amountUsd: number;
  }): LicenseSale | null {
    const ds = this.catalog.get(params.datasetId);
    if (!ds || ds.status !== "listed") return null;

    const userShare = (params.amountUsd * ds.revSharePct) / 100;
    const platformShare = params.amountUsd - userShare;

    const sale: LicenseSale = {
      id: `sale_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      datasetId: params.datasetId,
      buyerId: params.buyerId,
      amountUsd: params.amountUsd,
      userShareUsd: userShare,
      platformShareUsd: platformShare,
      soldAt: new Date().toISOString(),
      status: "completed",
    };

    this.sales.set(sale.id, sale);
    this.catalog.updateStatus(params.datasetId, "sold");
    return sale;
  }

  listSales(ownerCid?: string): LicenseSale[] {
    const all = [...this.sales.values()];
    if (!ownerCid) return all;
    return all.filter((s) => {
      const ds = this.catalog.get(s.datasetId);
      return ds?.ownerCid === ownerCid;
    });
  }

  getSale(id: string): LicenseSale | undefined {
    return this.sales.get(id);
  }
}
