const state = { view: "mail", box: "inbox", filter: "primary", query: "", selected: null, data: null, composer: null, error: "", wallet: null, clean: null, busy: "" };

const el = (tag, props = {}, children = []) => {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "class") node.className = value;
    else if (key === "value") node.value = value;
    else if (key === "style") node.style.cssText = value; // CSSOM, allowed by the page's CSP (style attributes are not)
    else if (key.startsWith("on")) node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key.startsWith("aria-")) node.setAttribute(key, String(value));
    else if (value === true) node.setAttribute(key, "");
    else if (value != null && value !== false) node.setAttribute(key, value);
  }
  for (const child of children) node.append(child?.nodeType ? child : document.createTextNode(child ?? ""));
  return node;
};
const none = () => document.createTextNode("");

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
  if (payload.clean) state.clean = payload.clean;
  return payload;
}

async function act(fn) {
  try {
    state.error = "";
    await fn();
  } catch (error) {
    state.error = error.message;
  }
  render();
}

/* ---------------- Mail ---------------- */

const UPDATES = new Set(["newsletter", "notification", "receipt", "bounce"]);

function inBox(message, box) {
  if (box === "starred") return message.starred && message.folder !== "trash";
  return message.folder === box;
}

function rows() {
  const messages = state.data.messages;
  const latest = new Map();
  for (const message of messages.filter((item) => inBox(item, state.box))) {
    if (state.box === "inbox" && state.filter === "primary" && message.kind !== "person") continue;
    if (state.box === "inbox" && state.filter === "updates" && !UPDATES.has(message.kind)) continue;
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

const mine = (message) => message.from.email === state.data.me.email;
const title = (message) => (mine(message) ? `To ${message.to[0]?.name || "someone"}` : message.from.name);
const size = (bytes) => (bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1048576).toFixed(1)} MB`);

function shortDate(iso) {
  const d = new Date(iso);
  const today = new Date();
  if (d.toDateString() === today.toDateString()) return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(d);
  return new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short" }).format(d);
}

const blankComposer = (over = {}) => ({ to: "", cc: "", subject: "", body: "", attachments: [], ...over });

async function openMessage(message) {
  if (message.folder === "drafts") {
    state.composer = blankComposer({ draftId: message.id, threadId: message.threadId, to: message.to.map((p) => p.email).join(", "), subject: message.subject, body: message.body, attachments: message.attachments || [] });
    render();
    return;
  }
  state.selected = message.id;
  state.composer = null;
  
  await act(() => api("/api/read", { threadId: message.threadId }));
}

function go(view) {
  state.view = view;
  state.error = "";
  state.selected = null;
  
  if (view === "wallet") loadWallet();
  if (view === "clean") loadClean();
  render();
}

function render() {
  const data = state.data;
  if (!data) return;
  const root = document.querySelector("#app");
  const scroll = root.querySelector(".rows")?.scrollTop ?? 0;
  root.replaceChildren();
  const selected = data.messages.find((message) => message.id === state.selected);
  const search = el("input", { placeholder: "Search mail", "aria-label": "Search mail", value: state.query, onInput: (event) => { state.query = event.target.value; render(); document.querySelector(".search input")?.focus(); } });
  const initial = (data.me.name || data.me.email).trim().charAt(0).toUpperCase();
  root.append(
    el("header", {}, [
      el("div", { class: "brand" }, [el("div", { class: "mark" }, ["Cumulus", el("span", {}, ["://"]), "Mail"])]),
      el("label", { class: "search" }, [el("span", { class: "glass", "aria-hidden": "true" }, ["⌕"]), search]),
      el("button", { class: "avatar", title: `${data.me.name} · ${data.me.email}`, "aria-label": "Your Cumulus://ID", onClick: () => go("account") }, [initial]),
    ]),
    el("div", { class: "layout" }, [
      sidebar(data),
      el("main", { class: "panel" }, [state.view === "mail" ? (selected ? reader(selected) : list()) : aux(data)]),
    ]),
  );
  const rowsNode = root.querySelector(".rows");
  if (rowsNode) rowsNode.scrollTop = scroll;
  if (state.query) { const input = root.querySelector(".search input"); input.setSelectionRange(input.value.length, input.value.length); }
  if (state.composer && !state.composer.inline) root.append(modal());
}

function sidebar(data) {
  const boxes = [["inbox", "Inbox"], ["starred", "Starred"], ["sent", "Sent"], ["drafts", "Drafts"], ["archive", "Archive"], ["trash", "Trash"]];
  const views = [["clean", "Clean"], ["wallet", "Wallet"], ["contacts", "People"], ["tasks", "Conductor"]];
  const navButton = (current, label, onClick, extra) => el("button", { "aria-current": current, onClick }, [label, extra ?? none()]);
  return el("nav", { "aria-label": "Mail" }, [
    el("button", { class: "compose", onClick: () => { state.composer = blankComposer(); state.error = ""; render(); } }, [el("span", { "aria-hidden": "true" }, ["✎"]), "Compose"]),
    ...boxes.map(([id, label]) => navButton(state.view === "mail" && state.box === id, label, () => { state.box = id; state.filter = "all"; go("mail"); },
      id === "inbox" && data.unread ? el("span", { class: "count" }, [String(data.unread)]) : null)),
    el("hr"),
    ...views.map(([id, label]) => navButton(state.view === id, label, () => go(id))),
    el("button", { class: "mobile-only", "aria-current": state.view === "account", onClick: () => go("account") }, ["Account"]),
    el("button", { class: "me", onClick: () => go("account") }, [
      el("div", { class: "meter" }, [el("span", { style: `width:${Math.max(1, data.quota.pct)}%` })]),
      el("div", { class: "sub" }, [`${data.quota.label} used`]),
    ]),
  ]);
}

function list() {
  const items = rows().map((message) => el("div", { class: `row${message.unread ? " unread" : ""}`, role: "button", tabindex: "0", onClick: () => openMessage(message), onKeydown: (e) => { if (e.key === "Enter") openMessage(message); } }, [
    el("button", { class: `star${message.starred ? " on" : ""}`, "aria-label": message.starred ? "Unstar" : "Star", onClick: (e) => { e.stopPropagation(); act(() => api("/api/star", { id: message.id })); } }, [message.starred ? "★" : "☆"]),
    el("span", { class: "who" }, [title(message)]),
    el("span", { class: "what" }, [
      message.payment ? el("span", { class: "tag" }, [`${message.payment.amount} ${message.payment.asset}`]) : none(),
      message.attachments?.length ? el("span", { class: "tag" }, ["File"]) : none(),
      el("b", {}, [message.subject || "(no subject)"]),
      el("span", { class: "snip" }, [` — ${message.body.replace(/\s+/g, " ").slice(0, 160)}`]),
    ]),
    el("span", { class: "when" }, [shortDate(message.at)]),
  ]));
  const unreadIn = (pred) => new Set(state.data.messages.filter((m) => m.folder === "inbox" && m.unread && pred(m)).map((m) => m.threadId)).size;
  const tabs = state.box === "inbox"
    ? el("div", { class: "tabs", role: "tablist" }, [
        ["primary", "Primary", unreadIn((m) => m.kind === "person")],
        ["updates", "Updates", unreadIn((m) => UPDATES.has(m.kind))],
        ["all", "All mail", 0],
      ].map(([id, label, n]) => el("button", { role: "tab", "aria-selected": state.filter === id, onClick: () => { state.filter = id; render(); } }, [label, n ? el("span", { class: "pill" }, [`${n} new`]) : none()])))
    : none();
  const head = el("div", { class: "list-head" }, [
    el("button", { class: "icon", "aria-label": "Refresh", title: "Refresh", onClick: () => act(async () => { state.data = await api("/api/state"); }) }, ["↻"]),
    el("span", { class: "spacer" }),
    state.box === "trash" && items.length
      ? el("button", { class: "quiet danger", onClick: () => { if (confirm("Delete everything in Trash forever?")) act(() => api("/api/trash/empty", {})); } }, ["Empty trash now"])
      : none(),
    el("span", { class: "sub" }, [items.length ? `1–${items.length} of ${items.length}` : ""]),
  ]);
  const empty = state.query ? "No mail matches that search." : state.box === "inbox" && state.filter === "primary" ? "No mail from people. You're all caught up." : "Nothing here.";
  return el("section", { class: "list", "aria-label": state.box }, [head, tabs, el("div", { class: "rows" }, items.length ? items : [el("p", { class: "empty" }, [empty])])]);
}

function paymentCard(item) {
  const outgoing = mine(item) && !item.system;
  return el("div", { class: "payment" }, [
    el("div", { class: "sub" }, ["FIRM Wallet"]),
    el("strong", {}, [`${item.system ? "" : outgoing ? "Sent " : "Received "}${item.payment.amount} ${item.payment.asset}`]),
    el("div", { class: "sub" }, [`${item.payment.txId.slice(0, 15)}… · recorded on Firmament`]),
  ]);
}

function reader(message) {
  const thread = state.data.messages.filter((item) => item.threadId === message.threadId && item.folder !== "drafts" && (item.folder !== "trash" || message.folder === "trash")).sort((a, b) => (a.at < b.at ? -1 : 1));
  const e2e = [message.from, ...message.to, ...message.cc].every((person) => person.email.endsWith("@cumulusmail.cloud"));
  const key = thread.map((item) => item.id).join(":");
  const brief = state.data.briefs?.[key];
  const inTrash = message.folder === "trash";
  const close = () => { state.selected = null;  };
  const move = (folder) => act(async () => { await api("/api/move", { id: message.id, folder }); close(); });
  return el("section", { class: "reader" }, [
    el("div", { class: "tools" }, [
      el("button", { class: "icon", "aria-label": `Back to ${state.box}`, title: `Back to ${state.box}`, onClick: () => { close(); render(); } }, ["←"]),
      message.system ? none() : el("button", { class: "quiet", onClick: () => reply(message) }, ["Reply"]),
      message.folder === "archive" ? el("button", { class: "quiet", onClick: () => move("inbox") }, ["Move to inbox"]) : el("button", { class: "quiet", onClick: () => move("archive") }, ["Archive"]),
      inTrash
        ? el("button", { class: "quiet danger", onClick: () => { if (confirm("Delete this message forever?")) act(async () => { await api("/api/remove", { id: message.id }); close(); }); } }, ["Delete forever"])
        : el("button", { class: "quiet", onClick: () => move("trash") }, ["Trash"]),
      message.system || mine(message) ? none() : el("button", { class: "quiet", onClick: () => { if (confirm(`Send future mail from ${message.from.email} to Archive?`)) act(async () => { await api("/api/clean/mute", { email: message.from.email }); close(); }); } }, ["Mute sender"]),
      el("button", { class: "quiet", onClick: () => act(async () => { await api("/api/task", { title: message.subject, detail: "Queued from mail · waiting on you", messageId: message.id }); state.view = "tasks"; }) }, ["Conductor"]),
      message.system ? none() : el("button", { class: "quiet", onClick: () => briefThread(message, key) }, [state.busy === key ? "Briefing…" : "Brief with SCALVS"]),
    ]),
    el("div", { class: "sheet" }, [
      el("h1", {}, [message.subject || "(no subject)"]),
      el("div", { class: "meta" }, [
        el("span", { class: e2e ? "lock" : "" }, [e2e ? "End-to-end · Cumulus to Cumulus" : "Encrypted at rest · leaves Cumulus when delivered"]),
        ...(message.delivery || []).map((hop) => el("span", { class: hop.status === "bounced" ? "danger" : "" }, [`${hop.email} · ${hop.status === "inside" ? "delivered" : hop.status === "bounced" ? "not delivered" : "sent out"}`])),
      ]),
      brief ? el("div", { class: "brief" }, [brief]) : none(),
      state.error ? el("p", { class: "error", role: "alert" }, [state.error]) : none(),
      ...thread.map((item) => el("article", { class: "msg" }, [
        el("div", { class: "from" }, [
          el("div", {}, [el("strong", {}, [item.from.name]), el("span", { class: "sub" }, [`  ${item.from.email}`]), item.system ? el("span", { class: "tag" }, ["Account notice"]) : none()]),
          el("span", { class: "sub" }, [shortDate(item.at)]),
        ]),
        item.payment ? paymentCard(item) : none(),
        el("p", { class: "letter" }, [item.body]),
        ...(item.attachments || []).map((file) => el("span", { class: "file" }, [file.name, el("span", { class: "sub" }, [size(file.size)])])),
      ])),
      state.composer?.inline ? el("div", { class: "inline-composer" }, [composerForm()]) : none(),
    ]),
  ]);
}

function reply(message) {
  state.error = "";
  state.composer = blankComposer({
    inline: true,
    threadId: message.threadId,
    to: mine(message) ? message.to.map((p) => p.email).join(", ") : message.from.email,
    subject: message.subject.startsWith("Re:") ? message.subject : `Re: ${message.subject}`,
    body: `\n\n—\n${message.from.name} wrote:\n${message.body.split("\n").map((line) => `> ${line}`).join("\n")}`,
  });
  render();
  document.querySelector(".inline-composer textarea")?.focus();
}

async function briefThread(message, key) {
  state.busy = key;
  render();
  await act(async () => {
    const result = await api("/api/brief", { mode: "summary", threadId: message.threadId, subject: message.subject });
    state.data.briefs = { ...state.data.briefs, [key]: result.text };
  });
  state.busy = "";
  render();
}

function composerForm() {
  const form = state.composer;
  form.payment ??= { asset: "USDC", amount: "" };
  const field = (label, key, attrs = {}) => el("label", { class: `field${key === "body" ? " body" : ""}` }, [
    key === "body" ? none() : el("span", {}, [label]),
    el(key === "body" ? "textarea" : "input", { "aria-label": label, value: form[key], ...attrs, onInput: (event) => { form[key] = event.target.value; } }),
  ]);
  const asset = el("select", { "aria-label": "Asset", onChange: (event) => { form.payment.asset = event.target.value; } }, ["USDC", "FIRM"].map((a) => el("option", { value: a }, [a])));
  asset.value = form.payment.asset;
  return el("form", { class: "composer", onSubmit: async (event) => { event.preventDefault(); await send(); } }, [
    field("To", "to", { autocomplete: "email" }),
    form.inline ? none() : field("Cc", "cc"),
    form.inline ? none() : field("Subject", "subject"),
    el("label", { class: "field" }, [
      el("span", {}, ["Pay"]),
      el("input", { inputmode: "decimal", placeholder: "Optional", "aria-label": "Amount to send", value: form.payment.amount, onInput: (event) => { form.payment.amount = event.target.value.trim(); } }),
      asset,
    ]),
    field("Message", "body"),
    ...(form.attachments || []).map((file, index) => el("span", { class: "file" }, [
      file.name, el("span", { class: "sub" }, [size(file.size)]),
      el("button", { type: "button", "aria-label": `Remove ${file.name}`, onClick: () => { form.attachments.splice(index, 1); render(); } }, ["×"]),
    ])),
    el("div", { class: "actions" }, [
      el("button", { class: "primary", type: "submit" }, ["Send"]),
      el("button", { class: "quiet", type: "button", onClick: () => filePicker.click() }, ["Attach"]),
      el("button", { class: "quiet", type: "button", onClick: () => act(async () => { await api("/api/draft", form); state.composer = null; state.box = "drafts"; state.view = "mail"; }) }, ["Save draft"]),
      el("span", { class: "spacer" }),
      form.inline ? el("button", { class: "quiet", type: "button", onClick: () => { state.composer = null; render(); } }, ["Discard"]) : none(),
      state.error ? el("span", { class: "error", role: "alert" }, [state.error]) : none(),
    ]),
    filePicker,
  ]);
}

const filePicker = el("input", { type: "file", style: "display:none", onChange: (event) => {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file || !state.composer) return;
  if (file.size > 25 * 1024 * 1024) { state.error = "Files stay under 25 MB. Larger ones belong in Cloud."; render(); return; }
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
  return el("div", { class: "scrim", onClick: (event) => { if (event.target === event.currentTarget) { state.composer = null; render(); } } }, [
    el("div", { class: "dialog", role: "dialog", "aria-label": "New message" }, [
      el("div", { class: "dialog-head" }, [el("h2", {}, [state.composer.draftId ? "Draft" : "New message"]), el("button", { class: "quiet", "aria-label": "Close", onClick: () => { state.composer = null; render(); } }, ["×"])]),
      composerForm(),
    ]),
  ]);
}

/* ---------------- SCALVS Clean ---------------- */

async function loadClean() {
  await act(async () => { state.clean = await api("/api/clean"); });
}

const DAY_CHOICES = [null, 1, 3, 7, 14, 30, 90];
const dayLabel = (d) => (d === null ? "Off" : d === 1 ? "After 1 day" : `After ${d} days`);
const ago = (iso) => {
  const mins = Math.round((Date.now() - Date.parse(iso)) / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} min ago`;
  if (mins < 1440) return `${Math.round(mins / 60)} h ago`;
  return shortDate(iso);
};

