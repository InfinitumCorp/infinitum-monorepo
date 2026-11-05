import { EVENTS, ME, SEED_CONTACTS, SEED_MESSAGES, SEED_TASKS } from "./model.ts";
import type { ConductorTask, Contact, FolderId, MailMessage } from "./model.ts";
import { isCumulus, parsePeople, quota, validEmail } from "./logic.ts";

export interface MailSnapshot {
  messages: MailMessage[];
  aliases: string[];
  tasks: ConductorTask[];
  contacts: Contact[];
}

export interface SendInput {
  to: string;
  cc?: string;
  subject: string;
  body: string;
  threadId?: string;
  draftId?: string;
  now?: string;
}

export class Mailbox {
  messages: MailMessage[];
  aliases: string[];
  tasks: ConductorTask[];
  contacts: Contact[];

  constructor(seed: Partial<MailSnapshot> = {}) {
    this.messages = structuredClone(seed.messages ?? SEED_MESSAGES);
    this.aliases = structuredClone(seed.aliases ?? ["press@cumulusmail.cloud"]);
    this.tasks = structuredClone(seed.tasks ?? SEED_TASKS);
    this.contacts = structuredClone(seed.contacts ?? SEED_CONTACTS);
  }

  view() {
    return {
      me: ME,
      messages: this.messages,
      aliases: this.aliases,
      tasks: this.tasks,
      contacts: this.contacts,
      events: EVENTS,
      quota: quota(this.messages),
    };
  }

  markThreadRead(threadId: string) {
    for (const message of this.messages) {
      if (message.threadId === threadId) message.unread = false;
    }
  }

  toggleStar(id: string) {
    const message = this.messages.find((item) => item.id === id);
    if (message) message.starred = !message.starred;
  }

  move(id: string, folder: FolderId) {
    const message = this.messages.find((item) => item.id === id);
    if (!message) return;
    message.folder = folder;
    message.unread = false;
  }

  remove(id: string) {
    this.messages = this.messages.filter((item) => item.id !== id);
  }

  send(input: SendInput): { ok: true; id: string } | { ok: false; error: string } {
    const to = parsePeople(input.to);
    const cc = parsePeople(input.cc ?? "");
    if (to.length === 0 || to.some((person) => !validEmail(person.email))) {
      return { ok: false, error: "Add a real address in To." };
    }
    if (cc.some((person) => !validEmail(person.email))) {
      return { ok: false, error: "Check the Cc addresses." };
    }
    if (!input.subject.trim() && !input.body.trim()) {
      return { ok: false, error: "Write a subject or a message." };
    }
    const id = `m-${(input.now ?? Date.now().toString(36)).replace(/[^\w-]/g, "").slice(-12)}`;
    if (input.draftId) this.remove(input.draftId);
    this.messages.unshift({
      id,
      threadId: input.threadId ?? id,
      folder: "sent",
      from: { name: ME.name, email: ME.email },
      to,
      cc,
      subject: input.subject.trim() || "(no subject)",
      body: input.body,
      at: input.now ?? new Date().toISOString(),
      unread: false,
      starred: false,
      labels: to.every((person) => isCumulus(person.email)) ? ["Personal"] : ["External"],
    });
    return { ok: true, id };
  }

  saveDraft(input: SendInput): { id: string } {
    const id = input.draftId ?? `d-${Date.now().toString(36)}`;
    const message: MailMessage = {
      id,
      threadId: input.threadId ?? id,
      folder: "drafts",
      from: { name: ME.name, email: ME.email },
      to: parsePeople(input.to),
      cc: parsePeople(input.cc ?? ""),
      subject: input.subject.trim() || "(no subject)",
      body: input.body,
      at: input.now ?? new Date().toISOString(),
      unread: false,
      starred: false,
      labels: [],
    };
    const index = this.messages.findIndex((item) => item.id === id);
    if (index >= 0) this.messages[index] = message;
    else this.messages.unshift(message);
    return { id };
  }

  addAlias(alias: string) {
    const cleaned = alias.trim().toLowerCase();
    if (!validEmail(cleaned) || !isCumulus(cleaned) || cleaned === ME.email) return false;
    if (this.aliases.includes(cleaned)) return false;
    this.aliases.push(cleaned);
    return true;
  }

  addTask(task: ConductorTask) {
    if (task.messageId && this.tasks.some((item) => item.messageId === task.messageId)) return false;
    this.tasks.unshift(task);
    return true;
  }

  toggleTask(id: string) {
    const task = this.tasks.find((item) => item.id === id);
    if (task) task.done = !task.done;
  }

  addContact(contact: Contact) {
    if (this.contacts.some((item) => item.email === contact.email)) return false;
    this.contacts.push(contact);
    return true;
  }
}
