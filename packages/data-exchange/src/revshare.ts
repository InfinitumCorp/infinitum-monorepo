import type { LicenseSale } from "./types.ts";

/**
 * Revenue share calculator and payout tracker.
 * Default: 85% to data owner (user), 15% to platform.
 * Payouts can be in USDC, $CMLS, or product tokens via FIRM Wallet.
 */
export class RevShareEngine {
  private payouts: { saleId: string; cid: string; amountUsd: number; paid: boolean; paidAt?: string }[] = [];

  recordPayout(sale: LicenseSale, ownerCid: string): void {
    this.payouts.push({
      saleId: sale.id,
      cid: ownerCid,
      amountUsd: sale.userShareUsd,
      paid: false,
    });
  }

  markPaid(saleId: string): boolean {
    const p = this.payouts.find((x) => x.saleId === saleId);
    if (!p || p.paid) return false;
    p.paid = true;
    p.paidAt = new Date().toISOString();
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
}
