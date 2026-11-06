import type { LicenseSale } from "./types.ts";

/** One owner's share of a sale. 85% by default, paid in USDC, $CMLS, or a product token. */
export interface Payout {
  saleId: string;
  cid: string;
  amountUsd: number;
  paid: boolean;
  paidAt?: string;
}

/**
 * Revenue share calculator and payout tracker.
 * Default: 85% to data owner (user), 15% to platform.
 */
export class RevShareEngine {
  private payouts: Payout[] = [];
  changed?: () => void;

  recordPayout(sale: LicenseSale, ownerCid: string): void {
    this.payouts.push({
      saleId: sale.id,
      cid: ownerCid,
      amountUsd: sale.userShareUsd,
      paid: false,
    });
    this.changed?.();
  }

  drop(saleId: string): void {
    const next = this.payouts.filter((p) => p.saleId !== saleId);
    if (next.length === this.payouts.length) return;
    this.payouts = next;
    this.changed?.();
  }

  markPaid(saleId: string): boolean {
    const p = this.payouts.find((x) => x.saleId === saleId);
    if (!p || p.paid) return false;
    p.paid = true;
    p.paidAt = new Date().toISOString();
    this.changed?.();
    return true;
  }

  pendingForCid(cid: string): number {
    return this.payouts
      .filter((p) => p.cid === cid && !p.paid)
      .reduce((sum, p) => sum + p.amountUsd, 0);
  }

  listPayouts(cid?: string) {
    if (!cid) return this.payouts;
    return this.payouts.filter((p) => p.cid === cid);
  }

  get(saleId: string): Payout | undefined {
    return this.payouts.find((p) => p.saleId === saleId);
  }

  exportState(): Payout[] {
    return this.payouts.map((p) => ({ ...p }));
  }

  importState(records: Payout[]) {
    this.payouts = records.map((p) => ({ ...p }));
  }
}
