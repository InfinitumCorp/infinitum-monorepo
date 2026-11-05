const state = { view: "mail", box: "inbox", query: "", selected: null, data: null, composer: null, error: "", wallet: null };

const el = (tag, props = {}, children = []) => {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "class") node.className = value;
    else if (key === "value") node.value = value;
    else if (key === "style") node.style.cssText = value; // CSSOM, allowed by the page's CSP (style attributes are not)
    else if (key.startsWith("on")) node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value != null) node.setAttribute(key, value);
  }
  for (const child of children) node.append(child?.nodeType ? child : document.createTextNode(child ?? ""));
  return node;
};

async function api(path, body) {
  const response = await fetch(path, {
    method: body ? "POST" : "GET",
    headers: { "Content-Type": "application/json", "x-cumulus": "1" },
    body: body ? JSON.stringify(body) : undefined,
    credentials: "same-origin",
  });
  const payload = await response.json().catch(() => ({}));
  if (response.status === 401) {
    state.data = null;
    renderAuth();
    throw new Error(payload.error || "Please sign in");
  }
  if (!response.ok) throw new Error(payload.error || "Request failed");
  if (payload.state) state.data = { ...state.data, ...payload.state };
  else if (payload.messages) state.data = { ...state.data, ...payload };
  return payload;
}

function inBox(message, box) {
  if (box === "starred") return message.starred && message.folder !== "trash";
  return message.folder === box;
}

function rows() {
  const messages = state.data.messages;
  const latest = new Map();
  for (const message of messages.filter((item) => inBox(item, state.box))) {
    const prev = latest.get(message.threadId);
    if (!prev || prev.at < message.at) latest.set(message.threadId, message);
  }
  const q = state.query.trim().toLowerCase();
  return [...latest.values()]
    .filter((row) => !q || messages.some((item) => item.threadId === row.threadId && matches(item, q)))
    .sort((a, b) => (a.at < b.at ? 1 : -1));
}

function matches(item, query) {
  return query.split(/\s+/).filter(Boolean).every((token) => {
    if (token.startsWith("from:")) return `${item.from.name} ${item.from.email}`.toLowerCase().includes(token.slice(5));
    if (token.startsWith("to:")) return [...item.to, ...item.cc].some((person) => `${person.name} ${person.email}`.toLowerCase().includes(token.slice(3)));
    if (token.startsWith("subject:")) return item.subject.toLowerCase().includes(token.slice(8));
    if (token === "is:unread") return item.unread;
    if (token === "is:starred") return item.starred;
    if (token === "has:file") return Boolean(item.attachments?.length);
    const files = (item.attachments || []).map((file) => file.name).join(" ");
    return `${item.subject} ${item.body} ${item.from.name} ${item.from.email} ${files}`.toLowerCase().includes(token);
  });
}

function title(message) {
  if (message.from.email === state.data.me.email) return `To ${message.to[0]?.name || "someone"}`;
  return message.from.name;
}

function shortDate(iso) {
  return new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", day: "2-digit", month: "short" }).format(new Date(iso));
}

async function openMessage(message) {
  if (message.folder === "drafts") {
    state.composer = { draftId: message.id, threadId: message.threadId, to: message.to.map((p) => p.email).join(", "), cc: "", subject: message.subject, body: message.body, attachments: message.attachments || [] };
    render();
    return;
  }
  state.selected = message.id;
  document.body.classList.add("reading");
  await api("/api/read", { threadId: message.threadId });
  render();
}

function render() {
  const data = state.data;
  if (!data) return;
  const root = document.querySelector("#app");
  root.replaceChildren();
  const selected = data.messages.find((message) => message.id === state.selected);
  root.append(
    el("header", {}, [
      el("div", {}, [el("div", { class: "mark" }, ["Cumulus", el("span", {}, ["://"]), "Mail"]), el("div", { class: "sub" }, [data.me.email])]),
      el("label", {}, [el("input", { placeholder: "Search mail", "aria-label": "Search mail", value: state.query, onInput: (event) => { state.query = event.target.value; render(); } })]),
      el("button", { class: "compose", onClick: () => { state.composer = { to: "", cc: "", subject: "", body: "", attachments: [] }; state.error = ""; render(); } }, ["Compose"]),
    ]),
    el("div", { class: "layout" }, [
      sidebar(data),
      state.view === "mail" ? list() : aux(data),
      state.view === "mail" ? reader(selected) : el("div"),
    ]),
  );
  if (state.composer && !state.composer.inline) root.append(modal());
}

