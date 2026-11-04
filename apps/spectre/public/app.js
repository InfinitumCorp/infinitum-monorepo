// +SPCTR web client. No framework: small render functions over a JSON API.

const $ = (sel, el = document) => el.querySelector(sel);
const root = $("#root");
const STAGE_LABEL = { lead: "Lead", qualified: "Qualified", discovery: "Discovery", proposal: "Proposal", negotiation: "Negotiation", won: "Won", lost: "Lost" };
const OPEN = ["lead", "qualified", "discovery", "proposal", "negotiation"];

/* ---------------- helpers ---------------- */

const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const money = (n, cur = "USD") => new Intl.NumberFormat(undefined, { style: "currency", currency: cur, maximumFractionDigits: 0 }).format(n || 0);
const pct = (p) => `${Math.round((p ?? 0) * 100)}%`;
const date = (iso) => (iso ? new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" }) : "");
const ago = (iso) => {
  const d = (Date.now() - new Date(iso).getTime()) / 86400000;
  if (d < 1) return "today";
  if (d < 2) return "yesterday";
  return d < 30 ? `${Math.round(d)} days ago` : date(iso);
};
const initials = (name) => (name || "?").split(/[\s._-]+/).map((s) => s[0]).join("").slice(0, 2).toUpperCase();

function toast(msg) {
  const t = $("#toast");
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.remove("show"), 2600);
}

async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: { "content-type": "application/json", "x-spectre": "1" },
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: "same-origin",
  });
  const data = await res.json().catch(() => ({}));
  if (res.status === 401) { state.me = null; render(); throw new Error(data.error || "Please sign in"); }
  if (!res.ok) throw Object.assign(new Error(data.error || "Something went wrong"), { status: res.status, code: data.code });
  return data;
}

const icon = {
  today: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
  pipeline: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="4" width="5" height="16" rx="1.5"/><rect x="10" y="4" width="5" height="11" rx="1.5"/><rect x="17" y="4" width="4" height="7" rx="1.5"/></svg>',
  accounts: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 21V7l8-4 8 4v14"/><path d="M9 21v-6h6v6"/></svg>',
  people: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.8-3.5 3.4-5.5 6.5-5.5s5.7 2 6.5 5.5"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14.8c1.8.7 3 2.4 3.5 5.2"/></svg>',
  health: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 12h4l3-7 4 14 3-7h4"/></svg>',
  send: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 19V5M5 12l7-7 7 7"/></svg>',
  settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>',
  menu: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M4 12h16M4 17h16"/></svg>',
};

/* ---------------- passkeys (WebAuthn) ---------------- */

const b64uToBuf = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(s.length / 4) * 4, "=")), (c) => c.charCodeAt(0)).buffer;
const bufToB64u = (b) => btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

async function createPasskey(o) {
  const cred = await navigator.credentials.create({
    publicKey: {
      challenge: b64uToBuf(o.challenge),
      rp: o.rp,
      user: { id: b64uToBuf(o.user.id), name: o.user.name, displayName: o.user.displayName },
      pubKeyCredParams: o.pubKeyCredParams,
      authenticatorSelection: o.authenticatorSelection,
      excludeCredentials: o.excludeCredentials.map((c) => ({ type: "public-key", id: b64uToBuf(c.id) })),
      attestation: "none",
      timeout: o.timeout,
    },
  });
  return { clientDataJSON: bufToB64u(cred.response.clientDataJSON), attestationObject: bufToB64u(cred.response.attestationObject) };
}

async function usePasskey(o) {
  const cred = await navigator.credentials.get({
    publicKey: {
      challenge: b64uToBuf(o.challenge),
      rpId: o.rpId,
      allowCredentials: o.allowCredentials.map((c) => ({ type: "public-key", id: b64uToBuf(c.id) })),
      userVerification: o.userVerification,
      timeout: o.timeout,
    },
  });
  return {
    credentialId: bufToB64u(cred.rawId),
    clientDataJSON: bufToB64u(cred.response.clientDataJSON),
    authenticatorData: bufToB64u(cred.response.authenticatorData),
    signature: bufToB64u(cred.response.signature),
  };
}

/* ---------------- state & routing ---------------- */

const state = { me: undefined, thread: [] };

