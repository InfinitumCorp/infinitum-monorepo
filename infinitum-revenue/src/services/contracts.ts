/**
 * Contracted revenue: consulting and advisory engagements billed by milestone (including
 * ODIN° and military editions, which are never self-serve), signed on-prem / device licences,
 * and partner referral commissions.
 */
import { createPrivateKey, createPublicKey, generateKeyPairSync, sign, verify, type KeyObject } from "node:crypto";
import { newId, type Db } from "../db.js";
import { type Ctx, fail, audit } from "../context.js";
import { ensureCustomerId, getOrg, type Org } from "./billing.js";
import { creditEarnings } from "./marketplace.js";

export const PRACTICES = [
  "it_consulting", "security_consulting", "computer_engineering", "project_management",
  "narrative_intelligence", "communication_strategy", "crisis_preparedness",
  "corporate_cognitive_warfare", "reflexive_capital_warfare", "irregular_warfare",
  "managed_services", "implementation", "training",
  "odin", "military_edition",
] as const;
const DEFENSE_ONLY = new Set(["odin", "military_edition"]);
const CONFIDENTIAL_BY_DEFAULT = new Set(["corporate_cognitive_warfare", "reflexive_capital_warfare", "irregular_warfare", "odin", "military_edition"]);

// ------------------------------------------------------------- engagements

export async function createEngagement(ctx: Ctx, a: {
  orgId: string; practice: string; title: string; confidential?: boolean;
  milestones: { title: string; amountCents: number; dueDays?: number }[];
}) {
  if (!PRACTICES.includes(a.practice as any)) fail(400, "bad_practice", `practice must be one of ${PRACTICES.join(", ")}`);
  const org = await getOrg(ctx.db, a.orgId);
  if (DEFENSE_ONLY.has(a.practice) && !(org.kind === "military" && org.verified_defense))
    fail(403, "defense_only", "ODIN° and military editions are only for verified defense organisations");
  if (!a.milestones?.length) fail(400, "no_milestones", "At least one milestone is required");
  return ctx.db.tx(async (db) => {
    const id = newId("eng");
    const confidential = a.confidential ?? CONFIDENTIAL_BY_DEFAULT.has(a.practice);
    await db.query("insert into engagements (id, org_id, practice, title, confidential, status) values ($1,$2,$3,$4,$5,'active')",
      [id, org.id, a.practice, a.title, confidential]);
    for (const m of a.milestones) {
      if (!Number.isInteger(m.amountCents) || m.amountCents <= 0) fail(400, "bad_amount", "Milestone amountCents must be a positive integer");
      await db.query("insert into milestones (id, engagement_id, title, amount_cents, due_days) values ($1,$2,$3,$4,$5)",
        [newId("ms"), id, m.title, m.amountCents, m.dueDays ?? 30]);
    }
    await audit(db, org.id, "admin", "engagement.created", { id, practice: a.practice });
    return getEngagement(db, id);
  });
}

export async function getEngagement(db: Db, id: string) {
  const [e] = await db.query("select * from engagements where id=$1", [id]);
  if (!e) fail(404, "engagement_not_found", "Engagement not found");
  e.milestones = await db.query("select * from milestones where engagement_id=$1 order by title", [id]);
  return e;
}

export const listEngagements = async (db: Db, orgId: string) => {
  const rows = await db.query("select id from engagements where org_id=$1 order by created_at desc", [orgId]);
  return Promise.all(rows.map((r) => getEngagement(db, r.id)));
};

export async function invoiceMilestone(ctx: Ctx, milestoneId: string) {
  const [m] = await ctx.db.query(`select m.*, e.org_id, e.title as engagement_title, e.confidential from milestones m
    join engagements e on e.id=m.engagement_id where m.id=$1`, [milestoneId]);
  if (!m) fail(404, "milestone_not_found", "Milestone not found");
  if (m.status !== "pending") fail(409, "bad_state", `Milestone is ${m.status}`);
  const org = await getOrg(ctx.db, m.org_id);
  const description = m.confidential ? `Advisory services — ref ${m.engagement_id}` : `${m.engagement_title}: ${m.title}`;
  const inv = await ctx.pay.sendInvoice({ customerId: await ensureCustomerId(ctx, org), description,
    amountCents: Number(m.amount_cents), dueDays: m.due_days, metadata: { milestone_id: m.id, org_id: org.id } });
  await ctx.db.query("update milestones set status='invoiced', provider_invoice_id=$2, invoice_url=$3 where id=$1", [m.id, inv.id, inv.url]);
  return inv;
}

