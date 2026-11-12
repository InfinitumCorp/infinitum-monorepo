/**
 * Firmament Base anchor client.
 * Scaffold: prepares the call. Production would use eth_sendRawTransaction.
 */
export interface AnchorCall {
  to: string;
  data: string;
}

export function prepareAnchorCall(anchorContract: string, root: string): AnchorCall {
  const selector = "0x4e4d9fea"; // anchor(bytes32)
  const rootPadded = root.replace(/^0x/, "").padStart(64, "0");
  return { to: anchorContract, data: selector + rootPadded };
}
