/** Hardware orders (pay in full or deposit + balance invoice) and leasing. */
import { newId, type Db } from "../db.js";
import { type Ctx, fail, audit } from "../context.js";
import { getSku } from "../catalog.js";
import { ensureCustomerId, type Org } from "./billing.js";

export async function createOrder(ctx: Ctx, org: Org, a: { items: { sku: string; quantity: number }[]; shipping?: unknown }) {
  if (!a.items?.length) fail(400, "empty_order", "Order has no items");
  let total = 0, dueNow = 0;
  const lines = a.items.map(({ sku, quantity }) => {
    const s = getSku(sku) ?? fail(404, "sku_not_found", `Unknown SKU ${sku}`);
    if (!Number.isInteger(quantity) || quantity < 1) fail(400, "bad_quantity", "quantity must be a positive integer");
    if (s.channel === "contract") fail(409, "contact_sales", `${s.name} is quoted and contracted, not sold online.`);
    if (s.channel === "sales_assisted" && !org.approved_products.includes(s.product))
      fail(403, "approval_required", `${s.name} requires account approval before ordering.`);
    total += s.priceCents * quantity;
    dueNow += Math.round(s.priceCents * quantity * s.depositRatio);
    return { sku: s.id, name: s.name, quantity, unitCents: s.priceCents, depositRatio: s.depositRatio };
  });
  const id = newId("ord");
  const isDeposit = dueNow < total;
  const session = await ctx.pay.paymentCheckout({
    customerId: await ensureCustomerId(ctx, org), orgId: org.id, purpose: "order", ref: id,
    items: isDeposit
      ? [{ name: `Reservation deposit: ${lines.map((l) => `${l.quantity}× ${l.name}`).join(", ")}`, amountCents: dueNow, quantity: 1 }]
      : lines.map((l) => ({ name: l.name, amountCents: l.unitCents, quantity: l.quantity })),
    successUrl: `${ctx.config.publicUrl}/orders/${id}?paid=1`, cancelUrl: `${ctx.config.publicUrl}/orders/${id}?cancelled=1`,
  });
  await ctx.db.query(
    `insert into orders (id, org_id, status, items, total_cents, due_now_cents, balance_cents, provider_session_id, shipping)
     values ($1,$2,'pending_payment',$3,$4,$5,$6,$7,$8)`,
    [id, org.id, JSON.stringify(lines), total, dueNow, total - dueNow, session.id, JSON.stringify(a.shipping ?? null)]);
  return { orderId: id, totalCents: total, dueNowCents: dueNow, balanceCents: total - dueNow, checkoutUrl: session.url };
}

export async function markOrderCheckoutPaid(db: Db, orderId: string) {
  await db.query(`update orders set status = case when balance_cents > 0 then 'deposit_paid' else 'paid' end
    where id=$1 and status='pending_payment'`, [orderId]);
}

/** Operator action when units are ready to ship: invoice the remaining balance. */
export async function invoiceOrderBalance(ctx: Ctx, orderId: string) {
  const [o] = await ctx.db.query("select * from orders where id=$1", [orderId]);
  if (!o) fail(404, "order_not_found", "Order not found");
  if (o.status !== "deposit_paid") fail(409, "bad_state", `Order is ${o.status}; only deposit_paid orders have a balance to invoice`);
  const [org] = await ctx.db.query<Org>("select * from orgs where id=$1", [o.org_id]);
  const inv = await ctx.pay.sendInvoice({
    customerId: await ensureCustomerId(ctx, org), description: `Balance for order ${o.id}`,
    amountCents: Number(o.balance_cents), dueDays: 14, metadata: { order_id: o.id, org_id: o.org_id },
  });
  await audit(ctx.db, o.org_id, "admin", "order.balance_invoiced", { orderId, invoiceId: inv.id });
  return inv;
}

export async function markOrderBalancePaid(db: Db, orderId: string) {
  await db.query("update orders set status='paid', balance_cents=0 where id=$1 and status='deposit_paid'", [orderId]);
}

export async function markOrderFulfilled(ctx: Ctx, orderId: string) {
  const [o] = await ctx.db.query("update orders set status='fulfilled' where id=$1 and status='paid' returning *", [orderId]);
  if (!o) fail(409, "bad_state", "Only fully paid orders can be fulfilled");
  return o;
}

export const listOrders = (db: Db, orgId: string) =>
  db.query("select * from orders where org_id=$1 order by created_at desc", [orgId]);
