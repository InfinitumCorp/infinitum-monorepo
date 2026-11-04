/**
 * Firmament Base anchoring helper.
 * Uses the real Merkle root from the ledger.
 */
import { merkleRoot } from "./merkle.ts";

export function merkleRootPlaceholder(hashes: string[]): string {
  return merkleRoot(hashes);
}

export interface AnchorProof {
  root: string;
  chain: "base";
  txHash?: string;
}

export function prepareAnchor(hashes: string[]): AnchorProof {
  return { root: merkleRoot(hashes), chain: "base" };
}