function sidebar(data) {
  const views = [["mail", "Mail"], ["wallet", "Wallet"], ["contacts", "Contacts"], ["tasks", "Conductor"]];
  const boxes = [["inbox", "Inbox"], ["starred", "Starred"], ["sent", "Sent"], ["drafts", "Drafts"], ["archive", "Archive"], ["trash", "Trash"]];
  return el("nav", {}, [
    ...views.map(([id, label]) => el("button", { "aria-current": state.view === id, onClick: () => { state.view = id; state.error = ""; document.body.classList.remove("reading"); if (id === "wallet") loadWallet(); render(); } }, [
      label,
      id === "mail" && data.unread ? el("span", { class: "count" }, [String(data.unread)]) : el("span"),
    ])),
    el("button", { class: "mobile-only", "aria-current": state.view === "account", onClick: () => { state.view = "account"; render(); } }, ["Account"]),
    ...(state.view === "mail" ? boxes.map(([id, label]) => el("button", { class: "box", "aria-current": state.box === id, onClick: () => { state.box = id; state.selected = null; document.body.classList.remove("reading"); render(); } }, [label])) : []),
    el("button", { class: "account", onClick: () => { state.view = "account"; render(); } }, [
      data.me.name,
      el("div", { class: "sub" }, [data.me.email]),
      el("div", { class: "bar" }, [el("span", { style: `width:${data.quota.pct}%` })]),
      el("div", { class: "muted" }, [`${data.quota.label} · shared with Cloud`]),
    ]),
  ]);
}

function list() {
  const items = rows().map((message) => el("div", { class: "row", "aria-current": message.id === state.selected }, [
    el("button", { class: "body", onClick: () => openMessage(message) }, [
      el("strong", { class: message.unread ? "unread" : "" }, [title(message) + "  " + shortDate(message.at)]),
      el("em", {}, [(message.payment ? `${message.payment.amount} ${message.payment.asset} · ` : "") + message.subject]),
    ]),
    el("button", { "aria-label": message.starred ? "Unstar" : "Star", onClick: async () => { await api("/api/star", { id: message.id }); render(); } }, [message.starred ? "★" : "☆"]),
  ]));
  const head = el("div", { class: "list-head" }, [
    el("h2", {}, [state.box]),
    state.box === "trash" && items.length
      ? el("button", { class: "line", onClick: async () => { if (confirm("Delete everything in Trash forever?")) { await api("/api/trash/empty", {}); render(); } } }, ["Empty trash"])
      : el("span"),
  ]);
  return el("section", { class: "list" }, [head, el("div", { class: "rows" }, items.length ? items : [el("p", { class: "muted", style: "padding:16px" }, ["Nothing here."])])]);
}

function paymentCard(item) {
  const outgoing = item.from.email === state.data.me.email && !item.system;
  return el("div", { class: "payment" }, [
    el("div", { class: "e2e" }, ["FIRM WALLET"]),
    el("strong", {}, [`${item.system ? "" : outgoing ? "Sent " : "Received "}${item.payment.amount} ${item.payment.asset}`]),
    el("div", { class: "muted" }, [`Transaction ${item.payment.txId.slice(0, 15)}… · recorded on Firmament Network`]),
  ]);
}