function route() {
  const [page, id] = location.hash.replace(/^#\/?/, "").split("/");
  return { page: page || "today", id };
}
window.addEventListener("hashchange", () => render());

async function boot() {
  try { state.me = await api("GET", "/api/me"); } catch { state.me = null; }
  render();
}

function render() {
  const { page, id } = route();
  if (!state.me) {
    if (page === "mfa" && id) return renderMfa(id);
    return renderAuth(page === "signin-error" && id ? decodeURIComponent(id) : "");
  }
  renderShell(page);
  const views = { today: renderToday, pipeline: renderPipeline, accounts: id ? () => renderAccount(id) : renderAccounts, people: renderPeople, health: renderHealth, settings: renderSettings };
  (views[page] || renderToday)().catch((e) => { $("main").innerHTML = `<div class="page"><div class="empty">${esc(e.message)}</div></div>`; });
}

/* ---------------- sign-in ---------------- */

function renderAuth(message = "") {
  root.innerHTML = `
    <div class="auth"><div class="auth-card">
      <div class="logo">+SPCTR<sup class="tm">™</sup> <span>(Spectre)</span></div>
      <p class="muted">Sign in with your Cumulus://ID</p>
      <button class="btn primary" id="signin">Continue with passkey</button>
      <div class="social" id="social"></div>
      <div class="divider">or create a CID</div>
      <input class="input" id="username" placeholder="Choose a username" autocomplete="username webauthn" autocapitalize="none" spellcheck="false">
      <button class="btn" id="signup">Create account with passkey</button>
      <div class="error" id="err"></div>
      <p class="faint small">One account for every Infinitum product. Your passkey stays on your device.</p>
    </div></div>`;
  const err = $("#err");
  err.textContent = message;
  const fail = (e) => { err.textContent = e.name === "NotAllowedError" ? "Passkey request was cancelled." : e.message; };
  // Social sign-in buttons for every provider the server has configured.
  api("GET", "/api/auth/providers").then((providers) => {
    $("#social").innerHTML = providers.map((p) => `<a class="btn" href="/api/auth/oauth/${esc(p.id)}/start">Continue with ${esc(p.label)}</a>`).join("");
  }).catch(() => {});

  $("#signin").onclick = async () => {
    err.textContent = "";
    try {
      const { options } = await api("POST", "/api/auth/signin", {});
      await finishSignIn(await usePasskey(options));
    } catch (e) { fail(e); }
  };
  $("#signup").onclick = async () => {
    err.textContent = "";
    const username = $("#username").value.trim();
    if (!username) { err.textContent = "Choose a username first."; return; }
    try {
      const { cid, options } = await api("POST", "/api/auth/signup", { username });
      const response = await createPasskey(options);
      const done = await api("POST", "/api/auth/signup/finish", { cid, response });
      await finishSignIn(await usePasskey(done.options));
    } catch (e) { fail(e); }
  };
}

async function finishSignIn(response) {
  const r = await api("POST", "/api/auth/signin/finish", { response });
  if (r.status === "mfa_required") {
    const code = prompt("Enter the 6-digit code from your authenticator app");
    if (!code) throw new Error("Sign-in needs your authenticator code.");
    await api("POST", "/api/auth/mfa", { mfaToken: r.mfaToken, code });
  }
  state.me = await api("GET", "/api/me");
  location.hash = "#/today";
  render();
}

function renderMfa(mfaToken) {
  root.innerHTML = `
    <div class="auth"><div class="auth-card">
      <div class="logo">+SPCTR<sup class="tm">™</sup> <span>(Spectre)</span></div>
      <p class="muted">Enter the 6-digit code from your authenticator app</p>
      <input class="input code" id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="123456">
      <button class="btn primary" id="verify">Verify</button>
      <div class="error" id="err"></div>
      <a class="faint small" href="#/">Use a different sign-in method</a>
    </div></div>`;
  $("#code").focus();
  const submit = async () => {
    try {
      await api("POST", "/api/auth/mfa", { mfaToken, code: $("#code").value.trim() });
      state.me = await api("GET", "/api/me");
      location.hash = "#/today";
      render();
    } catch (e) { $("#err").textContent = e.message; }
  };
  $("#verify").onclick = submit;
  $("#code").onkeydown = (e) => { if (e.key === "Enter") submit(); };
}

/* ---------------- shell ---------------- */

function renderShell(page) {
  if (!$(".shell")) {
    root.innerHTML = `
      <div class="shell">
        <button class="btn ghost small menu-btn" id="menu" aria-label="Menu">${icon.menu}</button>
        <aside class="side">
          <div class="brand">+SPCTR<sup class="tm">™</sup></div>
          <input class="input small" id="global-search" placeholder="Search…" style="margin:8px 12px;width:calc(100% - 24px)">
          <nav class="nav">
            <a href="#/today" data-p="today">${icon.today}Today</a>
            <a href="#/pipeline" data-p="pipeline">${icon.pipeline}Pipeline</a>
            <a href="#/accounts" data-p="accounts">${icon.accounts}Accounts</a>
            <a href="#/people" data-p="people">${icon.people}People</a>
            <a href="#/health" data-p="health">${icon.health}Data health</a>
          </nav>
          <div class="spacer"></div>
          <nav class="nav"><a href="#/settings" data-p="settings">${icon.settings}Settings</a></nav>
          <div class="me">
            <div class="avatar">${esc(initials(state.me.username))}</div>
            <div class="who"><div>${esc(state.me.username)}</div><div class="faint small">${esc(state.me.mailAddress)}</div></div>
          </div>
          <button class="btn ghost small" id="signout">Sign out</button>
        </aside>
        <main></main>
      </div>`;
    $("#menu").onclick = () => $(".shell").classList.toggle("open");
    $("#signout").onclick = async () => { await api("POST", "/api/auth/signout"); state.me = null; render(); };
    const gs = $("#global-search");
    if (gs) {
      gs.onkeydown = async (e) => {
        if (e.key === "Enter") {
          const q = gs.value.trim();
          if (!q) return;
          try {
            const r = await api("GET", `/api/search?q=${encodeURIComponent(q)}`);
            const lines = [
              ...r.companies.map((c) => `Account: ${c.name}`),
              ...r.contacts.map((c) => `Person: ${c.name}`),
              ...r.deals.map((d) => `Deal: ${d.name}`),
            ];
            alert(lines.length ? lines.join("\n") : "No results");
          } catch (err) { toast(err.message); }
        }
      };
    }
  }
  $(".shell").classList.remove("open");
  document.querySelectorAll(".nav a").forEach((a) => a.classList.toggle("active", a.dataset.p === page));
}

const main = () => $("main");
const fillBars = (el) => el.querySelectorAll("[data-w]").forEach((b) => { b.style.width = b.dataset.w; });

/* ---------------- Today ---------------- */

async function renderToday() {
  const [t, m, acts] = await Promise.all([api("GET", "/api/today"), api("GET", "/api/metrics"), api("GET", "/api/activities?limit=8")]);
  const first = state.thread.length === 0;
  main().innerHTML = `
    <div class="page narrow">
      ${first ? `<div class="hero"><h1>What can I help with?</h1></div>` : ""}
      <div class="thread">${state.thread.map((m) => `<div class="msg ${m.role}${m.notice ? " notice" : ""}">${esc(m.text)}</div>`).join("")}</div>
      <form class="composer" id="ask">
        <textarea id="q" rows="1" placeholder="Ask Spectre anything" aria-label="Ask Spectre"></textarea>
        <button class="btn primary send" aria-label="Send">${icon.send}</button>
      </form>
      ${first ? `<div class="suggestions">
        <button data-q="Which deals are most at risk this month, and why?">Deals at risk</button>
        <button data-q="Which government accounts have budget this quarter and no meeting booked?">Government accounts without meetings</button>
        <button data-q="Summarize my pipeline for a board update.">Board summary</button>
      </div>` : ""}

      <div class="section stats">
        <div class="stat"><div class="muted small">Open deals</div><div class="v">${t.totals.openDeals}</div></div>
        <div class="stat"><div class="muted small">Pipeline</div><div class="v">${money(t.totals.pipeline)}</div></div>
        <div class="stat"><div class="muted small">Weighted</div><div class="v">${money(t.totals.weighted)}</div></div>
        <div class="stat"><div class="muted small">Accounts</div><div class="v">${m.companies}</div></div>
        <div class="stat"><div class="muted small">People</div><div class="v">${m.contacts}</div></div>
      </div>

      <div class="section"><h2>Next best actions</h2>
        <button class="btn small" id="run-exec">Run AI actions</button>
        ${t.actions.length ? `<div class="list">${t.actions.slice(0, 8).map((a) => `
          <button class="item" data-deal="${esc(a.dealId)}">
            <span class="pill p${a.priority}">${a.priority === 1 ? "Today" : a.priority === 2 ? "This week" : "Soon"}</span>
            <div class="main"><div class="title">${esc(a.action)}</div><div class="sub">${esc(a.company ?? "")} · ${esc(a.deal)} · ${esc(a.why)}</div></div>
            <div class="muted small">${money(a.amount, a.currency)}</div>
          </button>`).join("")}</div>` : `<div class="empty">No open deals yet. <a href="#/pipeline">Add your first deal</a>.</div>`}
      </div>

      ${t.alerts.length ? `<div class="section"><h2>Risk alerts</h2><div class="list">${t.alerts.slice(0, 6).map((a) => `
        <button class="item" data-deal="${esc(a.dealId)}"><span class="pill ${a.severity}">${a.severity}</span><div class="main">${esc(a.message)}</div></button>`).join("")}</div></div>` : ""}

      <div class="section"><h2>Recent activity</h2>
        ${acts.activities.length ? `<div class="list">${acts.activities.map((a) => `<div class="item"><div class="main"><div class="title">${esc(a.summary)}</div><div class="sub">${esc(a.kind)} · ${ago(a.at)}</div></div></div>`).join("")}</div>` : `<div class="empty">No activity yet.</div>`}
      </div>
      <div class="section"><h2>Forecast</h2>
        <table class="simple"><thead><tr><th>Month</th><th class="num">Won</th><th class="num">Weighted</th><th class="num">Best case</th></tr></thead>
        <tbody>${t.forecast.map((f) => `<tr><td>${new Date(f.period + "-01T00:00:00Z").toLocaleDateString(undefined, { month: "long", year: "numeric", timeZone: "UTC" })}</td><td class="num">${money(f.committed)}</td><td class="num">${money(f.weighted)}</td><td class="num">${money(f.bestCase)}</td></tr>`).join("")}</tbody></table>
        <p class="faint small">Weighted uses each deal's win probability. Forecasts support your judgment; they're estimates, not guarantees.</p>
      </div>
    </div>`;

  const q = $("#q");
  q.addEventListener("input", () => { q.style.height = "auto"; q.style.height = q.scrollHeight + "px"; });
  q.addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); $("#ask").requestSubmit(); } });
  $("#ask").onsubmit = (e) => { e.preventDefault(); ask(q.value); };
  document.querySelectorAll("[data-q]").forEach((b) => (b.onclick = () => ask(b.dataset.q)));
  document.querySelectorAll("[data-deal]").forEach((b) => (b.onclick = () => openDeal(b.dataset.deal)));
  $("#run-exec")?.addEventListener("click", async () => {
    toast("Running actions…");
    try {
      const r = await api("POST", "/api/execution/run", {});
      toast("Actions executed");
      console.log(r);
      renderToday();
    } catch (e) { toast(e.message); }
  });
}

