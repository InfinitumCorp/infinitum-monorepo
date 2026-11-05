import { readFileSync, writeFileSync, renameSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import type { CumulusStores } from "./suite.ts";

/**
 * Snapshot persistence for stores made of Maps (same format as +SPCTR's). Writes are atomic
 * (temp file + rename). Hosted deployments use the nexus-store gateway instead.
 */

export function toSnapshot(store: object): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(store as Record<string, unknown>)) out[k] = v instanceof Map ? { __map: [...v.entries()] } : v;
  return out;
}

export function applySnapshot<T extends object>(target: T, data: Record<string, unknown>): T {
  const t = target as Record<string, unknown>;
  for (const v of Object.values(t)) if (v instanceof Map) v.clear();
  for (const [k, v] of Object.entries(data)) {
    const value = v as { __map?: [unknown, unknown][] };
    if (value && Array.isArray(value.__map)) {
      const existing = t[k];
      if (existing instanceof Map) for (const [mk, mv] of value.__map) existing.set(mk, mv);
      else t[k] = new Map(value.__map);
    } else t[k] = v;
  }
  return target;
}

export function saveSnapshot(path: string, store: object) {
  mkdirSync(dirname(path), { recursive: true });
  const tmp = `${path}.tmp`;
  writeFileSync(tmp, JSON.stringify(toSnapshot(store)));
  renameSync(tmp, path);
}

export function loadSnapshot<T extends object>(path: string, target: T): T {
  if (!existsSync(path)) return target;
  return applySnapshot(target, JSON.parse(readFileSync(path, "utf8")) as Record<string, unknown>);
}

/** Load every suite store from `dir` (cid.json, mail.json, wallet.json, quota.json, suite.json). */
export function loadCumulusStores(dir: string, stores: CumulusStores): CumulusStores {
  for (const [name, store] of Object.entries(stores)) loadSnapshot(join(dir, `${name}.json`), store);
  return stores;
}

export function saveCumulusStores(dir: string, stores: CumulusStores) {
  for (const [name, store] of Object.entries(stores)) saveSnapshot(join(dir, `${name}.json`), store);
}