function cleanView() {
  const c = state.clean;
  if (!c) return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("p", { class: "muted" }, ["Loading…"])])]);
  const waiting = c.preview.reduce((n, a) => n + a.count, 0);
  const rule = (label, hint, key) => {
    const select = el("select", { "aria-label": label, onChange: (e) => act(() => api("/api/clean/rules", { [key]: e.target.value === "off" ? null : Number(e.target.value) })) },
      DAY_CHOICES.map((d) => el("option", { value: d === null ? "off" : String(d) }, [dayLabel(d)])));
    select.value = c.rules[key] === null ? "off" : String(c.rules[key]);
    if (!DAY_CHOICES.includes(c.rules[key])) select.prepend(el("option", { value: String(c.rules[key]), selected: true }, [dayLabel(c.rules[key])]));
    return el("div", { class: "rule" }, [el("div", {}, [el("div", {}, [label]), el("div", { class: "sub" }, [hint])]), select]);
  };
  const by = { rules: "Automatic", scalvs: "SCALVS", you: "You" };
  return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [
    el("h1", {}, [waiting ? `${waiting} message${waiting === 1 ? "" : "s"} ready to clean` : "Your inbox is clean"]),
    el("p", { class: "muted" }, [c.rules.auto ? `SCALVS cleans on its own${c.lastRun ? ` · last run ${ago(c.lastRun)}` : ""}. Everything it moves can be undone.` : "Automatic cleaning is off."]),
    el("div", { class: "actions" }, [
      el("button", { class: "primary", disabled: !waiting, onClick: () => act(() => api("/api/clean/run", {})) }, ["Clean now"]),
      el("button", { class: "outline", onClick: async () => { state.busy = "scalvs"; render(); await act(() => api("/api/clean/scalvs", {})); state.busy = ""; render(); } }, [state.busy === "scalvs" ? "SCALVS is reading…" : "Ask SCALVS"]),
    ]),
    state.error ? el("p", { class: "error", role: "alert" }, [state.error]) : none(),
    ...c.preview.map((a) => el("p", { class: "sub" }, [`${a.kind === "archive" ? "Archive" : a.kind === "trash" ? "Trash" : "Delete forever"} · ${a.reason}`])),

    el("div", { class: "stat-row" }, [
      ["person", "From people"], ["newsletter", "Newsletters"], ["notification", "Notices"], ["receipt", "Receipts"],
    ].map(([k, label]) => el("div", { class: "stat" }, [el("b", {}, [String(c.mix[k] ?? 0)]), el("span", {}, [label])]))),

    ...(c.suggestions.length || c.note ? [el("h3", {}, ["Suggestions"])] : []),
    c.note ? el("p", { class: "muted" }, [c.note]) : none(),
    ...c.suggestions.map((s) => el("div", { class: "suggest" }, [
      el("div", {}, [
        el("div", { class: "by" }, [s.source === "scalvs" ? "SCALVS" : "Rules"]),
        el("div", {}, [s.reason]),
        el("div", { class: "sub" }, [s.kind === "mute" ? `Mute ${s.email}` : `${s.kind === "trash" ? "Move to Trash" : "Archive"} · ${s.count} message${s.count === 1 ? "" : "s"}`]),
      ]),
      el("div", { class: "btns" }, [
        el("button", { class: "primary", onClick: () => act(() => api("/api/clean/approve", { id: s.id })) }, ["Approve"]),
        el("button", { class: "quiet", onClick: () => act(() => api("/api/clean/dismiss", { id: s.id })) }, ["Dismiss"]),
      ]),
    ])),

    el("h3", {}, ["Rules"]),
    el("div", { class: "rule" }, [
      el("div", {}, [el("div", {}, ["Clean automatically"]), el("div", { class: "sub" }, ["Runs when you open Mail, at most once an hour."])]),
      el("button", { class: "switch", role: "switch", "aria-checked": String(c.rules.auto), "aria-label": "Clean automatically", onClick: () => act(() => api("/api/clean/rules", { auto: !c.rules.auto })) }),
    ]),
    rule("Archive read newsletters", "Promotions, digests and mailing lists.", "newslettersAfterDays"),
    rule("Archive read notices", "Cloud shares and account notices you've seen. Security notices stay.", "notificationsAfterDays"),
    rule("Trash delivery failures", "Bounce notices from Mail Delivery.", "bouncesAfterDays"),
    rule("Empty Trash", "Deletes forever. This one can't be undone.", "emptyTrashAfterDays"),
    el("p", { class: "sub" }, ["Never moved automatically: starred mail, unread mail from people, payments, security notices, drafts and sent mail."]),

    ...(c.senders.length || c.rules.muted.length ? [el("h3", {}, ["Senders"])] : []),
    ...c.senders.map((s) => el("div", { class: "item" }, [
      el("div", {}, [el("div", {}, [s.name]), el("div", { class: "sub" }, [`${s.email} · ${s.count} message${s.count === 1 ? "" : "s"}`])]),
      el("button", { class: "outline", onClick: () => act(() => api("/api/clean/mute", { email: s.email })) }, ["Mute"]),
    ])),
    ...c.rules.muted.map((email) => el("div", { class: "item" }, [
      el("div", {}, [el("div", {}, [email]), el("div", { class: "sub" }, ["Muted · new mail goes to Archive"])]),
      el("button", { class: "quiet", onClick: () => act(() => api("/api/clean/unmute", { email })) }, ["Unmute"]),
    ])),

    ...(c.log.length ? [el("h3", {}, ["Activity"])] : []),
    ...c.log.map((e) => el("div", { class: "item" }, [
      el("div", {}, [el("div", {}, [e.reason]), el("div", { class: "sub" }, [`${by[e.by]} · ${e.kind === "archive" ? "archived" : e.kind === "trash" ? "trashed" : "deleted"} ${e.count} · ${ago(e.at)}${e.undone ? " · undone" : ""}`])]),
      e.kind !== "delete" && !e.undone ? el("button", { class: "quiet", onClick: () => act(() => api("/api/clean/undo", { id: e.id })) }, ["Undo"]) : none(),
    ])),
  ])]);
}

