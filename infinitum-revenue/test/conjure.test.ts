// @conjure on the customer API: org-key auth, a fake Conjure service, and the MIL exclusion.
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { migrate, pgliteDb } from "../src/db.js";
import { FakePayments } from "../src/payments.js";
import { createConjure, createConjureRoutes } from "../src/conjure/conjure.mjs";

const sent: any[] = [];
const fakeConjure = (async (url: string, init: RequestInit) => {
  const p = { id: "pr_9", product: "revenue", question: "will we hit plan?", agents: 8, rounds: 2, stats: [], createdAt: "2026-11-08T00:00:00Z", poles: { for: "we hit plan", against: "no" } };
  if (init.method === "POST") { sent.push(JSON.parse(String(init.body))); return new Response(JSON.stringify({ ...p, status: "queued", round: 0 }), { status: 202 }); }
  return new Response(JSON.stringify({ ...p, status: "done", round: 2, report: { outcome: "Close.", probability: 0.58, confidence: "low", forecast: "", drivers: [], signals: [], assumptions: [] } }));
}) as unknown as typeof fetch;

let app: ReturnType<typeof createApp>;
beforeEach(async () => {
  sent.length = 0;
  const db = await pgliteDb();
  await migrate(db);
  const ctx = { db, pay: new FakePayments(), config: { publicUrl: "https://infinitum.test", returnHosts: [], adminApiKey: "a".repeat(40), tokenPayoutsEnabled: false } };
  const conjure = createConjureRoutes({ conjure: createConjure({ url: "https://conjure.test", key: "cjp_revenue_x", fetch: fakeConjure }), secret: "cjp_revenue_x", prefix: "/v1/conjure", source: "revenue" });
  app = createApp(ctx, { conjure });
});

const call = async (method: string, path: string, key?: string, body?: unknown) => {
  const res = await app.request(path, { method, headers: { "content-type": "application/json", ...(key ? { authorization: `Bearer ${key}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: res.status, body: await res.json().catch(() => null) as any };
};
const signup = async (name: string, kind = "business") =>
  (await call("POST", "/v1/signup", undefined, { name, kind, email: `${name.toLowerCase()}@x.test` })).body as { orgId: string; apiKey: string };

describe("@conjure", () => {
  it("needs an org key", async () => {
    expect((await call("POST", "/v1/conjure", undefined, { text: "@conjure q?" })).status).toBe(401);
  });

  it("starts a forecast for the org and only that org can follow it", async () => {
    const a = await signup("Acme");
    const b = await signup("Beta");
    expect((await call("POST", "/v1/conjure", a.apiKey, { text: "nothing to see" })).body).toEqual({ mention: false });
    const r = await call("POST", "/v1/conjure", a.apiKey, { text: "@conjure will we hit plan?" });
    expect(r.status).toBe(200);
    expect(sent[0]).toMatchObject({ question: "will we hit plan?", source: "revenue", user: a.orgId });
    const live = await call("GET", `/v1/conjure/${r.body.ticket}`, a.apiKey);
    expect(live.body.text).toMatch(/58% likely that we hit plan/);
    expect((await call("GET", `/v1/conjure/${r.body.ticket}`, b.apiKey)).status).toBe(404);
  });

  it("is not available to military organisations", async () => {
    const m = await signup("Unit", "military");
    expect((await call("POST", "/v1/conjure", m.apiKey, { text: "@conjure q?" })).status).toBe(403);
    expect(sent.length).toBe(0);
  });
});
