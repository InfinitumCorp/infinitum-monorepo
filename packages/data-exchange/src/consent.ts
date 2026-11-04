import type { ConsentRecord, ProductId } from "./types.js";

/**
 * Consent manager. In production this writes to Firmament ledger via @infinitum/firmament.
 * All grants must be explicit and revocable.
 */
export class ConsentManager {
  private consents = new Map<string, ConsentRecord>();

  grant(params: {
    cid: string;
    product: ProductId;
    datasetId: string;
    scopes: string[];
    purpose: string;
  }): ConsentRecord {
    const id = `consent_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const record: ConsentRecord = {
      cid: params.cid,
      product: params.product,
      datasetId: params.datasetId,
      granted: true,
      scopes: params.scopes,
      purpose: params.purpose,
      timestamp: new Date().toISOString(),
      // firmamentTx would be set after on-chain or ledger append
    };
    this.consents.set(id, record);
    return record;
  }

  revoke(consentId: string): boolean {
    const c = this.consents.get(consentId);
    if (!c) return false;
    c.granted = false;
    c.timestamp = new Date().toISOString();
    return true;
  }

  get(consentId: string): ConsentRecord | undefined {
    return this.consents.get(consentId);
  }

  listForCid(cid: string): ConsentRecord[] {
    return [...this.consents.values()].filter((c) => c.cid === cid);
  }
}