async function ask(question) {
  question = question.trim();
  if (!question) return;
  state.thread.push({ role: "user", text: question });
  state.thread.push({ role: "ai", text: "Thinking…", pending: true });
  await renderToday();
  try {
    const { answer } = await api("POST", "/api/ask", { question });
    state.thread[state.thread.length - 1] = { role: "ai", text: answer };
  } catch (e) {
    state.thread[state.thread.length - 1] = { role: "ai", text: e.message, notice: true };
  }
  await renderToday();
  $("#q")?.focus();
}

/* ---------------- Pipeline ---------------- */

async function renderPipeline() {
  const min = new URLSearchParams(location.search).get("min") || "";
  const [{ deals }, stats] = await Promise.all([api("GET", `/api/deals${min ? `?minAmount=${min}` : ""}`), api("GET", "/api/pipeline/stats")]);
  const cols = [...OPEN, "won"];
  main().innerHTML = `
    <div class="page wide">
      <div class="page-head"><h1>Pipeline</h1><div class="row"><input class="input small" id="min-amount" placeholder="Min amount" value="${esc(min)}" style="width:120px"><button class="btn primary" id="new-deal">New deal</button></div></div>
      <div class="faint small">${Object.entries(stats.byStage || {}).map(([s, v]) => `${STAGE_LABEL[s] || s}: ${v.count}`).join(" · ")}</div>
      <div class="board">${cols.map((s) => {
        const list = deals.filter((d) => d.stage === s);
        return `<div class="col"><div class="col-head"><span>${STAGE_LABEL[s]}</span><span class="faint">${money(list.reduce((n, d) => n + d.amount, 0))}</span></div>
          ${list.map((d) => `<button class="card" data-deal="${esc(d.id)}">
            <div class="name">${esc(d.name)}</div><div class="muted small">${esc(d.company ?? "")}</div>
            <div class="meta"><span>${money(d.amount, d.currency)}</span><span>${s === "won" ? "Won" : pct(d.score?.probability)}</span>${d.score?.risk && d.score.risk !== "low" ? `<span class="pill ${d.score.risk}">${d.score.risk}</span>` : ""}</div>
            ${s === "won" ? "" : `<div class="bar"><i data-w="${pct(d.score?.probability)}"></i></div>`}
          </button>`).join("") || `<div class="faint small pad6">No deals</div>`}
        </div>`;
      }).join("")}</div>
    </div>`;
  fillBars(main());
  $("#new-deal").onclick = () => newDealForm();
  $("#min-amount")?.addEventListener("change", () => {
    const v = $("#min-amount").value;
    location.hash = "#/pipeline";
    if (v) history.replaceState(null, "", `?min=${v}`);
    renderPipeline();
  });
  document.querySelectorAll("[data-deal]").forEach((b) => (b.onclick = () => openDeal(b.dataset.deal)));
}

