/** Single entry point for payment-provider events. Every event is processed at most once. */
import type { Ctx } from "../context.js";
import type { PaymentEvent } from "../payments.js";
import { applyCreditTopup, upsertSubscription } from "./billing.js";
import { markOrderBalancePaid, markOrderCheckoutPaid } from "./commerce.js";
import { activateLicence, fundMission } from "./marketplace.js";
import { applyReferralCommission, markMilestonePaid } from "./contracts.js";
import { applyRefund, recordPayment } from "./refunds.js";

export async function handlePaymentEvent(ctx: Ctx, e: PaymentEvent) {
  return ctx.db.tx(async (db) => {
    const [fresh] = await db.query("insert into webhook_events (id, type) values ($1,$2) on conflict do nothing returning id", [e.eventId, e.kind]);
    if (!fresh) return { duplicate: true };

    switch (e.kind) {
      case "subscription.updated":
        await upsertSubscription(db, e);
        break;
      case "checkout.completed":
        await recordPayment(db, e);
        if (e.purpose === "credits") await applyCreditTopup(db, e.orgId, e.ref, e.sessionId);
        else if (e.purpose === "order") await markOrderCheckoutPaid(db, e.ref);
        else if (e.purpose === "licence") await activateLicence(db, e.ref);
        else if (e.purpose === "mission") await fundMission(db, e.ref);
        break;
      case "invoice.paid": {
        if (e.metadata.milestone_id) await markMilestonePaid(db, e.metadata.milestone_id);
        if (e.metadata.order_id) await markOrderBalancePaid(db, e.metadata.order_id);
        const [org] = await db.query("select id from orgs where provider_customer_id=$1", [e.customerId]);
        if (org && e.amountCents > 0) await applyReferralCommission(db, org.id, e.invoiceId, e.amountCents);
        break;
      }
      case "invoice.failed":
        if (e.providerSubscriptionId)
          await db.query("update subscriptions set status='past_due', updated_at=now() where provider_subscription_id=$1", [e.providerSubscriptionId]);
        break;
      case "charge.refunded":
        return { duplicate: false, refund: await applyRefund(db, { paymentIntentId: e.paymentIntentId, cumulativeCents: e.amountCents, eventId: e.eventId }) };
      case "dispute.lost": {
        const [p] = await db.query("select refunded_cents from payments where payment_intent_id=$1", [e.paymentIntentId]);
        const cumulative = Number(p?.refunded_cents ?? 0) + e.amountCents;
        return { duplicate: false, refund: await applyRefund(db, { paymentIntentId: e.paymentIntentId, cumulativeCents: cumulative, eventId: e.eventId }) };
      }
    }
    return { duplicate: false };
  });
}
