import { sha256Hex } from "./canonical.ts";

/** Binary Merkle tree over hex leaf hashes. Odd nodes are carried up unchanged. */
export function merkleRoot(leaves: string[]): string {
  if (leaves.length === 0) return "0".repeat(64);
  let level = leaves;
  while (level.length > 1) {
    const next: string[] = [];
    for (let i = 0; i < level.length; i += 2) {
      next.push(i + 1 < level.length ? sha256Hex(level[i] + level[i + 1]) : level[i]);
    }
    level = next;
  }
  return level[0];
}

export interface ProofStep {
  sibling: string;
  position: "left" | "right";
}

/** Proof that one entry is included under a given root, without revealing the other entries. */
export function merkleProof(leaves: string[], index: number): ProofStep[] {
  if (index < 0 || index >= leaves.length) throw new Error("Firmament: leaf index out of range");
  const proof: ProofStep[] = [];
  let level = leaves;
  let i = index;
  while (level.length > 1) {
    const isRight = i % 2 === 1;
    const siblingIndex = isRight ? i - 1 : i + 1;
    if (siblingIndex < level.length) {
      proof.push({ sibling: level[siblingIndex], position: isRight ? "left" : "right" });
    }
    const next: string[] = [];
    for (let j = 0; j < level.length; j += 2) {
      next.push(j + 1 < level.length ? sha256Hex(level[j] + level[j + 1]) : level[j]);
    }
    level = next;
    i = Math.floor(i / 2);
  }
  return proof;
}

export function verifyMerkleProof(leaf: string, proof: ProofStep[], root: string): boolean {
  let acc = leaf;
  for (const step of proof) {
    acc = step.position === "left" ? sha256Hex(step.sibling + acc) : sha256Hex(acc + step.sibling);
  }
  return acc === root;
}
