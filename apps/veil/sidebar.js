/**
 * VEIL sidebar. Renders state from the background worker and sends requests.
 * It holds no state of its own beyond what is on screen.
 */
import { ANONYMOUS, AUTHENTICATED, LABELS, can } from "./lib/workspace.js";
import { formatVeil, POLICY } from "./lib/rewards.js";

const $ = (id) => document.getElementById(id);
const statusEl = $("status");
const say = (text) => { statusEl.textContent = text; };

async function send(type, extra = {}) {
  const r = await chrome.runtime.sendMessage({ type, ...extra });
  if (r?.error) throw new Error(r.error);
  return r;
}

let state = null;

// ---------- Navigation & workspace gating ----------

function showPanel(id) {
  document.querySelectorAll("nav button").forEach((b) => b.classList.toggle("active", b.dataset.panel === id));
  document.querySelectorAll(".panel").forEach((p) => p.classList.toggle("active", p.id === id));
  if (id === "rewards") loadRewards();
}
document.querySelectorAll("nav button").forEach((b) => { b.onclick = () => showPanel(b.dataset.panel); });

function applyWorkspace() {
  const ws = state.workspace;
  const chip = $("ws-chip");
  chip.textContent = ws === ANONYMOUS ? "Anonymous" : "Signed in · not anonymous";
  chip.className = `chip ${ws === ANONYMOUS ? "anon" : "auth"}`;

  let activeHidden = false;
  document.querySelectorAll("nav button[data-cap]").forEach((b) => {
    b.hidden = !can(ws, b.dataset.cap);
    if (b.hidden && b.classList.contains("active")) activeHidden = true;
  });
  if (activeHidden) showPanel("privacy");

  $("ws-desc").textContent = ws === ANONYMOUS
    ? `${LABELS[ANONYMOUS]}: no account, AI, wallet, sync or rewards. Nothing links your browsing to you.`
    : `${LABELS[AUTHENTICATED]}: Cumulus://ID, AI, FIRM Wallet and $VEIL rewards. Signed-in browsing is never anonymous.`;
  $("ws-switch").textContent = ws === ANONYMOUS ? "Switch to signed-in workspace" : "Switch to anonymous workspace";
  $("signin-card").hidden = !can(ws, "signin");
  $("cid-who").textContent = state.cidUser
    ? `Signed in as ${state.cidUser.username ?? state.cidUser.cid}${state.cidUser.mailAddress ? ` · ${state.cidUser.mailAddress}` : ""}`
    : "Not signed in";
  if (can(ws, "signin")) loadProviders();
}

// ---------- Workspace switch (two-step, in-panel confirmation) ----------

let pendingSwitch = null;
$("ws-switch").onclick = async () => {
  const to = state.workspace === ANONYMOUS ? AUTHENTICATED : ANONYMOUS;
  const r = await send("switchWorkspace", { to, confirmed: false });
  if (!r.plan) return;
  pendingSwitch = to;
  $("confirm-text").textContent = r.plan.warning;
  $("confirm").hidden = false;
};
$("confirm-no").onclick = () => { pendingSwitch = null; $("confirm").hidden = true; };
$("confirm-yes").onclick = async () => {
  if (!pendingSwitch) return;
  try {
    say("Clearing site data…");
    await send("switchWorkspace", { to: pendingSwitch, confirmed: true });
    say(`Now in the ${LABELS[pendingSwitch].toLowerCase()}`);
  } catch (err) {
    say(`Switch failed: ${err.message}`);
  }
  pendingSwitch = null;
  $("confirm").hidden = true;
  await refresh();
};

// ---------- Privacy ----------

function renderTor() {
  $("tor-dot").classList.toggle("on", state.torEnabled);
  $("tor-status").textContent = state.torEnabled ? "Tor proxy on (socks5://127.0.0.1:9050)" : "Tor proxy off";
  $("tor-toggle").textContent = state.torEnabled ? "Turn off Tor" : "Turn on Tor";
}
$("tor-toggle").onclick = async () => {
  try {
    await send("toggleTor", { enabled: !state.torEnabled });
    await refresh();
    say(state.torEnabled ? "Tor on. Use Check connection to confirm." : "Tor off");
  } catch (err) { say(`Tor: ${err.message}`); }
};
$("check-tor").onclick = async () => {
  say("Checking with check.torproject.org…");
  const r = await send("checkTor");
  say(r.isTor ? "Confirmed: traffic is leaving through Tor" : `Not on Tor${r.error ? ` (${r.error})` : ""}`);
};
$("auto-clean").onchange = (e) => send("setAutoClean", { enabled: e.target.checked });
$("clean-current").onclick = async () => {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) return;
  try {
    const r = await chrome.tabs.sendMessage(tab.id, { type: "cleanUrl" });
    say(r?.cleaned ? "Tracking parameters removed" : "No tracking parameters found");
  } catch { say("This page can't be cleaned"); }
};
let identityArmed = false;
$("new-identity").onclick = async () => {
  if (!identityArmed) {
    identityArmed = true;
    $("new-identity").textContent = "Click again to clear and close all tabs";
    setTimeout(() => { identityArmed = false; $("new-identity").textContent = "New Identity"; }, 5000);
    return;
  }
  identityArmed = false;
  $("new-identity").textContent = "New Identity";
  try { await send("newIdentity"); say("New Identity: site data cleared, tabs closed"); }
  catch (err) { say(`New Identity failed: ${err.message}`); }
};

