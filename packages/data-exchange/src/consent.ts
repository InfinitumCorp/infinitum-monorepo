import type { ConsentRecord, ProductId } from "./types.js";
import type { Ledger, Signer } from "@infinitum/firmament";

/**
 * Consent manager. Records explicit grants/revokes on Firmament when a ledger is provided.
 * All grants must be explicit and revocable. Military/ODIN° data is excluded by policy.
 */
export class ConsentManager {
  private consents = new Map<string, ConsentRecord>();

  constructor(
    private ledger?: Ledger,
    private signer?: Signer,
  ) {}

  async grant(params: {
    cid: string;
    product: ProductId;
    datasetId: string;
    scopes: string[];
    purpose: string;
  }): Promise<ConsentRecord> {
    const id = `consent_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const record: ConsentRecord = {
      cid: params.cid,
      product: params.product,
      datasetId: params.datasetId,
      granted: true,
      scopes: params.scopes,
      purpose: params.purpose,
      timestamp: new Date().toISOString(),
    };

    if (this.ledger && this.signer) {
      const entry = await this.ledger.append(
        {
          type: "grant",
          product: "data-exchange",
          actor: params.cid,
          subject: params.datasetId,
          payload: {
            consentId: id,
            product: params.product,
            scopes: params.scopes,
            purpose: params.purpose,
            granted: true,
          },
        },
        this.signer,
      );
      record.firmamentTx = entry.hash;
    }

    this.consents.set(id, record);
    return record;
  }

  async revoke(consentId: string): Promise<boolean> {
    const c = this.consents.get(consentId);
    if (!c) return false;
    c.granted = false;
    c.timestamp = new Date().toISOString();

    if (this.ledger && this.signer) {
      const entry = await this.ledger.append(
        {
          type: "revoke",
          product: "data-exchange",
          actor: c.cid,
          subject: c.datasetId,
          payload: {
            consentId,
            revoked: true,
          },
        },
        this.signer,
      );
      c.firmamentTx = entry.hash;
    }

    return true;
  }

  get(consentId: string): ConsentRecord | undefined {
    return this.consents.get(consentId);
  }

  listForCid(cid: string): ConsentRecord[] {
    return [...this.consents.values()].filter((c) => c.cid === cid);
  }
}
