const state = { view: "mail", box: "inbox", query: "", selected: null, data: null, composer: null, error: "" };

const el = (tag, props = {}, children = []) => {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "class") node.className = value;
    else if (key === "value") node.value = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value != null) node.setAttribute(key, value);
  }
  for (const child of children) node.append(child?.nodeType ? child : document.createTextNode(child ?? ""));
  return node;
};

async function api(path, body) {
  const response = await fetch(path, body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : undefined);
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error || "Request failed");
  if (payload.state) state.data = { ...state.data, ...payload.state, briefs: state.data?.briefs ?? {} };
  else if (payload.messages) state.data = { ...state.data, ...payload, briefs: state.data?.briefs ?? payload.briefs ?? {} };
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
  const root = document.querySelector("#app");
  root.replaceChildren();
  const selected = data.messages.find((message) => message.id === state.selected);
  root.append(
    el("header", {}, [
      el("div", {}, [el("div", { class: "mark" }, ["Cumulus", el("span", {}, ["://"]), "Mail"]), el("div", { class: "sub" }, [data.me.email])]),
      el("label", {}, [el("input", { placeholder: "Search mail", value: state.query, onInput: (event) => { state.query = event.target.value; render(); } })]),
      el("button", { class: "compose", onClick: () => { state.composer = { to: "", cc: "", subject: "", body: "", attachments: [] }; render(); } }, ["Compose"]),
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
  const views = [["mail", "Mail"], ["calendar", "Calendar"], ["contacts", "Contacts"], ["tasks", "Conductor"]];
  const boxes = [["inbox", "Inbox"], ["starred", "Starred"], ["sent", "Sent"], ["drafts", "Drafts"], ["archive", "Archive"], ["trash", "Trash"]];
  const nav = el("nav", {}, [
    ...views.map(([id, label]) => el("button", { "aria-current": state.view === id, onClick: () => { state.view = id; document.body.classList.remove("reading"); render(); } }, [label])),
    ...(state.view === "mail" ? boxes.map(([id, label]) => el("button", { "aria-current": state.box === id, onClick: () => { state.box = id; state.selected = null; document.body.classList.remove("reading"); render(); } }, [label])) : []),
    el("button", { class: "account", onClick: () => { state.view = "account"; render(); } }, [
      data.me.name,
      el("div", { class: "sub" }, [data.me.email]),
      el("div", { class: "bar" }, [el("span", { style: `width:${data.quota.pct}%` })]),
      el("div", { class: "muted" }, [data.quota.label + " shared with Drive"]),
    ]),
  ]);
  return nav;
}

function list() {
  const items = rows().map((message) => el("div", { class: "row", "aria-current": message.id === state.selected }, [
    el("button", { class: "body", onClick: () => openMessage(message) }, [
      el("strong", {}, [title(message) + "  " + shortDate(message.at)]),
      el("em", {}, [message.subject]),
    ]),
    el("button", { onClick: async () => { await api("/api/star", { id: message.id }); render(); } }, [message.starred ? "★" : "☆"]),
  ]));
  return el("section", { class: "list" }, [el("h2", { style: "padding:12px 16px;font-size:18px" }, [state.box]), el("div", { class: "rows" }, items.length ? items : [el("p", { class: "muted", style: "padding:16px" }, ["Nothing here."])])]);
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
  return el("section", { class: "reader" }, [
    el("div", { class: "tools" }, [
      el("button", { onClick: () => { state.selected = null; document.body.classList.remove("reading"); render(); } }, ["Back"]),
      el("button", { onClick: () => reply(message) }, ["Reply"]),
      el("button", { onClick: async () => { await api("/api/move", { id: message.id, folder: "archive" }); state.selected = null; render(); } }, ["Archive"]),
      el("button", { onClick: async () => { await api("/api/move", { id: message.id, folder: "trash" }); state.selected = null; render(); } }, ["Trash"]),
      el("button", { onClick: async () => { await api("/api/task", { title: message.subject, detail: "Queued from mail · waiting on you", messageId: message.id }); state.view = "tasks"; render(); } }, ["Conductor"]),
    ]),
    el("div", { class: "sheet" }, [
      el("h2", {}, [message.subject]),
      el("p", { class: e2e ? "e2e" : "muted" }, [e2e ? "End-to-end · Cumulus to Cumulus" : "Encrypted at rest · leaves Cumulus when it is delivered"]),
      ...(message.delivery || []).map((hop) => el("p", { class: "sub" }, [`${hop.email} · ${hop.detail}`])),
      brief ? el("p", {}, [brief]) : el("span"),
      el("button", { class: "e2e", onClick: () => briefThread(message, thread, key) }, ["Brief with SCALVS"]),
      ...thread.map((item) => el("article", { class: "event" }, [
        el("div", {}, [
          el("strong", {}, [`${item.from.name} <${item.from.email}>`]),
          el("p", { class: "letter" }, [item.body]),
          ...(item.attachments || []).map((file) => el("p", { class: "sub" }, [`${file.name} · ${Math.max(1, Math.round(file.size / 1024))} KB`])),
        ]),
      ])),
      state.composer?.inline ? composerForm() : el("span"),
    ]),
  ]);
}

function reply(message) {
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

async function briefThread(message, thread, key) {
  try {
    const result = await api("/api/brief", { mode: "summary", threadId: message.threadId, subject: message.subject });
    state.data.briefs[key] = result.text;
    state.error = "";
  } catch (error) {
    state.error = error.message;
  }
  render();
}

function composerForm() {
  const form = state.composer;
  const field = (label, key) => el("label", {}, [label, el(key === "body" ? "textarea" : "input", { value: form[key], onInput: (event) => { form[key] = event.target.value; } })]);
  return el("form", { class: "composer", onSubmit: async (event) => { event.preventDefault(); await send(); } }, [
    field("To", "to"), field("Cc", "cc"), field("Subject", "subject"), field("Message", "body"),
    ...(form.attachments || []).map((file, index) => el("p", { class: "sub", style: "padding:0 12px" }, [
      `${file.name} · ${Math.max(1, Math.round(file.size / 1024))} KB `,
      el("button", { type: "button", onClick: () => { form.attachments.splice(index, 1); render(); } }, ["Remove"]),
    ])),
    el("div", { class: "actions" }, [
      el("button", { class: "compose", type: "submit" }, ["Send"]),
      el("button", { class: "line", type: "button", onClick: async () => { await api("/api/draft", form); state.composer = null; state.box = "drafts"; render(); } }, ["Save draft"]),
      el("button", { class: "line", type: "button", onClick: () => filePicker.click() }, ["Drive file"]),
      state.error ? el("span", {}, [state.error]) : el("span"),
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
  try {
    const result = await api("/api/send", state.composer);
    state.composer = null;
    state.error = "";
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
    el("div", { class: "modal", role: "dialog" }, [el("h2", { style: "padding:16px" }, ["New message"]), composerForm()]),
  ]);
}

function aux(data) {
  if (state.view === "calendar") {
    return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("h2", {}, ["Coming up"]), ...data.events.map((event) => el("article", { class: "event" }, [el("div", {}, [el("div", { class: "e2e" }, [event.whenLabel]), el("strong", {}, [event.title]), el("div", { class: "muted" }, [event.with + " · " + event.where])])]))])]);
  }
  if (state.view === "contacts") {
    return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("h2", {}, ["People"]), ...data.contacts.map((contact) => el("article", { class: "person" }, [
      el("div", {}, [el("strong", {}, [contact.name]), el("div", { class: "sub" }, [contact.email]), el("div", { class: "muted" }, [contact.note])]),
      el("button", { class: "line", onClick: () => { state.composer = { to: contact.email, cc: "", subject: "", body: "", attachments: [] }; state.view = "mail"; render(); } }, ["Write"]),
    ]))])]);
  }
  if (state.view === "tasks") {
    return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [el("h2", {}, ["Waiting on you"]), ...data.tasks.map((task) => el("button", { class: "task", onClick: async () => { await api("/api/task/toggle", { id: task.id }); render(); } }, [
      el("span", {}, [task.done ? "Done · " : "", task.title, el("div", { class: "muted" }, [task.detail])]),
    ]))])]);
  }
  return el("section", { class: "pane" }, [el("div", { class: "sheet" }, [
    el("h2", {}, [data.me.name]),
    el("p", { class: "sub" }, [data.me.cid]),
    el("p", {}, [data.me.email]),
    ...data.aliases.map((alias) => el("p", { class: "sub" }, [alias])),
    el("button", { class: "line", onClick: async () => {
      const words = ["north", "quiet", "glass", "harbor", "lumen"];
      const alias = `${words[Math.floor(Math.random() * words.length)]}.${words[Math.floor(Math.random() * words.length)]}.${1000 + Math.floor(Math.random() * 9000)}@cumulusmail.cloud`;
      await api("/api/alias", { alias });
      render();
    } }, ["Disposable alias"]),
    el("p", { class: "muted" }, ["Every mailbox is encrypted at rest. Mail between Cumulus accounts is end-to-end. Never scanned for ads and never sold."]),
  ])]);
}

const loaded = await api("/api/state");
state.data = loaded;
render();