function reader(message) {
  if (!message) {
    return el("section", { class: "reader" }, [el("div", { class: "sheet" }, [
      el("p", { class: "e2e" }, ["CUMULUS://MAIL"]),
      el("h2", {}, ["Private by default."]),
      el("p", { class: "muted" }, ["Encrypted at rest. End-to-end between Cumulus accounts. Never scanned for ads."]),
    ])]);
  }
  const thread = state.data.messages.filter((item) => item.threadId === message.threadId && item.folder !== "drafts" && (item.folder !== "trash" || message.folder === "trash")).sort((a, b) => a.at < b.at ? -1 : 1);
  const e2e = [message.from, ...message.to, ...message.cc].every((person) => person.email.endsWith("@cumulusmail.cloud"));
  const key = thread.map((item) => item.id).join(":");
  const brief = state.data.briefs?.[key];
  const inTrash = message.folder === "trash";
  return el("section", { class: "reader" }, [
    el("div", { class: "tools" }, [
      el("button", { onClick: () => { state.selected = null; document.body.classList.remove("reading"); render(); } }, ["Back"]),
      message.system ? el("span") : el("button", { onClick: () => reply(message) }, ["Reply"]),
      el("button", { onClick: async () => { await api("/api/move", { id: message.id, folder: "archive" }); state.selected = null; render(); } }, ["Archive"]),
      inTrash
        ? el("button", { onClick: async () => { if (confirm("Delete this message forever?")) { await api("/api/remove", { id: message.id }); state.selected = null; render(); } } }, ["Delete forever"])
        : el("button", { onClick: async () => { await api("/api/move", { id: message.id, folder: "trash" }); state.selected = null; render(); } }, ["Trash"]),
      el("button", { onClick: async () => { await api("/api/task", { title: message.subject, detail: "Queued from mail · waiting on you", messageId: message.id }); state.view = "tasks"; render(); } }, ["Conductor"]),
    ]),
    el("div", { class: "sheet" }, [
      el("h2", {}, [message.subject]),
      el("p", { class: e2e ? "e2e" : "muted" }, [e2e ? "End-to-end · Cumulus to Cumulus" : "Encrypted at rest · leaves Cumulus when it is delivered"]),
      ...(message.delivery || []).map((hop) => el("p", { class: "sub" }, [`${hop.email} · ${hop.detail}`])),
      brief ? el("p", {}, [brief]) : el("span"),
      message.system ? el("span") : el("button", { class: "e2e", onClick: () => briefThread(message, key) }, ["Brief with SCALVS"]),
      ...thread.map((item) => el("article", { class: "event" }, [
        el("div", {}, [
          el("strong", {}, [`${item.from.name} <${item.from.email}>`]),
          item.system ? el("span", { class: "badge" }, ["Cumulus account notice"]) : el("span"),
          item.payment ? paymentCard(item) : el("span"),
          el("p", { class: "letter" }, [item.body]),
          ...(item.attachments || []).map((file) => el("p", { class: "sub" }, [`${file.name} · ${Math.max(1, Math.round(file.size / 1024))} KB`])),
        ]),
      ])),
      state.composer?.inline ? composerForm() : el("span"),
    ]),
  ]);
}

function reply(message) {
  state.error = "";
  state.composer = {
    inline: true,
    threadId: message.threadId,
    to: message.from.email === state.data.me.email ? message.to.map((p) => p.email).join(", ") : message.from.email,
    cc: "",
    subject: message.subject.startsWith("Re:") ? message.subject : `Re: ${message.subject}`,
    body: `\n\n—\n${message.from.name} wrote:\n${message.body.split("\n").map((line) => `> ${line}`).join("\n")}`,
    attachments: [],
  };
  render();
}

async function briefThread(message, key) {
  try {
    const result = await api("/api/brief", { mode: "summary", threadId: message.threadId, subject: message.subject });
    state.data.briefs = { ...state.data.briefs, [key]: result.text };
    state.error = "";
  } catch (error) {
    state.error = error.message;
  }
  render();
}