/* ---------------- Deal drawer ---------------- */

async function openDeal(id) {
  const d = await api("GET", `/api/deals/${encodeURIComponent(id)}`);
  closeDrawer();
  const wrap = document.createElement("div");
  wrap.innerHTML = `
    <div class="drawer-bg"></div>
    <aside class="drawer" role="dialog" aria-label="${esc(d.deal.name)}">
      <button class="btn ghost small close" aria-label="Close">Close</button>
      <div class="muted small">${esc(d.company.name)}</div>
      <h2>${esc(d.deal.name)}</h2>
      <div class="row small muted">${money(d.deal.amount, d.deal.currency)} · closes ${date(d.deal.closeDate)} · ${esc(d.deal.market)}</div>

      <div class="section"><div class="row">
        <select class="input" id="stage" aria-label="Stage">${Object.keys(STAGE_LABEL).map((s) => `<option value="${s}" ${s === d.deal.stage ? "selected" : ""}>${STAGE_LABEL[s]}</option>`).join("")}</select>
      </div></div>

      ${OPEN.includes(d.deal.stage) ? `
      <div class="section"><h2>Win probability: ${pct(d.score.probability)} <span class="pill ${d.score.risk}">${d.score.risk} risk</span></h2>
        <div class="factor"><span class="muted">Typical for ${STAGE_LABEL[d.deal.stage].toLowerCase()} stage</span><span>${pct(d.score.base)}</span></div>
        ${d.score.factors.map((f) => `<div class="factor"><span>${esc(f.label)}</span><span class="${f.effect >= 1 ? "up" : "down"}">${f.effect >= 1 ? "▲" : "▼"} ×${f.effect}</span></div>`).join("")}
      </div>
      <div class="section"><h2>Next best action</h2><div class="next"><div class="act">${esc(d.next.action)}</div><div class="muted small">${esc(d.next.why)}</div></div></div>` : ""}

      <div class="section"><h2>People</h2>
        ${d.contacts.length ? d.contacts.map((c) => `<div class="factor"><span>${esc(c.name)}<span class="muted"> · ${esc(c.title ?? "")}</span></span><span class="pill">${esc(c.influence ?? "contact")}</span></div>`).join("") : `<div class="muted small">No one linked yet.</div>`}
      </div>

      <div class="section"><h2>Log activity</h2>
        <form id="log" class="grid2">
          <select class="input" name="kind"><option value="call">Call</option><option value="email">Email</option><option value="meeting">Meeting</option><option value="note">Note</option><option value="task">Task</option></select>
          <label class="row small muted"><input type="checkbox" name="inbound"> Customer initiated</label>
          <textarea class="input" name="summary" placeholder="What happened?" required></textarea>
          <div class="row"><input class="input hidden" type="date" name="dueAt" aria-label="Due date"><button class="btn primary">Save</button></div>
        </form>
      </div>

      <div class="section timeline"><h2>Activity</h2>
        ${d.activities.map((a) => `<div class="ev"><div class="when">${esc(a.kind)} · ${ago(a.at)}${a.kind === "task" ? (a.done ? " · done" : ` · due ${date(a.dueAt)} <button class="btn small ghost" data-done="${esc(a.id)}">Mark done</button>`) : ""}</div>${esc(a.summary)}</div>`).join("") || `<div class="muted small">Nothing logged yet.</div>`}
      </div>

      <div class="section timeline"><h2>Audit trail</h2>
        <p class="faint small">Recorded on Firmament Network. Each entry is signed and linked to the one before it.</p>
        ${d.history.map((h) => `<div class="ev"><div class="when">${date(h.at)} · ${esc(h.actor === state.me.cid ? "you" : h.actor)}</div>${esc(describeEvent(h.payload))}<div class="hash">${esc(h.hash.slice(0, 16))}…</div></div>`).join("")}
      </div>
    </aside>`;
  document.body.appendChild(wrap);
  wrap.id = "drawer";
  const close = () => closeDrawer();
  $(".drawer-bg", wrap).onclick = close;
  $(".close", wrap).onclick = close;
  const form = $("#log", wrap);
  form.kind.onchange = () => form.dueAt.classList.toggle("hidden", form.kind.value !== "task");
  form.onsubmit = async (e) => {
    e.preventDefault();
    await api("POST", "/api/activities", { kind: form.kind.value, summary: form.summary.value, inbound: form.inbound.checked, dealId: id, dueAt: form.dueAt.value ? new Date(form.dueAt.value).toISOString() : undefined });
    toast("Activity logged");
    openDeal(id);
    refreshBehind();
  };
  $("#stage", wrap).onchange = async (e) => {
    const stage = e.target.value;
    let lostReason;
    if (stage === "lost") { lostReason = prompt("Why was it lost? This improves your forecasts."); if (!lostReason) { e.target.value = d.deal.stage; return; } }
    await api("POST", `/api/deals/${encodeURIComponent(id)}/stage`, { stage, lostReason });
    toast(`Moved to ${STAGE_LABEL[stage]}`);
    openDeal(id);
    refreshBehind();
  };
  wrap.querySelectorAll("[data-done]").forEach((b) => (b.onclick = async () => { await api("POST", `/api/activities/${b.dataset.done}/complete`); openDeal(id); refreshBehind(); }));
}

