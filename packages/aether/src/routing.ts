/**
 * ÆTHER routing stub.
 * Finds a path between two nodes in the mesh (BFS).
 */
import type { AetherNode } from "./mesh.ts";

export function findPath(nodes: AetherNode[], from: string, to: string): string[] | null {
  const map = new Map(nodes.map((n) => [n.id, n]));
  const seen = new Set<string>();
  const queue: { id: string; path: string[] }[] = [{ id: from, path: [from] }];
  while (queue.length) {
    const { id, path } = queue.shift()!;
    if (id === to) return path;
    if (seen.has(id)) continue;
    seen.add(id);
    const node = map.get(id);
    if (!node) continue;
    for (const peer of node.peers) {
      if (!seen.has(peer)) queue.push({ id: peer, path: [...path, peer] });
    }
  }
  return null;
}
