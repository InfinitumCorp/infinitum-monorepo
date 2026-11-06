import { test, before } from "node:test";
import assert from "node:assert/strict";
import { fakeChrome } from "./fake-chrome.js";

const env = fakeChrome();
globalThis.chrome = env.chrome;
const fromSidebar = (msg) => env.send(msg, { id: "veilid", url: "chrome-extension://veilid/sidebar.html" });
const fromPage = (msg, tab = { id: 1, windowId: 1, active: true }) =>
  env.send(msg, { id: "veilid", url: "https://example.com/", tab });
const human = { type: "activity", trusted: true, visible: true, focused: true };

before(async () => {
  env.store.totalVeil = "3.5"; // v0.3 leftovers
  env.store.firmBalance = "42.0";
  await import("../background.js");
  await env.listeners.installed[0]();
});

test("installs into the anonymous workspace and drops v0.3 fake data", async () => {
  const s = await fromSidebar({ type: "getState" });
  assert.equal(s.workspace, "anonymous");
  assert.equal(s.firmAddress, null);
  assert.equal(env.store.totalVeil, undefined);
  assert.equal(env.store.firmBalance, undefined);
});

test("an update keeps an existing ledger", async () => {
  env.store.rewardsLedger.lifetimeMicro = 777;
  await env.listeners.installed[0]();
  assert.equal(env.store.rewardsLedger.lifetimeMicro, 777);
  env.store.rewardsLedger.lifetimeMicro = 0;
});

test("anonymous browsing earns nothing", async () => {
  const r = await fromPage(human);
  assert.deepEqual(r, { credited: false, reason: "workspace" });
});

test("pages cannot switch workspace, toggle Tor or claim", async () => {
  for (const type of ["switchWorkspace", "toggleTor", "claimRewards", "newIdentity", "getState"]) {
    const r = await fromPage({ type, to: "authenticated", confirmed: true, enabled: true });
    assert.match(r.error, /not allowed/, type);
  }
  assert.equal(env.store.workspace, "anonymous");
});

test("a switch needs confirmation, then clears site data", async () => {
  const plan = await fromSidebar({ type: "switchWorkspace", to: "authenticated" });
  assert.equal(plan.changed, false);
  assert.ok(plan.plan.warning);
  assert.equal(env.calls.browsingData.length, 0);

  const done = await fromSidebar({ type: "switchWorkspace", to: "authenticated", confirmed: true });
  assert.equal(done.workspace, "authenticated");
  assert.equal(env.calls.browsingData.length, 1);
  assert.equal(env.calls.browsingData[0].cookies, true);
  assert.equal(env.calls.browsingData[0].history, undefined);
});

test("signed-in activity on the active tab earns; background tabs do not", async () => {
  const bg = await fromPage(human, { id: 2, windowId: 1, active: false });
  assert.equal(bg.reason, "inactive-tab");
  const r = await fromPage(human);
  assert.equal(r.credited, true);
  const { summary } = await fromSidebar({ type: "getRewards" });
  assert.equal(summary.pendingMicro, 100_000);
});

test("claiming needs a Cumulus://ID", async () => {
  const r = await fromSidebar({ type: "claimRewards" });
  assert.match(r.error, /Cumulus:\/\/ID/);
  env.store.cidUser = { cid: "cid_1", username: "alex" };
  const ok = await fromSidebar({ type: "claimRewards" });
  assert.equal(ok.receipt.amount, "0.100000");
  assert.equal(ok.receipt.user, "cid_1");
});

test("Tor on sets SOCKS5 and limits WebRTC; off clears both", async () => {
  await fromSidebar({ type: "toggleTor", enabled: true });
  const rules = env.calls.proxySet.at(-1).value.rules.singleProxy;
  assert.deepEqual(rules, { scheme: "socks5", host: "127.0.0.1", port: 9050 });
  assert.equal(env.calls.webrtc.at(-1), "disable_non_proxied_udp");
  await fromSidebar({ type: "toggleTor", enabled: false });
  assert.equal(env.calls.proxyClear, 1);
  assert.equal(env.calls.webrtc.at(-1), "cleared");
});

test("leaving the signed-in workspace signs out and hides rewards", async () => {
  await fromSidebar({ type: "switchWorkspace", to: "anonymous", confirmed: true });
  assert.equal(env.store.cidUser, null);
  const r = await fromSidebar({ type: "claimRewards" });
  assert.match(r.error, /anonymous/);
});

test("New Identity clears site data and closes every old tab", async () => {
  const before = env.calls.browsingData.length;
  await fromSidebar({ type: "newIdentity" });
  assert.equal(env.calls.browsingData.length, before + 1);
  assert.deepEqual(env.calls.tabsRemoved.sort(), [1, 2]);
});

test("unknown messages are refused", async () => {
  const r = await fromSidebar({ type: "exportKeys" });
  assert.match(r.error, /unknown/);
});

test("the sidebar opened in a tab still counts as VEIL, and cannot fake activity", async () => {
  const sender = { id: "veilid", url: "chrome-extension://veilid/sidebar.html", tab: { id: 5, windowId: 1, active: true } };
  const s = await env.send({ type: "getState" }, sender);
  assert.equal(s.error, undefined);
  const a = await env.send(human, sender);
  assert.match(a.error, /not allowed/);
});

test("messages from another extension are refused", async () => {
  const r = await env.send({ type: "getState" }, { id: "other", url: "chrome-extension://other/x.html" });
  assert.match(r.error, /not allowed/);
});