function describeEvent(p) {
  if (p.event === "stage_changed") return `Stage ${STAGE_LABEL[p.from]} → ${STAGE_LABEL[p.to]}${p.lostReason ? ` (${p.lostReason})` : ""}`;
  if (p.event === "created") return "Deal created";
  if (p.event === "updated") return `Updated ${Object.keys(p.changes || {}).join(", ")}`;
  return p.event;
}

function closeDrawer() { $("#drawer")?.remove(); }
function refreshBehind() { const { page } = route(); if (page === "today") renderToday(); if (page === "pipeline") renderPipeline(); }
document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeDrawer(); });

/* ---------------- Accounts ---------------- */

async function renderAccounts() {
  const list = await api("GET", "/api/companies");
  main().innerHTML = `
    <div class="page">
      <div class="page-head"><h1>Accounts</h1><div class="row"><button class="btn" id="research-all">Research all</button><button class="btn" id="prospect">Prospect with AI</button><button class="btn primary" id="new-co">New account</button></div></div>
      ${list.length ? `<div class="list">${list.map((c) => `
        <a class="item" href="#/accounts/${esc(c.id)}">
          <div class="avatar">${esc(initials(c.name))}</div>
          <div class="main"><div class="title">${esc(c.name)}</div><div class="sub">${esc([c.kind, c.industry, c.domain].filter(Boolean).join(" · "))}</div></div>
          <div class="muted small">${c.openDeals} open deal${c.openDeals === 1 ? "" : "s"} · ${c.contacts} people</div>
        </a>`).join("")}</div>` : `<div class="empty">Add a company or government agency to start.</div>`}
    </div>`;
  $("#new-co").onclick = () => newCompanyForm();
  $("#research-all").onclick = async () => {
    toast("Researching all accounts…");
    try {
      const r = await api("POST", "/api/research/all", {});
      toast(`Researched ${r.researched} accounts`);
      renderAccounts();
    } catch (e) { toast(e.message); }
  };
  $("#prospect").onclick = async () => {
    toast("Prospecting…");
    try {
      const result = await api("POST", "/api/prospect", {});
      toast("Suggestions ready");
      console.log(result);
      alert(JSON.stringify(result, null, 2).slice(0, 500));
    } catch (e) { toast(e.message); }
  };
}