// ---------- Ask ----------

$("ask-btn").onclick = async () => {
  if (!can(state.workspace, "ai")) return;
  const text = $("q").value.trim();
  if (!text) return;
  let intent = text;
  if ($("include-page").checked) {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (tab) intent = `${text}\n\n(Page the user shared: ${tab.title ?? ""} — ${tab.url ?? ""})`;
  }
  say("Thinking…");
  try {
    const r = await fetch(`${state.conductorUrl}/api/perform`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ intent, autonomy: "rehearsal" }),
    });
    if (!r.ok) throw new Error(`Conductor answered ${r.status}`);
    $("out").textContent = JSON.stringify(await r.json(), null, 2);
    say("Ready");
  } catch (err) {
    $("out").textContent = `Conductor unreachable at ${state.conductorUrl}.\n${err.message}`;
    say("Conductor offline");
  }
};

// ---------- Rewards ----------

async function loadRewards() {
  if (!can(state?.workspace, "rewards")) return;
  const { summary, receipts } = await send("getRewards");
  $("r-today").textContent = `${formatVeil(summary.earnedTodayMicro)} $VEIL`;
  $("r-minutes").textContent = `${summary.eligibleMinutesToday} of ${POLICY.maxMinutesPerDay} rewarded minutes`;
  $("r-bar").style.width = `${(100 * summary.eligibleMinutesToday) / POLICY.maxMinutesPerDay}%`;
  $("r-pending").textContent = formatVeil(summary.pendingMicro);
  $("r-lifetime").textContent = formatVeil(summary.lifetimeMicro);
  $("r-limit").textContent = formatVeil(summary.dailyLimitMicro);
  $("r-receipts").textContent = String(summary.receipts);
  $("claim").disabled = summary.pendingMicro <= 0 || !state.cidUser;
  $("r-log").textContent = receipts.length
    ? receipts.map((r) => `${r.issuedAt.slice(0, 16).replace("T", " ")}  ${r.amount} $VEIL  ${r.status}`).join("\n")
    : state.cidUser ? "No receipts yet" : "Sign in with Cumulus://ID to claim";
}
$("claim").onclick = async () => {
  try {
    const { receipt } = await send("claimRewards");
    say(receipt ? `Receipt ${receipt.id} for ${receipt.amount} $VEIL` : "Nothing pending");
  } catch (err) { say(err.message); }
  loadRewards();
};

// ---------- Wallet ----------

function renderWallet() {
  $("w-addr").textContent = state.firmAddress ?? "Not set up";
  $("w-bal").textContent = "";
}

// ---------- Cumulus://ID ----------

async function loadProviders() {
  const el = $("social-buttons");
  el.replaceChildren();
  if (state.cidUser) return;
  try {
    const r = await fetch(`${state.cidOrigin}/api/auth/providers`, { credentials: "omit" });
    const providers = await r.json();
    if (!Array.isArray(providers) || !providers.length) {
      el.textContent = "No sign-in providers are configured on the CID origin.";
      return;
    }
    for (const p of providers) {
      const a = document.createElement("a");
      a.className = "provider";
      a.href = `${state.cidOrigin}/api/auth/oauth/${encodeURIComponent(p.id)}/start`;
      a.target = "_blank";
      a.rel = "noopener";
      a.textContent = `Continue with ${p.label}`;
      el.append(a);
    }
  } catch {
    el.textContent = `CID origin unreachable (${state.cidOrigin}).`;
  }
}
$("cid-refresh").onclick = async () => {
  try {
    const { cidUser } = await send("refreshCid");
    say(cidUser ? "Signed in" : "No CID session found. Finish signing in, then refresh.");
  } catch (err) { say(err.message); }
  await refresh();
};

// ---------- Boot ----------

async function refresh() {
  state = await send("getState");
  $("auto-clean").checked = state.autoClean;
  renderTor();
  renderWallet();
  applyWorkspace();
  if ($("rewards").classList.contains("active")) loadRewards();
}

chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== "local") return;
  if (changes.rewardsLedger && $("rewards").classList.contains("active")) loadRewards();
  if (changes.workspace || changes.torEnabled || changes.cidUser) refresh();
});

refresh().catch((err) => say(`VEIL background unavailable: ${err.message}`));
