// nexus-store: private data gateway for Infinitum Nexus apps (+SPCTR on Vercel).
// Callers present x-nexus-key; its SHA-256 must be listed in public.nexus_gateway_keys.
// Uses the project's service-role credentials, which never leave Supabase.

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY")!;

const rest = (path: string, init: RequestInit = {}) =>
  fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: SERVICE_KEY,
      ...(SERVICE_KEY.startsWith("eyJ") ? { authorization: `Bearer ${SERVICE_KEY}` } : {}),
      "content-type": "application/json",
      ...(init.headers ?? {}),
    },
  });

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

async function sha256Hex(text: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

const validKeys = new Map<string, number>(); // hash -> checked at (ms)
async function authorized(key: string | null) {
  if (!key || key.length < 32) return false;
  const hash = await sha256Hex(key);
  const seen = validKeys.get(hash);
  if (seen && Date.now() - seen < 60_000) return true;
  const res = await rest(`nexus_gateway_keys?key_hash=eq.${hash}&select=key_hash`);
  const rows = res.ok ? await res.json() : [];
  if (rows.length) validKeys.set(hash, Date.now());
  return rows.length > 0;
}

async function load(since: number, versions: Record<string, number>) {
  const entries: unknown[] = [];
  for (let offset = 0; ; offset += 1000) {
    const res = await rest(`nexus_ledger?seq=gt.${Number(since)}&select=entry&order=seq.asc&limit=1000&offset=${offset}`);
    if (!res.ok) throw new Error(`ledger read ${res.status}`);
    const rows = (await res.json()) as { entry: unknown }[];
    entries.push(...rows.map((r) => r.entry));
    if (rows.length < 1000) break;
  }
  const vres = await rest("nexus_docs?select=id,version");
  if (!vres.ok) throw new Error(`docs read ${vres.status}`);
  const changed = ((await vres.json()) as { id: string; version: number }[])
    .filter((d) => d.version !== (versions?.[d.id] ?? 0))
    .map((d) => d.id);
  let docs: unknown[] = [];
  if (changed.length) {
    const list = changed.map((id) => `"${id.replace(/"/g, "")}"`).join(",");
    const dres = await rest(`nexus_docs?id=in.(${encodeURIComponent(list)})&select=id,version,data`);
    if (!dres.ok) throw new Error(`docs read ${dres.status}`);
    docs = await dres.json();
  }
  return { entries, docs };
}

async function commit(entries: unknown[], docs: unknown[]) {
  const res = await rest("rpc/nexus_commit", { method: "POST", body: JSON.stringify({ p_entries: entries, p_docs: docs }) });
  const text = await res.text();
  if (res.ok) return json(JSON.parse(text));
  if (text.includes("nexus_conflict")) return json({ error: "conflict" }, 409);
  console.error("commit failed", res.status, text.slice(0, 300));
  return json({ error: "commit failed" }, 500);
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);
  if (!(await authorized(req.headers.get("x-nexus-key")))) return json({ error: "unauthorized" }, 401);
  let body: { op?: string; since?: number; versions?: Record<string, number>; entries?: unknown[]; docs?: unknown[] };
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad json" }, 400);
  }
  try {
    if (body.op === "load") return json(await load(body.since ?? -1, body.versions ?? {}));
    if (body.op === "commit") return await commit(body.entries ?? [], body.docs ?? []);
    return json({ error: "unknown op" }, 400);
  } catch (err) {
    console.error(err);
    return json({ error: "store error" }, 500);
  }
});
