import { readFileSync, writeFileSync, renameSync, mkdirSync, existsSync } from "node:fs";
import { dirname } from "node:path";

/**
 * Snapshot persistence for stores made of Maps. Writes are atomic (temp file + rename),
 * so a crash mid-write never corrupts data. A Postgres adapter on Cumulus Database
 * replaces this for multi-instance hosting.
 */

type MapStore = object;

/** Plain-JSON form of a store made of Maps. */
export function toSnapshot(store: MapStore): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(store as Record<string, unknown>)) out[k] = v instanceof Map ? { __map: [...v.entries()] } : v;
  return out;
}

/** Replace a store's contents with a snapshot, in place (other objects keep their reference). */
export function applySnapshot<T extends MapStore>(target: T, data: Record<string, unknown>): T {
  const t = target as Record<string, unknown>;
  for (const [k, v] of Object.entries(t)) if (v instanceof Map) v.clear();
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

export function saveSnapshot(path: string, store: MapStore) {
  const out = toSnapshot(store);
  mkdirSync(dirname(path), { recursive: true });
  const tmp = `${path}.tmp`;
  writeFileSync(tmp, JSON.stringify(out));
  renameSync(tmp, path);
}

export function loadSnapshot<T extends MapStore>(path: string, target: T): T {
  if (!existsSync(path)) return target;
  return applySnapshot(target, JSON.parse(readFileSync(path, "utf8")) as Record<string, unknown>);
}