export const markMilestonePaid = (db: Db, milestoneId: string) =>
  db.query("update milestones set status='paid', paid_at=now() where id=$1 and status='invoiced'", [milestoneId]);

// ------------------------------------------------------------- signed licences

let devKey: KeyObject | null = null;
function signingKey(ctx: Ctx): KeyObject {
  if (ctx.config.licenceSigningKeyPem) return createPrivateKey(ctx.config.licenceSigningKeyPem);
  if (!devKey) {
    devKey = generateKeyPairSync("ed25519").privateKey;
    console.warn("LICENCE_SIGNING_KEY not set: using an ephemeral key. Licences will not verify after restart.");
  }
  return devKey;
}

export const licencePublicKeyPem = (ctx: Ctx) =>
  createPublicKey(signingKey(ctx)).export({ type: "spki", format: "pem" }).toString();

export async function issueLicence(ctx: Ctx, a: { orgId: string; product: string; edition: string; seats: number; days: number; deviceFingerprint?: string }) {
  await getOrg(ctx.db, a.orgId);
  const id = newId("lk");
  const expiresAt = new Date(Date.now() + a.days * 86400_000);
  const payload = { lid: id, org: a.orgId, product: a.product, edition: a.edition, seats: a.seats,
    dev: a.deviceFingerprint ?? null, exp: Math.floor(expiresAt.getTime() / 1000) };
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const sig = sign(null, Buffer.from(body), signingKey(ctx)).toString("base64url");
  const token = `${body}.${sig}`;
  await ctx.db.query(`insert into licence_keys (id, org_id, product, edition, seats, device_fingerprint, expires_at, token)
    values ($1,$2,$3,$4,$5,$6,$7,$8)`, [id, a.orgId, a.product, a.edition, a.seats, a.deviceFingerprint ?? null, expiresAt, token]);
  return { licenceId: id, token, expiresAt };
}

/** Offline-capable check: signature + expiry. Online callers also get revocation status. */
export async function verifyLicence(ctx: Ctx, token: string) {
  const [body, sig] = token.split(".");
  if (!body || !sig) return { valid: false, reason: "malformed" };
  const ok = verify(null, Buffer.from(body), createPublicKey(signingKey(ctx)), Buffer.from(sig, "base64url"));
  if (!ok) return { valid: false, reason: "bad_signature" };
  const payload = JSON.parse(Buffer.from(body, "base64url").toString());
  if (payload.exp * 1000 < Date.now()) return { valid: false, reason: "expired", payload };
  const [row] = await ctx.db.query("select revoked_at from licence_keys where id=$1", [payload.lid]);
  if (row?.revoked_at) return { valid: false, reason: "revoked", payload };
  return { valid: true, payload };
}

export const revokeLicence = (db: Db, id: string) =>
  db.query("update licence_keys set revoked_at=now() where id=$1 and revoked_at is null returning id", [id]);

// ------------------------------------------------------------- referrals

export async function createReferralCode(db: Db, org: Org, code: string) {
  if (!/^[A-Z0-9_-]{4,32}$/i.test(code)) fail(400, "bad_code", "Code must be 4–32 letters, digits, - or _");
  const [r] = await db.query(`insert into referral_codes (code, owner_org_id) values ($1,$2) on conflict do nothing returning *`, [code.toUpperCase(), org.id]);
  return r ?? fail(409, "code_taken", "Code already exists");
}

/** On each paid invoice from a referred org (within the commission window), credit the referrer. */
export async function applyReferralCommission(db: Db, orgId: string, invoiceId: string, amountCents: number) {
  const [a] = await db.query(`select r.owner_org_id, r.commission_bps from referral_attributions a
    join referral_codes r on r.code=a.code
    where a.org_id=$1 and a.created_at > now() - make_interval(months => r.months)`, [orgId]);
  if (!a || a.owner_org_id === orgId) return;
  const commission = Math.floor((amountCents * a.commission_bps) / 10000);
  await creditEarnings(db, a.owner_org_id, commission, "referral", invoiceId, 30);
}
