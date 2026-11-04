import { readFileSync, writeFileSync, renameSync, mkdirSync, existsSync } from "node:fs";
import { dirname } from "node:path";

/**
 * Snapshot persistence for stores made of Maps. Writes are atomic (temp file + rename),
 * so a crash mid-write never corrupts data. A Postgres adapter on Cumulus Database
 * replaces this for multi-instance hosting.
 */

type MapStore = object;

export function saveSnapshot(path: string, store: MapStore) {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(store as Record<string, unknown>)) out[k] = v instanceof Map ? { __map: [...v.entries()] } : v;
  mkdirSync(dirname(path), { recursive: true });
  const tmp = `${path}.tmp`;
  writeFileSync(tmp, JSON.stringify(out));
  renameSync(tmp, path);
}

export function loadSnapshot<T extends MapStore>(path: string, target: T): T {
  if (!existsSync(path)) return target;
  const data = JSON.parse(readFileSync(path, "utf8")) as Record<string, unknown>;
  for (const [k, v] of Object.entries(data)) {
    const value = v as { __map?: [unknown, unknown][] };
    (target as Record<string, unknown>)[k] = value && Array.isArray(value.__map) ? new Map(value.__map) : v;
  }
  return target;
}