function composerForm() {
  const form = state.composer;
  form.payment ??= { asset: "USDC", amount: "" };
  const field = (label, key) => el("label", {}, [label, el(key === "body" ? "textarea" : "input", { value: form[key], onInput: (event) => { form[key] = event.target.value; } })]);
  const asset = el("select", { "aria-label": "Asset", onChange: (event) => { form.payment.asset = event.target.value; } }, ["USDC", "FIRM"].map((a) => el("option", { value: a }, [a])));
  asset.value = form.payment.asset;
  return el("form", { class: "composer", onSubmit: async (event) => { event.preventDefault(); await send(); } }, [
    field("To", "to"), field("Cc", "cc"), field("Subject", "subject"), field("Message", "body"),
    ...(form.attachments || []).map((file, index) => el("p", { class: "sub", style: "padding:0 12px" }, [
      `${file.name} · ${Math.max(1, Math.round(file.size / 1024))} KB `,
      el("button", { type: "button", onClick: () => { form.attachments.splice(index, 1); render(); } }, ["Remove"]),
    ])),
    el("label", { class: "pay" }, [
      "Send money",
      el("input", { inputmode: "decimal", placeholder: "Optional · 0.00", value: form.payment.amount, onInput: (event) => { form.payment.amount = event.target.value.trim(); } }),
      asset,
    ]),
    el("div", { class: "actions" }, [
      el("button", { class: "compose", type: "submit" }, ["Send"]),
      el("button", { class: "line", type: "button", onClick: async () => { await api("/api/draft", form); state.composer = null; state.box = "drafts"; render(); } }, ["Save draft"]),
      el("button", { class: "line", type: "button", onClick: () => filePicker.click() }, ["Drive file"]),
      state.error ? el("span", { class: "error" }, [state.error]) : el("span"),
    ]),
    filePicker,
  ]);
}

const filePicker = el("input", { type: "file", style: "display:none", onChange: (event) => {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file || !state.composer) return;
  if (file.size > 25 * 1024 * 1024) { state.error = "Files stay under 25 MB. Larger ones belong in Drive."; render(); return; }
  state.composer.attachments = [...(state.composer.attachments || []), { name: file.name, size: file.size }];
  state.error = "";
  render();
} });

async function send() {
  const form = state.composer;
  const paying = Boolean(form.payment?.amount);
  if (paying && !confirm(`Send ${form.payment.amount} ${form.payment.asset} from your FIRM Wallet to ${form.to}?`)) return;
  try {
    const result = await api("/api/send", { ...form, payment: paying ? form.payment : undefined });
    state.composer = null;
    state.error = result.bounced?.length ? `Not delivered to ${result.bounced.join(", ")}` : "";
    state.box = "sent";
    state.view = "mail";
    state.selected = result.id;
  } catch (error) {
    state.error = error.message;
  }
  render();
}

function modal() {
  return el("div", { class: "modal-scrim", onClick: (event) => { if (event.target === event.currentTarget) { state.composer = null; render(); } } }, [
    el("div", { class: "modal", role: "dialog", "aria-label": "New message" }, [el("h2", { style: "padding:16px" }, ["New message"]), composerForm()]),
  ]);
}

/* ---------------- FIRM Wallet ---------------- */

async function loadWallet() {
  try {
    state.wallet = await api("/api/wallet");
  } catch (error) {
    state.error = error.message;
  }
  render();
}

function walletView() {
  const w = state.wallet;
  if (!w) return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("p", { class: "muted" }, ["Loading your FIRM Wallet…"])])]);
  const form = { to: "", asset: "USDC", amount: "", memo: "" };
  const input = (label, key, attrs = {}) => el("label", {}, [label, el("input", { ...attrs, onInput: (event) => { form[key] = event.target.value.trim(); } })]);
  const asset = el("select", { "aria-label": "Asset", onChange: (event) => { form.asset = event.target.value; } }, ["USDC", "FIRM"].map((a) => el("option", { value: a }, [a])));
  return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [
    el("p", { class: "e2e" }, ["FIRM WALLET · INCLUDED WITH YOUR CUMULUS://ID"]),
    el("h2", {}, [`${w.balances.USDC} USDC · ${w.balances.FIRM} FIRM`]),
    el("p", { class: "sub" }, [w.address]),
    el("form", { class: "composer", onSubmit: async (event) => {
      event.preventDefault();
      if (!form.to || !form.amount) { state.error = "Add who to pay and how much."; render(); return; }
      if (!confirm(`Send ${form.amount} ${form.asset} to ${form.to}?`)) return;
      try {
        await api("/api/wallet/pay", form);
        state.error = "";
        await loadWallet();
      } catch (error) {
        state.error = error.message;
        render();
      }
    } }, [
      input("To", "to", { placeholder: "name@cumulusmail.cloud or 0x…" }),
      el("label", { class: "pay" }, ["Amount", el("input", { inputmode: "decimal", placeholder: "0.00", onInput: (event) => { form.amount = event.target.value.trim(); } }), asset]),
      input("Note", "memo", { placeholder: "Optional" }),
      el("div", { class: "actions" }, [el("button", { class: "compose", type: "submit" }, ["Send"]), state.error ? el("span", { class: "error" }, [state.error]) : el("span")]),
    ]),
    el("h2", { class: "small-head" }, ["Activity"]),
    ...(w.history.length
      ? w.history.map((t) => el("article", { class: "event" }, [el("div", {}, [
          el("strong", {}, [`${t.direction === "in" ? "+" : "−"}${t.amount} ${t.asset}`]),
          el("div", { class: "muted" }, [`${t.direction === "in" ? "From" : "To"} ${t.counterparty}${t.memo ? " · " + t.memo : ""} · ${shortDate(t.at)}`]),
        ])]))
      : [el("p", { class: "muted" }, ["No activity yet. Money you send or receive by mail shows up here too."])]),
  ])]);
}

