/**
 * Firmament Base anchoring helper.
 * Scaffold: computes a Merkle root placeholder for anchoring.
 * Production would submit the root to a Base contract.
 */
export function merkleRootPlaceholder(hashes: string[]): string {
  if (!hashes.length) return "0x0";
  // Simple concat hash placeholder
  return "0xroot_" + hashes.length + "_" + hashes[0].slice(0, 8);
}

export interface AnchorProof {
  root: string;
  chain: "base";
  txHash?: string;
}

export function prepareAnchor(hashes: string[]): AnchorProof {
  return { root: merkleRootPlaceholder(hashes), chain: "base" };
}