/* ---------------- FIRM Wallet ---------------- */

async function loadWallet() {
  await act(async () => { state.wallet = await api("/api/wallet"); });
}

function walletView() {
  const w = state.wallet;
  if (!w) return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("p", { class: "muted" }, ["Loading your FIRM Wallet…"])])]);
  const form = { to: "", asset: "USDC", amount: "", memo: "" };
  const input = (label, key, attrs = {}) => el("label", { class: "field" }, [el("span", {}, [label]), el("input", { "aria-label": label, ...attrs, onInput: (event) => { form[key] = event.target.value.trim(); } })]);
  const asset = el("select", { "aria-label": "Asset", onChange: (event) => { form.asset = event.target.value; } }, ["USDC", "FIRM"].map((a) => el("option", { value: a }, [a])));
  return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [
    el("p", { class: "sub" }, ["FIRM Wallet · included with your Cumulus://ID"]),
    el("div", { class: "stat-row" }, [
      el("div", { class: "stat" }, [el("b", { class: "big" }, [w.balances.USDC]), el("span", {}, ["USDC"])]),
      el("div", { class: "stat" }, [el("b", { class: "big" }, [w.balances.FIRM]), el("span", {}, ["FIRM"])]),
    ]),
    el("p", { class: "mono muted" }, [w.address]),
    el("form", { class: "wallet-form", onSubmit: (event) => {
      event.preventDefault();
      if (!form.to || !form.amount) { state.error = "Add who to pay and how much."; render(); return; }
      if (!confirm(`Send ${form.amount} ${form.asset} to ${form.to}?`)) return;
      act(async () => { await api("/api/wallet/pay", form); state.wallet = await api("/api/wallet"); });
    } }, [
      input("To", "to", { placeholder: "name@cumulusmail.cloud or 0x…" }),
      el("label", { class: "field" }, [el("span", {}, ["Amount"]), el("input", { "aria-label": "Amount", inputmode: "decimal", placeholder: "0.00", onInput: (event) => { form.amount = event.target.value.trim(); } }), asset]),
      input("Note", "memo", { placeholder: "Optional" }),
      el("div", { class: "actions" }, [el("button", { class: "primary", type: "submit" }, ["Send"]), state.error ? el("span", { class: "error", role: "alert" }, [state.error]) : none()]),
    ]),
    el("h3", {}, ["Activity"]),
    ...(w.history.length
      ? w.history.map((t) => el("div", { class: "item" }, [
          el("div", {}, [el("div", {}, [`${t.direction === "in" ? "From" : "To"} ${t.counterparty}`]), el("div", { class: "sub" }, [`${t.memo ? `${t.memo} · ` : ""}${shortDate(t.at)}`])]),
          el("strong", { class: t.direction === "in" ? "lock" : "" }, [`${t.direction === "in" ? "+" : "−"}${t.amount} ${t.asset}`]),
        ]))
      : [el("p", { class: "muted" }, ["No activity yet. Money you send or receive by mail shows up here too."])]),
  ])]);
}

