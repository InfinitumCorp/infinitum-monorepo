import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { createServer } from "node:http";
import { join } from "node:path";
import { Ledger, generateSigner } from "@infinitum/firmament";
import { createCumulus } from "@infinitum/cumulus";
import { VirtualAuthenticator } from "../../../packages/cid/src/testing/virtual-authenticator.ts";
import { createMailApp } from "./app.ts";
import { scalvsCleanAgent } from "./clean-agent.ts";

const signer = generateSigner();
const ledger = new Ledger(undefined, { authorizedSigners: [signer.publicKey] });
let base = "";
let suite: ReturnType<typeof createCumulus>;
let server: ReturnType<ReturnType<typeof createMailApp>["server"]>;

class Browser {
  cookie = "";
  readonly auth = new VirtualAuthenticator();
  async call(method: string, path: string, body?: unknown, headers: Record<string, string> = { "x-cumulus": "1" }) {
    const res = await fetch(base + path, {
      method,
      headers: { "content-type": "application/json", cookie: this.cookie, ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: "manual",
    });
    const set = res.headers.get("set-cookie");
    if (set) this.cookie = set.split(";")[0]!;
    const text = await res.text();
    let data: any = text;
    try { data = JSON.parse(text); } catch { /* not JSON */ }
    return { status: res.status, data, headers: res.headers };
  }
  async signUp(username: string) {
    const origin = base;
    const a = await this.call("POST", "/api/auth/signup", { username });
    assert.equal(a.status, 200, JSON.stringify(a.data));
    const reg = this.auth.create({ challenge: a.data.options.challenge, origin, rpId: "localhost" });
    const b = await this.call("POST", "/api/auth/signup/finish", { cid: a.data.cid, response: reg });
    assert.equal(b.status, 200, JSON.stringify(b.data));
    const credentialId = b.data.options.allowCredentials[0].id;
    const c = await this.call("POST", "/api/auth/signin/finish", { response: this.auth.get({ credentialId, challenge: b.data.options.challenge, origin }) });
    assert.equal(c.status, 200, JSON.stringify(c.data));
    return a.data.cid as string;
  }
}

before(async () => {
  // Bind first so the passkey origin matches the port.
  const probe = createServer();
  await new Promise<void>((r) => probe.listen(0, "127.0.0.1", r));
  const port = (probe.address() as AddressInfo).port;
  await new Promise<void>((r) => probe.close(() => r()));
  base = `http://localhost:${port}`;
  suite = createCumulus({ ledger, signer, rpId: "localhost", origins: [base], mailOrigin: base });
  server = createMailApp({ suite, publicDir: join(import.meta.dirname, "..", "public"), origin: base, secureCookies: false, scalvs: async () => ({ ok: true, text: "Brief." }), cleanAgent: scalvsCleanAgent({ model: "m" }) }).server();
  await new Promise<void>((r) => server.listen(port, "127.0.0.1", r));
});
after(() => new Promise<void>((r) => server.close(() => r())));

describe("Cumulus://Mail over HTTP", () => {
  it("is unlisted: robots.txt disallows everything and pages are noindex", async () => {
    const b = new Browser();
    const robots = await b.call("GET", "/robots.txt");
    assert.match(robots.data, /Disallow: \//);
    const page = await b.call("GET", "/");
    assert.match(page.headers.get("x-robots-tag") ?? "", /noindex/);
    assert.match(page.data, /noindex/);
  });

  it("needs a Cumulus://ID session and the request header", async () => {
    const b = new Browser();
    assert.equal((await b.call("GET", "/api/state")).status, 401);
    await b.signUp("nora");
    assert.equal((await b.call("POST", "/api/send", { to: "x@cumulusmail.cloud", subject: "x", body: "x" }, {})).status, 403, "no CSRF header");
  });

  it("signs up, finds the welcome mail, mails another person and pays by mail", async () => {
    const alex = new Browser();
    const leah = new Browser();
    const alexCid = await alex.signUp("alex");
    const leahCid = await leah.signUp("leah");

    const state = await alex.call("GET", "/api/state");
    assert.equal(state.data.me.email, "alex@cumulusmail.cloud");
    assert.ok(state.data.messages.some((m: any) => m.subject === "Welcome to your Cumulus://ID"));
    assert.equal(state.data.account.plan, "Free");
    assert.equal(state.data.account.entitlements.firmWallet, true);

    await suite.credit(alexCid, "USDC", "50", "Cumulus Data Exchange");
    const sent = await alex.call("POST", "/api/send", { to: "leah@cumulusmail.cloud", subject: "Your share", body: "Here you go.", payment: { asset: "USDC", amount: "12.25" } });
    assert.equal(sent.status, 200, JSON.stringify(sent.data));
    assert.equal(sent.data.payment.amount, "12.25");

    const leahState = await leah.call("GET", "/api/state");
    const got = leahState.data.messages.find((m: any) => m.subject === "Your share");
    assert.equal(got.payment.amount, "12.25");
    assert.equal(suite.wallets.balances(leahCid).USDC, "12.25");

    const wallet = await leah.call("GET", "/api/wallet");
    assert.equal(wallet.data.balances.USDC, "12.25");
    assert.equal(wallet.data.history[0].counterparty, "alex@cumulusmail.cloud");

    const pay = await leah.call("POST", "/api/wallet/pay", { to: "alex@cumulusmail.cloud", asset: "USDC", amount: "100" });
    assert.equal(pay.status, 400);
    assert.match(pay.data.error, /Not enough USDC/);

    // Leah can't touch Alex's messages by id.
    const alexMsg = state.data.messages[0];
    await leah.call("POST", "/api/move", { id: alexMsg.id, folder: "trash" });
    const after = await alex.call("GET", "/api/state");
    assert.equal(after.data.messages.find((m: any) => m.id === alexMsg.id).folder, "inbox");
    assert.equal((await leah.call("GET", `/api/message?id=${alexMsg.id}`)).status, 404);
  });

  it("revoking Mail in Cumulus://ID permissions cuts access", async () => {
    const b = new Browser();
    const cidValue = await b.signUp("revoker");
    assert.equal((await b.call("GET", "/api/state")).status, 200);
    await suite.cid.revoke(cidValue, "cumulus-mail");
    assert.equal((await b.call("GET", "/api/state")).status, 403);
  });

  it("signing out ends the session", async () => {
    const b = new Browser();
    await b.signUp("leaver");
    assert.equal((await b.call("POST", "/api/auth/signout", {})).status, 200);
    assert.equal((await b.call("GET", "/api/state")).status, 401);
  });

  it("SCALVS Clean suggests, the owner approves, and it can be undone", async () => {
    const owner = new Browser();
    await owner.signUp("tidy");
    const bulk = new Browser();
    await bulk.signUp("digest");
    for (const n of [1, 2, 3]) await bulk.call("POST", "/api/send", { to: "tidy@cumulusmail.cloud", subject: `Weekly ${n}`, body: "News. Unsubscribe." });

    const review = await owner.call("POST", "/api/clean/scalvs", {});
    assert.equal(review.status, 200, JSON.stringify(review.data));
    const mute = review.data.clean.suggestions.find((s: any) => s.kind === "mute");
    assert.equal(mute?.email, "digest@cumulusmail.cloud");
    const approved = await owner.call("POST", "/api/clean/approve", { id: mute.id });
    assert.equal(approved.status, 200);
    assert.deepEqual(approved.data.clean.rules.muted, ["digest@cumulusmail.cloud"]);
    assert.equal(approved.data.state.messages.filter((m: any) => m.subject.startsWith("Weekly") && m.folder === "archive").length, 3);

    const undo = await owner.call("POST", "/api/clean/undo", { id: approved.data.clean.log[0].id });
    assert.equal(undo.data.restored, 3);
    const rules = await owner.call("POST", "/api/clean/rules", { auto: false, newslettersAfterDays: 3, emptyTrashAfterDays: null });
    assert.equal(rules.data.clean.rules.auto, false);
    assert.equal(rules.data.clean.rules.newslettersAfterDays, 3);
    assert.equal((await owner.call("POST", "/api/clean/mute", { email: "security@cumulusmail.cloud" })).status, 400);
  });
});

describe("SCALVS Clean agent", () => {
  it("turns SCALVS proposals into suggestions and drops ids it wasn't shown", async () => {
    const calls: any[] = [];
    const replies = [
      { stop_reason: "tool_use", content: [{ type: "tool_use", id: "t1", name: "list_inbox", input: {} }] },
      { stop_reason: "tool_use", content: [
        { type: "tool_use", id: "t2", name: "archive_messages", input: { ids: ["m1", "not-yours"], reason: "Old newsletter" } },
        { type: "tool_use", id: "t3", name: "trash_messages", input: { ids: ["starred"], reason: "Junk" } },
      ] },
      { stop_reason: "end_turn", content: [{ type: "text", text: "Mostly newsletters." }] },
    ];
    const fetchImpl = (async (_url: string, init: any) => {
      calls.push(JSON.parse(init.body));
      return new Response(JSON.stringify(replies.shift()), { status: 200 });
    }) as typeof fetch;
    const at = new Date("2026-11-01T00:00:00Z").toISOString();
    const base = { threadId: "x", folder: "inbox" as const, to: [], cc: [], body: "Unsubscribe", at, unread: false, labels: [] };
    const agent = scalvsCleanAgent({ apiKey: "test-key", model: "m", fetchImpl });
    const out = await agent({
      messages: [
        { ...base, id: "m1", from: { name: "Paper", email: "news@paper.example" }, subject: "Daily", starred: false },
        { ...base, id: "starred", from: { name: "Paper", email: "news@paper.example" }, subject: "Keep", starred: true },
      ],
      rules: { auto: true, newslettersAfterDays: 7, notificationsAfterDays: 14, bouncesAfterDays: 30, emptyTrashAfterDays: null, muted: [] },
      preview: [],
      now: new Date("2026-11-05T00:00:00Z"),
    });
    assert.deepEqual(out.suggestions.map((s) => [s.kind, s.ids]), [["archive", ["m1"]]]);
    assert.equal(out.note, "Mostly newsletters.");
    assert.match(calls[0].system, /untrusted/);
  });
});
