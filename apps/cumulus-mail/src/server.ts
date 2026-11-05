/**
 * Cumulus://Mail.
 * Run: node --experimental-strip-types src/server.ts
 */
import { createReadStream, existsSync, statSync } from "node:fs";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { extname, join, normalize, sep } from "node:path";
import { isCumulus, isE2E, Mailbox, transcript, threadOf } from "@infinitum/mail";

const publicDir = join(import.meta.dirname, "..", "public");
const mail = new Mailbox();
const briefs = new Map<string, string>();

const types: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".svg": "image/svg+xml",
};

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    req.on("data", (chunk: Buffer) => {
      size += chunk.length;
      if (size > 100_000) {
        reject(new Error("body too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString()));
    req.on("error", reject);
  });
}

function json(res: ServerResponse, status: number, body: unknown) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(body));
}

function fileFor(urlPath: string): string | null {
  const trimmed = urlPath === "/" ? "index.html" : urlPath.replace(/^\/+/, "");
  const relative = normalize(trimmed);
  if (relative.startsWith("..") || relative.includes("\0")) return null;
  const full = join(publicDir, relative);
  const root = publicDir.endsWith(sep) ? publicDir : publicDir + sep;
  if (full !== publicDir && !full.startsWith(root)) return null;
  if (!existsSync(full) || !statSync(full).isFile()) return null;
  return full;
}

async function scalvs(mode: "summary" | "draft", subject: string, text: string) {
  const apiKey = process.env.XAI_API_KEY;
  if (!apiKey) return { ok: false as const, error: "Set XAI_API_KEY to turn on SCALVS." };
  const system =
    mode === "summary"
      ? "You are SCALVS inside Cumulus://Mail. Three short lines of what matters, then one line that starts with Action:. Plain text. No invented facts."
      : "You are SCALVS drafting a reply for Alex Reid. Reply body only. No subject line. No invented commitments.";
  const response = await fetch("https://api.x.ai/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({
      model: "grok-4.5",
      max_tokens: 220,
      temperature: 0.3,
      messages: [
        { role: "system", content: system },
        { role: "user", content: `Subject: ${subject.slice(0, 200)}\n\n${text.slice(0, 6000)}` },
      ],
    }),
  });
  if (!response.ok) return { ok: false as const, error: `SCALVS could not read this thread (${response.status}).` };
  const payload = (await response.json()) as { choices?: { message?: { content?: string } }[] };
  const reply = payload.choices?.[0]?.message?.content?.trim() ?? "";
  if (!reply) return { ok: false as const, error: "SCALVS returned an empty brief." };
  return { ok: true as const, text: reply };
}

async function handle(req: IncomingMessage, res: ServerResponse) {
  const url = new URL(req.url ?? "/", "http://localhost");
  try {
    if (req.method === "GET" && url.pathname === "/api/state") {
      json(res, 200, { ...mail.view(), briefs: Object.fromEntries(briefs) });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/send") {
      const body = JSON.parse(await readBody(req)) as Parameters<Mailbox["send"]>[0];
      const result = mail.send(body);
      json(res, result.ok ? 200 : 400, result.ok ? { ...result, state: mail.view() } : result);
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/draft") {
      const body = JSON.parse(await readBody(req)) as Parameters<Mailbox["saveDraft"]>[0];
      json(res, 200, { ...mail.saveDraft(body), state: mail.view() });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/read") {
      const body = JSON.parse(await readBody(req)) as { threadId?: string };
      if (body.threadId) mail.markThreadRead(body.threadId);
      json(res, 200, mail.view());
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/star") {
      const body = JSON.parse(await readBody(req)) as { id?: string };
      if (body.id) mail.toggleStar(body.id);
      json(res, 200, mail.view());
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/move") {
      const body = JSON.parse(await readBody(req)) as { id?: string; folder?: "inbox" | "sent" | "drafts" | "archive" | "trash" };
      if (body.id && body.folder) mail.move(body.id, body.folder);
      json(res, 200, mail.view());
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/remove") {
      const body = JSON.parse(await readBody(req)) as { id?: string };
      if (body.id) mail.remove(body.id);
      json(res, 200, mail.view());
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/alias") {
      const body = JSON.parse(await readBody(req)) as { alias?: string };
      json(res, 200, { ok: mail.addAlias(body.alias ?? ""), state: mail.view() });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/task") {
      const body = JSON.parse(await readBody(req)) as { title?: string; detail?: string; messageId?: string };
      const ok = mail.addTask({
        id: `t-${Date.now().toString(36)}`,
        title: String(body.title ?? "Task").slice(0, 180),
        detail: String(body.detail ?? "Queued from mail").slice(0, 240),
        done: false,
        messageId: body.messageId,
      });
      json(res, 200, { ok, state: mail.view() });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/task/toggle") {
      const body = JSON.parse(await readBody(req)) as { id?: string };
      if (body.id) mail.toggleTask(body.id);
      json(res, 200, mail.view());
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/contact") {
      const body = JSON.parse(await readBody(req)) as { name?: string; email?: string };
      const email = String(body.email ?? "");
      const ok = mail.addContact({
        id: `c-${Date.now().toString(36)}`,
        name: String(body.name ?? email),
        email,
        org: isCumulus(email) ? "Cumulus://Mail" : "External",
        note: "Saved from a message.",
      });
      json(res, 200, { ok, state: mail.view() });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/brief") {
      const body = JSON.parse(await readBody(req)) as { mode?: string; threadId?: string; subject?: string };
      const mode = body.mode === "draft" ? "draft" : "summary";
      const thread = threadOf(mail.messages, body.threadId ?? "").filter((message) => message.folder !== "trash" || mode === "summary");
      const text = transcript(thread);
      const result = await scalvs(mode, body.subject ?? thread[0]?.subject ?? "", text);
      if (result.ok && mode === "summary" && body.threadId) briefs.set(thread.map((message) => message.id).join(":"), result.text);
      json(res, result.ok ? 200 : 400, result);
      return;
    }

    if (req.method === "GET" && url.pathname === "/api/message") {
      const id = url.searchParams.get("id") ?? "";
      const message = mail.messages.find((item) => item.id === id);
      if (!message) {
        json(res, 404, { error: "not found" });
        return;
      }
      json(res, 200, { message, e2e: isE2E(message) });
      return;
    }

    if (req.method === "GET") {
      const file = fileFor(url.pathname);
      if (!file) {
        json(res, 404, { error: "not found" });
        return;
      }
      res.statusCode = 200;
      res.setHeader("Content-Type", types[extname(file)] ?? "application/octet-stream");
      createReadStream(file).pipe(res);
      return;
    }

    json(res, 404, { error: "not found" });
  } catch (error) {
    json(res, 500, { error: error instanceof Error ? error.message : "error" });
  }
}

const port = Number(process.env.PORT ?? 3010);
createServer(handle).listen(port, () => {
  console.log(`Cumulus://Mail listening on http://localhost:${port}`);
});