function aux(data) {
  if (state.view === "wallet") return walletView();
  if (state.view === "contacts") {
    return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("h2", {}, ["People"]), ...(data.contacts.length ? data.contacts.map((contact) => el("article", { class: "person" }, [
      el("div", {}, [el("strong", {}, [contact.name]), el("div", { class: "sub" }, [contact.email]), el("div", { class: "muted" }, [contact.note])]),
      el("button", { class: "line", onClick: () => { state.composer = { to: contact.email, cc: "", subject: "", body: "", attachments: [] }; state.view = "mail"; render(); } }, ["Write"]),
    ])) : [el("p", { class: "muted" }, ["No contacts yet."])])])]);
  }
  if (state.view === "tasks") {
    return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("h2", {}, ["Waiting on you"]), ...(data.tasks.length ? data.tasks.map((task) => el("button", { class: "task", onClick: async () => { await api("/api/task/toggle", { id: task.id }); render(); } }, [
      el("span", {}, [task.done ? "Done · " : "", task.title, el("div", { class: "muted" }, [task.detail])]),
    ])) : [el("p", { class: "muted" }, ["Nothing queued. Send a message to /CNDCTR/ from any thread."])])])]);
  }
  const a = data.account;
  return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [
    el("h2", {}, [data.me.name]),
    el("p", { class: "sub" }, [data.me.cid]),
    el("p", {}, [data.me.email]),
    ...data.aliases.map((alias) => el("p", { class: "sub" }, [alias])),
    el("button", { class: "line", onClick: async () => {
      const words = ["north", "quiet", "glass", "harbor", "lumen"];
      const pick = () => words[Math.floor(Math.random() * words.length)];
      await api("/api/alias", { alias: `${pick()}.${pick()}.${1000 + Math.floor(Math.random() * 9000)}@cumulusmail.cloud` });
      render();
    } }, ["Disposable alias"]),
    el("h2", { class: "small-head" }, [`${a.plan} plan · included with your Cumulus://ID`]),
    el("ul", { class: "plan" }, [
      el("li", {}, [`Cumulus://Mail · ${a.mailAddress}`]),
      el("li", {}, [`Cumulus://Cloud · ${a.storage.label} (Mail ${a.storage.mail}, Cloud ${a.storage.cloud})`]),
      el("li", {}, [`FIRM Wallet · ${a.wallet.address}`]),
      el("li", {}, ["SCALVS.ai and /CNDCTR/ free · METATRON dashboard"]),
    ]),
    el("p", { class: "muted" }, ["Every mailbox is encrypted at rest. Mail between Cumulus accounts is end-to-end. Never scanned for ads and never sold."]),
    el("button", { class: "line", onClick: async () => { await api("/api/auth/signout", {}); state.data = null; renderAuth(); } }, ["Sign out"]),
  ])]);
}

/* ---------------- Cumulus://ID sign-in (passkeys) ---------------- */

const b64uToBuf = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(s.length / 4) * 4, "=")), (c) => c.charCodeAt(0)).buffer;
const bufToB64u = (b) => btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

async function createPasskey(o) {
  const cred = await navigator.credentials.create({ publicKey: {
    challenge: b64uToBuf(o.challenge),
    rp: o.rp,
    user: { id: b64uToBuf(o.user.id), name: o.user.name, displayName: o.user.displayName },
    pubKeyCredParams: o.pubKeyCredParams,
    authenticatorSelection: o.authenticatorSelection,
    excludeCredentials: o.excludeCredentials.map((c) => ({ type: "public-key", id: b64uToBuf(c.id) })),
    attestation: "none",
    timeout: o.timeout,
  } });
  return { clientDataJSON: bufToB64u(cred.response.clientDataJSON), attestationObject: bufToB64u(cred.response.attestationObject) };
}

