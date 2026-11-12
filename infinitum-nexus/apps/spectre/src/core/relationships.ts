import type { Contact, Relationship } from "./model.ts";

/**
 * Relationship intelligence: who knows whom, who influences each decision,
 * and the warmest path into any account.
 */

export interface PathStep {
  from: string;
  to: string;
  strength: number;
}

export interface WarmPath {
  target: string;
  steps: PathStep[];
  /** 0..1: product of per-hop warmth (strength / 5). Higher is warmer. */
  warmth: number;
}

/**
 * Warmest path from any of `starts` (your team, as "cid:..." ids) to any contact at the target company.
 * Edges are undirected; each hop's warmth is strength/5 and a path's warmth is the product, so
 * one strong introduction beats a chain of weak ones. Max 4 hops.
 */
export function warmestPath(
  starts: string[],
  targetCompanyId: string,
  contacts: Contact[],
  relationships: Relationship[],
  maxHops = 4,
): WarmPath | null {
  const targets = new Set(contacts.filter((c) => c.companyId === targetCompanyId && !c.optedOut).map((c) => c.id));
  if (targets.size === 0) return null;

  const adj = new Map<string, { to: string; strength: number }[]>();
  const add = (a: string, b: string, s: number) => {
    if (!adj.has(a)) adj.set(a, []);
    adj.get(a)!.push({ to: b, strength: s });
  };
  for (const r of relationships) {
    add(r.from, r.to, r.strength);
    add(r.to, r.from, r.strength);
  }

  // Best-first search maximizing product of warmth (Dijkstra on -log warmth).
  const best = new Map<string, number>();
  const prev = new Map<string, PathStep>();
  const hops = new Map<string, number>();
  const queue: { node: string; warmth: number }[] = [];
  for (const s of starts) {
    best.set(s, 1);
    hops.set(s, 0);
    queue.push({ node: s, warmth: 1 });
  }

  while (queue.length) {
    queue.sort((a, b) => b.warmth - a.warmth);
    const { node, warmth } = queue.shift()!;
    if (warmth < (best.get(node) ?? 0)) continue;
    if (targets.has(node) && !starts.includes(node)) {
      const steps: PathStep[] = [];
      for (let n = node; prev.has(n); n = prev.get(n)!.from) steps.unshift(prev.get(n)!);
      return { target: node, steps, warmth: Math.round(warmth * 1000) / 1000 };
    }
    if ((hops.get(node) ?? 0) >= maxHops) continue;
    for (const e of adj.get(node) ?? []) {
      const w = warmth * (e.strength / 5);
      if (w > (best.get(e.to) ?? 0)) {
        best.set(e.to, w);
        prev.set(e.to, { from: node, to: e.to, strength: e.strength });
        hops.set(e.to, (hops.get(node) ?? 0) + 1);
        queue.push({ node: e.to, warmth: w });
      }
    }
  }
  return null;
}

/** The buying committee for an account, ordered by influence. */
export function buyingCommittee(companyId: string, contacts: Contact[]) {
  const order = { "decision-maker": 0, champion: 1, influencer: 2, blocker: 3, user: 4 } as const;
  return contacts
    .filter((c) => c.companyId === companyId && !c.optedOut)
    .sort((a, b) => (order[a.influence ?? "user"] ?? 5) - (order[b.influence ?? "user"] ?? 5));
}

/** Gaps worth fixing: no decision-maker, no champion, or a known blocker. */
export function committeeGaps(companyId: string, contacts: Contact[]): string[] {
  const c = buyingCommittee(companyId, contacts);
  const gaps: string[] = [];
  if (!c.some((x) => x.influence === "decision-maker")) gaps.push("No decision-maker identified");
  if (!c.some((x) => x.influence === "champion")) gaps.push("No champion identified");
  const blockers = c.filter((x) => x.influence === "blocker");
  if (blockers.length) gaps.push(`${blockers.length} blocker${blockers.length > 1 ? "s" : ""} to win over`);
  return gaps;
}