async function renderAccount(id) {
  const a = await api("GET", `/api/companies/${encodeURIComponent(id)}`);
  const c = a.company;
  main().innerHTML = `
    <div class="page">
      <div class="page-head">
        <div><div class="muted small"><a href="#/accounts">Accounts</a> / ${esc(c.kind)}</div><h1>${esc(c.name)}</h1><div class="muted small">${esc([c.industry, c.domain].filter(Boolean).join(" · "))}</div></div>
        <div class="row"><button class="btn" id="add-person">Add person</button><button class="btn primary" id="add-deal">New deal</button></div>
      </div>

      ${a.warmPath ? `<div class="next"><div class="act">Warmest way in: ${a.warmPath.steps.length === 1 ? "you know them directly" : `${a.warmPath.steps.length - 1} introduction${a.warmPath.steps.length > 2 ? "s" : ""} away`}</div><div class="muted small">Warmth ${pct(a.warmPath.warmth)}</div></div>` : ""}

      <div class="section"><h2>Buying committee</h2>
        ${a.gaps.length ? `<div class="row small wrap mb10">${a.gaps.map((g) => `<span class="pill p2">${esc(g)}</span>`).join("")}</div>` : ""}
        ${a.committee.length ? `<div class="list">${a.committee.map((p) => `<div class="item"><div class="avatar">${esc(initials(p.name))}</div><div class="main"><div class="title">${esc(p.name)}</div><div class="sub">${esc(p.title ?? "")}</div></div><span class="pill">${esc(p.influence ?? "contact")}</span></div>`).join("")}</div>` : `<div class="empty">No people yet.</div>`}
      </div>

      <div class="section"><h2>Deals</h2>
        ${a.deals.length ? `<div class="list">${a.deals.map((d) => `<button class="item" data-deal="${esc(d.id)}"><div class="main"><div class="title">${esc(d.name)}</div><div class="sub">${STAGE_LABEL[d.stage]} · closes ${date(d.closeDate)}</div></div><div class="muted small">${money(d.amount, d.currency)}${OPEN.includes(d.stage) ? ` · ${pct(d.score?.probability)}` : ""}</div></button>`).join("")}</div>` : `<div class="empty">No deals yet.</div>`}
      </div>

      <div class="section"><h2>Research</h2>
        <button class="btn" id="ai-research">Research with AI</button>
        ${c.facts.length ? `<div class="list">${c.facts.map((f) => `<div class="item"><div class="main"><div class="title">${esc(f.value)}</div><div class="sub">${esc(f.field)} · source: ${esc(f.source)} · ${date(f.observedAt)}</div></div></div>`).join("")}</div>` : `<div class="empty">No research yet.</div>`}
        <form id="fact" class="row wrap mt10">
          <input class="input" name="field" placeholder="Topic (e.g. funding)" required>
          <input class="input" name="value" placeholder="What you learned" required>
          <input class="input" name="source" placeholder="Source link">
          <button class="btn">Add</button>
        </form>
      </div>
    </div>`;
  $("#add-person").onclick = () => newContactForm(id);
  $("#add-deal").onclick = () => newDealForm(id);
  $("#ai-research").onclick = async () => {
    toast("Researching…");
    try {
      const result = await api("POST", "/api/research", { name: c.name, companyId: id });
      const n = result.persisted || (result.facts || []).length;
      toast(`Research complete · ${n} facts saved`);
      if (result.summary) alert(result.summary);
      renderAccount(id);
    } catch (e) { toast(e.message); }
  };
  document.querySelectorAll("[data-deal]").forEach((b) => (b.onclick = () => openDeal(b.dataset.deal)));
  const f = $("#fact");
  f.onsubmit = async (e) => {
    e.preventDefault();
    await api("POST", `/api/companies/${encodeURIComponent(id)}/facts`, { field: f.field.value, value: f.value.value, source: f.source.value });
    toast("Added, with its source recorded");
    renderAccount(id);
  };
}

/* ---------------- People ---------------- */

