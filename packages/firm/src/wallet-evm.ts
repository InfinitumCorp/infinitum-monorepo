/**
 * EVM wallet signing notes for FIRM.
 * Production would use secp256k1 (ethers/viem) to produce recoverable signatures
 * and send signed transactions via eth_sendRawTransaction.
 *
 * This module documents the expected interface.
 */
export interface EvmSignedTx {
  raw: string;
  hash: string;
}

export interface EvmWallet {
  address: string;
  signMessage(message: string): Promise<string>;
  signTransaction(tx: { to: string; data: string; value?: string }): Promise<EvmSignedTx>;
}

/** Scaffold: returns a placeholder. Replace with real secp256k1. */
export function createEvmWalletScaffold(address: string): EvmWallet {
  return {
    address,
    async signMessage(message) {
      return `0xsigned_${message.slice(0, 16)}`;
    },
    async signTransaction(tx) {
      return { raw: `0xraw_${tx.to}`, hash: `0xhash_${tx.to}` };
    },
  };
}