async function usePasskey(o) {
  const cred = await navigator.credentials.get({ publicKey: {
    challenge: b64uToBuf(o.challenge),
    rpId: o.rpId,
    allowCredentials: o.allowCredentials.map((c) => ({ type: "public-key", id: b64uToBuf(c.id) })),
    userVerification: o.userVerification,
    timeout: o.timeout,
  } });
  return {
    credentialId: bufToB64u(cred.rawId),
    clientDataJSON: bufToB64u(cred.response.clientDataJSON),
    authenticatorData: bufToB64u(cred.response.authenticatorData),
    signature: bufToB64u(cred.response.signature),
  };
}

async function finishSignIn(response) {
  const result = await api("/api/auth/signin/finish", { response });
  if (result.status === "mfa_required") {
    const code = prompt("Enter the 6-digit code from your authenticator app");
    if (!code) throw new Error("Sign-in needs your authenticator code.");
    await api("/api/auth/mfa", { mfaToken: result.mfaToken, code });
  }
  history.replaceState(null, "", "/");
  await boot();
}

function renderAuth(message = "") {
  document.body.classList.remove("reading");
  const root = document.querySelector("#app");
  const err = el("p", { class: "error", role: "alert" }, [message]);
  const fail = (e) => { err.textContent = e.name === "NotAllowedError" ? "Passkey request was cancelled." : e.message; };
  const username = el("input", { placeholder: "Choose a username", "aria-label": "Username", autocomplete: "username webauthn", autocapitalize: "none", spellcheck: "false" });
  const social = el("div", { class: "social" });
  api("/api/auth/providers")
    .then((providers) => social.replaceChildren(...providers.map((p) => el("a", { class: "line", href: `/api/auth/oauth/${encodeURIComponent(p.id)}/start` }, [`Continue with ${p.label}`]))))
    .catch(() => {});
  root.replaceChildren(el("div", { class: "auth" }, [el("div", { class: "auth-card" }, [
    el("div", { class: "mark" }, ["Cumulus", el("span", {}, ["://"]), "Mail"]),
    el("p", { class: "muted" }, ["Sign in with your Cumulus://ID"]),
    el("button", { class: "compose", onClick: async () => {
      err.textContent = "";
      try {
        const { options } = await api("/api/auth/signin", {});
        await finishSignIn(await usePasskey(options));
      } catch (e) { fail(e); }
    } }, ["Continue with passkey"]),
    social,
    el("p", { class: "muted divider" }, ["New here? Create a Cumulus://ID"]),
    username,
    el("button", { class: "line", onClick: async () => {
      err.textContent = "";
      const name = username.value.trim();
      if (!name) { err.textContent = "Choose a username first."; return; }
      try {
        const { cid, options } = await api("/api/auth/signup", { username: name });
        const done = await api("/api/auth/signup/finish", { cid, response: await createPasskey(options) });
        await finishSignIn(await usePasskey(done.options));
      } catch (e) { fail(e); }
    } }, ["Create account with passkey"]),
    err,
    el("p", { class: "muted small" }, ["Every Cumulus://ID comes with a @cumulusmail.cloud address, 15 GB shared with Cumulus://Cloud, and a FIRM Wallet. Free."]),
  ])]));
}

async function boot() {
  const [page, arg] = location.hash.replace(/^#\/?/, "").split("/");
  if (page === "signin-error") return renderAuth(decodeURIComponent(arg || ""));
  if (page === "mfa" && arg) {
    const code = prompt("Enter the 6-digit code from your authenticator app");
    history.replaceState(null, "", "/");
    if (code) {
      try { await api("/api/auth/mfa", { mfaToken: arg, code }); } catch (e) { return renderAuth(e.message); }
    }
  }
  try {
    state.data = await api("/api/state");
    render();
  } catch {
    // Not signed in: renderAuth() has already been shown.
  }
}

boot();