async function renderPeople() {
  const list = await api("GET", "/api/contacts");
  main().innerHTML = `
    <div class="page">
      <div class="page-head"><h1>People</h1><button class="btn primary" id="new-ct">Add person</button></div>
      ${list.length ? `<div class="list">${list.map((c) => `
        <div class="item"><div class="avatar">${esc(initials(c.name))}</div>
          <div class="main"><div class="title">${esc(c.name)}</div><div class="sub">${esc([c.title, c.company].filter(Boolean).join(" · "))}</div></div>
          <div class="muted small">${esc(c.email ?? "")}</div>
          <button class="btn ghost small" data-research="${esc(c.id)}" data-name="${esc(c.name)}" data-company="${esc(c.company || "")}">Research</button>
          <button class="btn ghost small" data-optout="${esc(c.id)}" title="Erase this person's details and never re-add them">Opt out</button>
        </div>`).join("")}</div>` : `<div class="empty">No people yet.</div>`}
    </div>`;
  $("#new-ct").onclick = () => newContactForm();
  document.querySelectorAll("[data-research]").forEach((b) => (b.onclick = async () => {
    toast("Researching…");
    try {
      const r = await api("POST", "/api/research/contact", { name: b.dataset.name, company: b.dataset.company });
      toast("Research done");
      if (r.summary) alert(r.summary);
    } catch (e) { toast(e.message); }
  }));
  document.querySelectorAll("[data-optout]").forEach((b) => (b.onclick = async () => {
    if (!confirm("Erase this person's details? Spectre will keep only a do-not-contact marker.")) return;
    await api("POST", `/api/contacts/${b.dataset.optout}/opt-out`);
    toast("Opted out");
    renderPeople();
  }));
}

/* ---------------- Data health ---------------- */

async function renderHealth() {
  const h = await api("GET", "/api/data-health");
  const group = (g, kind) => `
    <div class="item"><div class="main"><div class="title">${esc(g.keep.name)}</div><div class="sub">${esc(g.reason)} · also: ${g.duplicates.map((d) => esc(d.name)).join(", ")}</div></div>
    <button class="btn small" data-merge='${esc(JSON.stringify({ kind, keepId: g.keep.id, mergeIds: g.duplicates.map((d) => d.id) }))}'>Merge</button></div>`;
  const dups = [...h.duplicateCompanies.map((g) => group(g, "company")), ...h.duplicateContacts.map((g) => group(g, "contact"))];
  main().innerHTML = `
    <div class="page narrow">
      <div class="page-head"><h1>Data health</h1></div>
      <div class="section"><h2>Possible duplicates</h2>${dups.length ? `<div class="list">${dups.join("")}</div>` : `<div class="empty">No duplicates found.</div>`}</div>
      <div class="section"><h2>Needs attention</h2>${h.issues.length ? `<div class="list">${h.issues.map((i) => `<div class="item"><span class="pill">${i.kind === "stale_contact" ? "stale" : "missing"}</span><div class="main">${esc(i.message)}</div></div>`).join("")}</div>` : `<div class="empty">Everything looks complete.</div>`}</div>
    </div>`;
  document.querySelectorAll("[data-merge]").forEach((b) => (b.onclick = async () => {
    await api("POST", "/api/data-health/merge", JSON.parse(b.dataset.merge));
    toast("Merged. Deals and activity now point to one record.");
    renderHealth();
  }));
}

/* ---------------- Forms ---------------- */

function modal(html, onSubmit) {
  const dlg = $("#modal");
  const form = $("#modal-form");
  form.innerHTML = html + `<div class="error" id="m-err"></div><div class="actions"><button type="button" class="btn" id="m-cancel">Cancel</button><button class="btn primary" id="m-ok">Save</button></div>`;
  $("#m-cancel").onclick = () => dlg.close();
  form.onsubmit = async (e) => {
    e.preventDefault();
    try { await onSubmit(new FormData(form)); dlg.close(); } catch (err) { $("#m-err").textContent = err.message; }
  };
  dlg.showModal();
  form.querySelector("input, select")?.focus();
}

function newCompanyForm() {
  modal(`<h2>New account</h2>
    <label class="field">Name<input class="input" name="name" required></label>
    <div class="grid2">
      <label class="field">Website<input class="input" name="domain" placeholder="example.com"></label>
      <label class="field">Type<select class="input" name="kind"><option value="company">Company</option><option value="agency">Government agency</option><option value="partner">Partner</option><option value="investor">Investor</option></select></label>
    </div>
    <label class="field">Industry<input class="input" name="industry"></label>`, async (f) => {
    const c = await api("POST", "/api/companies", Object.fromEntries(f));
    toast("Account added");
    location.hash = `#/accounts/${c.id}`;
  });
}

