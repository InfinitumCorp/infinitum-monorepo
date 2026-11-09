import { createServer } from "node:http";
import { httpServerHandler } from "cloudflare:node";
import { DurableObject } from "cloudflare:workers";
import type { Entry } from "@infinitum/firmament";
import handler, { useGateway } from "./vercel.ts";
import { ConflictError, type DocRow, type NexusGateway } from "./remote.ts";
import { initSchema, loadState, commitState, type CommitResult } from "./do-store.ts";

/**
 * Cumulus://ID (+SPCTR) on Cloudflare Workers at id.cumuluscloud.app, free plan.
 * Static files (public/) are Workers static assets; /api/* reaches the same hosted handler as the
 * Vercel build through Workers' Node.js HTTP server support. State (Firmament ledger + CID and
 * Spectre stores) lives in one SQLite-backed Durable Object, NexusStore, with the same
 * all-or-nothing commit rules as the Supabase nexus-store function.
 * Configuration: wrangler.jsonc (vars) and Worker secrets (FIRMAMENT_SIGNER_KEY, sign-in secrets
 * such as COGNITO_CLIENT_SECRET).
 */
const PORT = 8080;
createServer((req, res) => void handler(req, res)).listen(PORT);
const http = httpServerHandler({ port: PORT });

interface NexusStoreStub {
  load(since: number, versions: Record<string, number>): Promise<{ entries: Entry[]; docs: DocRow[] }>;
  commit(entries: Entry[], docs: { id: string; expected: number; data: Record<string, unknown> }[]): Promise<CommitResult>;
}
interface Env {
  NEXUS_STATE: { idFromName(name: string): unknown; get(id: unknown): NexusStoreStub };
}

export class NexusStore extends DurableObject<Env> {
  constructor(ctx: ConstructorParameters<typeof DurableObject<Env>>[0], env: Env) {
    super(ctx, env);
    initSchema(ctx.storage.sql);
  }
  async load(since: number, versions: Record<string, number>) {
    return loadState(this.ctx.storage.sql, since, versions);
  }
  async commit(entries: Entry[], docs: { id: string; expected: number; data: Record<string, unknown> }[]) {
    return commitState(this.ctx.storage.sql, entries, docs, (fn) => this.ctx.storage.transactionSync(fn));
  }
}

/**
 * Talks to the NexusStore Durable Object. A stub is made per call: Workers forbid using I/O
 * objects (a stub included) from a request other than the one that created them.
 */
class DurableGateway implements NexusGateway {
  private readonly ns: Env["NEXUS_STATE"];
  constructor(ns: Env["NEXUS_STATE"]) {
    this.ns = ns;
  }
  private stub() {
    return this.ns.get(this.ns.idFromName("cumulus-id"));
  }
  load(since: number, versions: Record<string, number>) {
    return this.stub().load(since, versions);
  }
  async commit(entries: Entry[], docs: { id: string; expected: number; data: Record<string, unknown> }[]) {
    const result = await this.stub().commit(entries, docs);
    if (!result.ok) throw new ConflictError();
    return result.versions;
  }
}

let configured = false;
function configure(env: Env) {
  if (configured) return;
  useGateway(new DurableGateway(env.NEXUS_STATE));
  configured = true;
}

export default {
  fetch(request: Request, env: Env, ctx: unknown) {
    configure(env);
    return http.fetch(request, env, ctx);
  },
};