function aux(data) {
  if (state.view === "wallet") return walletView();
  if (state.view === "clean") return cleanView();
  if (state.view === "contacts") {
    return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("h1", {}, ["People"]), ...(data.contacts.length ? data.contacts.map((contact) => el("div", { class: "item" }, [
      el("div", {}, [el("div", {}, [contact.name]), el("div", { class: "sub" }, [contact.email]), contact.note ? el("div", { class: "sub" }, [contact.note]) : none()]),
      el("button", { class: "outline", onClick: () => { state.composer = blankComposer({ to: contact.email }); state.view = "mail"; render(); } }, ["Write"]),
    ])) : [el("p", { class: "muted" }, ["No contacts yet."])])])]);
  }
  if (state.view === "tasks") {
    return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("h1", {}, ["Waiting on you"]), ...(data.tasks.length ? data.tasks.map((task) => el("div", { class: "item" }, [
      el("div", {}, [el("div", { class: task.done ? "muted" : "" }, [task.title]), el("div", { class: "sub" }, [task.detail])]),
      el("button", { class: "quiet", onClick: () => act(() => api("/api/task/toggle", { id: task.id })) }, [task.done ? "Reopen" : "Done"]),
    ])) : [el("p", { class: "muted" }, ["Nothing queued. Send any thread to Conductor from the message toolbar."])])])]);
  }
  const a = data.account;
  return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [
    el("h1", {}, [data.me.name]),
    el("p", { class: "sub" }, ["Your Cumulus://ID"]),
    el("p", { class: "big" }, [data.me.email]),
    el("p", { class: "muted" }, ["One address for every Infinitum product. Sign in to +SPCTR, Cloud and the FIRM Wallet with it."]),
    el("h3", {}, ["Aliases"]),
    ...(data.aliases.length ? data.aliases.map((alias) => el("p", { class: "mono" }, [alias])) : [el("p", { class: "muted" }, ["No aliases yet."])]),
    el("button", { class: "outline", onClick: () => act(async () => {
      const words = ["north", "quiet", "glass", "harbor", "lumen"];
      const pick = () => words[Math.floor(Math.random() * words.length)];
      await api("/api/alias", { alias: `${pick()}.${pick()}.${1000 + Math.floor(Math.random() * 9000)}@cumulusmail.cloud` });
    }) }, ["New disposable alias"]),
    el("h3", {}, [`${a.plan} plan · included with your Cumulus://ID`]),
    el("ul", { class: "plan" }, [
      el("li", {}, [`Mail · ${a.mailAddress}`]),
      el("li", {}, [`Cloud · ${a.storage.label} (Mail ${a.storage.mail}, Cloud ${a.storage.cloud})`]),
      el("li", {}, ["FIRM Wallet"]),
      el("li", {}, ["SCALVS Clean, SCALVS.ai and /CNDCTR/"]),
    ]),
    el("p", { class: "muted" }, ["Encrypted at rest. End-to-end between Cumulus accounts. Never scanned for ads and never sold."]),
    el("div", { class: "actions" }, [el("button", { class: "outline", onClick: async () => { await api("/api/auth/signout", {}); state.data = null; renderAuth(); } }, ["Sign out"])]),
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
  
  const root = document.querySelector("#app");
  const err = el("p", { class: "error", role: "alert" }, [message]);
  const fail = (e) => { err.textContent = e.name === "NotAllowedError" ? "Passkey request was cancelled." : e.message; };
  const username = el("input", { placeholder: "name@cumulusmail.cloud", "aria-label": "Your new Cumulus://ID", autocomplete: "username webauthn", autocapitalize: "none", spellcheck: "false" });
  const social = el("div", { class: "social" });
  api("/api/auth/providers")
    .then((providers) => social.replaceChildren(...providers.map((p) => el("a", { class: "outline", href: `/api/auth/oauth/${encodeURIComponent(p.id)}/start` }, [`Continue with ${p.label}`]))))
    .catch(() => {});
  root.replaceChildren(el("div", { class: "auth" }, [el("div", { class: "auth-card" }, [
    el("div", { class: "mark" }, ["Cumulus", el("span", {}, ["://"]), "Mail"]),
    el("p", { class: "muted" }, ["Sign in with your Cumulus://ID"]),
    el("button", { class: "primary", onClick: async () => {
      err.textContent = "";
      try {
        const { options } = await api("/api/auth/signin", {});
        await finishSignIn(await usePasskey(options));
      } catch (e) { fail(e); }
    } }, ["Continue with passkey"]),
    social,
    el("p", { class: "muted divider" }, ["New here? Pick your address. It's your Cumulus://ID."]),
    username,
    el("button", { class: "outline", onClick: async () => {
      err.textContent = "";
      const name = username.value.trim();
      if (!name) { err.textContent = "Pick your address first."; return; }
      try {
        const { cid, options } = await api("/api/auth/signup", { username: name });
        const done = await api("/api/auth/signup/finish", { cid, response: await createPasskey(options) });
        await finishSignIn(await usePasskey(done.options));
      } catch (e) { fail(e); }
    } }, ["Create my Cumulus://ID"]),
    err,
    el("p", { class: "muted small" }, ["Your @cumulusmail.cloud address is your Cumulus://ID. It comes with 15 GB shared with Cumulus://Cloud and a FIRM Wallet. Free."]),
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