async function newContactForm(companyId) {
  const companies = await api("GET", "/api/companies");
  modal(`<h2>Add person</h2>
    <div class="grid2">
      <label class="field">Name<input class="input" name="name" required></label>
      <label class="field">Title<input class="input" name="title"></label>
      <label class="field">Email<input class="input" name="email" type="email"></label>
      <label class="field">Phone<input class="input" name="phone"></label>
    </div>
    <div class="grid2">
      <label class="field">Account<select class="input" name="companyId"><option value="">None</option>${companies.map((c) => `<option value="${esc(c.id)}" ${c.id === companyId ? "selected" : ""}>${esc(c.name)}</option>`).join("")}</select></label>
      <label class="field">Role in decision<select class="input" name="influence"><option value="">Unknown</option><option value="decision-maker">Decision-maker</option><option value="champion">Champion</option><option value="influencer">Influencer</option><option value="blocker">Blocker</option><option value="user">User</option></select></label>
    </div>
    <label class="row small muted"><input type="checkbox" name="knowMe"> I know this person personally</label>`, async (f) => {
    const body = Object.fromEntries(f);
    const knowMe = body.knowMe === "on";
    delete body.knowMe;
    const c = await api("POST", "/api/contacts", body);
    if (knowMe) await api("POST", "/api/relationships", { from: "me", to: c.id, strength: 4 });
    toast("Person added");
    render();
  });
}

async function newDealForm(companyId) {
  const companies = await api("GET", "/api/companies");
  if (!companies.length) { toast("Add an account first"); return newCompanyForm(); }
  const contacts = await api("GET", "/api/contacts");
  const inMonth = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  modal(`<h2>New deal</h2>
    <label class="field">Deal name<input class="input" name="name" required></label>
    <div class="grid2">
      <label class="field">Account<select class="input" name="companyId" required>${companies.map((c) => `<option value="${esc(c.id)}" ${c.id === companyId ? "selected" : ""}>${esc(c.name)}</option>`).join("")}</select></label>
      <label class="field">Market<select class="input" name="market"><option value="commercial">Commercial</option><option value="government">Government</option><option value="partnership">Partnership</option><option value="investor">Investor</option></select></label>
      <label class="field">Amount (USD)<input class="input" name="amount" type="number" min="0" step="1" required></label>
      <label class="field">Expected close<input class="input" name="closeDate" type="date" value="${inMonth}" required></label>
    </div>
    <label class="field">People involved<select class="input" name="contactIds" multiple size="4">${contacts.map((c) => `<option value="${esc(c.id)}">${esc(c.name)}${c.company ? ` · ${esc(c.company)}` : ""}</option>`).join("")}</select></label>`, async (f) => {
    const body = Object.fromEntries(f);
    body.contactIds = f.getAll("contactIds");
    await api("POST", "/api/deals", body);
    toast("Deal created");
    render();
  });
}

boot();

/* ---------------- Settings: sign-in methods ---------------- */

async function renderSettings() {
  const data = await api("GET", "/api/me/identities");
  const linked = new Map(data.linked.map((i) => [i.provider, i]));
  const label = Object.fromEntries(data.available.map((p) => [p.id, p.label]));
  for (const i of data.linked) label[i.provider] ??= i.provider;
  const ids = [...new Set([...data.available.map((p) => p.id), ...data.linked.map((i) => i.provider)])];
  main().innerHTML = `
    <div class="page narrow">
      <div class="page-head"><h1>Settings</h1></div>
      <div class="section">
        <h2>Account</h2>
        <div class="list"><div class="item"><div class="avatar">${esc(initials(state.me.username))}</div>
          <div class="main"><div class="title">${esc(state.me.username)}</div><div class="sub">${esc(state.me.mailAddress)} · ${esc(state.me.handle)}</div></div></div></div>
      </div>
      <div class="section">
        <h2>Ways to sign in</h2>
        <div class="list">
          <div class="item"><div class="main"><div class="title">Passkeys</div><div class="sub">${data.passkeys ? `${data.passkeys} on this account` : "None yet"}</div></div></div>
          ${ids.map((id) => {
            const l = linked.get(id);
            return `<div class="item"><div class="main"><div class="title">${esc(label[id])}</div>
              <div class="sub">${l ? `Linked${l.email ? ` · ${esc(l.email)}` : ""} · ${date(l.linkedAt)}` : "Not linked"}</div></div>
              ${l ? `<button class="btn small" data-unlink="${esc(id)}">Unlink</button>`
                  : data.available.some((p) => p.id === id) ? `<a class="btn small" href="/api/auth/oauth/${esc(id)}/start?link=1">Link</a>` : ""}</div>`;
          }).join("")}
        </div>
        <p class="faint small mt10">Linked accounts sign you in to this same Cumulus://ID. Your multifactor setting still applies.</p>
      </div>
      <div class="section">
        <h2>AI</h2>
        <div class="list">
          <div class="item"><div class="main"><div class="title">METATRON scoring</div><div class="sub">Default on for win probability</div></div></div>
          <div class="item"><div class="main"><div class="title">Research</div><div class="sub">Public web search for account facts</div></div></div>
          <div class="item"><div class="main"><div class="title">Agents</div><div class="sub">Researcher, Prospector, Deal Coach, Query</div></div></div>
        </div>
        <p class="faint small mt10">Set ANTHROPIC_API_KEY for Ask Spectre. Research uses public search.</p>
      </div>
    </div>`;
  main().querySelectorAll("[data-unlink]").forEach((b) => {
    b.onclick = async () => {
      try { await api("POST", `/api/me/identities/${b.dataset.unlink}/unlink`); toast("Unlinked"); renderSettings(); }
      catch (e) { toast(e.message); }
    };
  });
}
