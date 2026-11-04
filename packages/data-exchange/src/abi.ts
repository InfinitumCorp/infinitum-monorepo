/**
 * ABI calldata for CdatLicense.mint / burn.
 * Selector is keccak256("mint(address,string,string,uint64,uint256)")[:4] = 0x01d47bed.
 * No external crypto dependency: the selector is fixed to that signature.
 */

export const CDAT_MINT_SELECTOR = "01d47bed";
export const CDAT_BURN_SELECTOR = "42966c68";

export interface CdatMintArgs {
  licensee: string;
  datasetId: string;
  terms: string;
  expiresAt: number;
  amountUsdCents: number;
}

function word(hex: string): string {
  return hex.replace(/^0x/, "").toLowerCase().padStart(64, "0");
}

function uintWord(value: number | bigint): string {
  return word(BigInt(value).toString(16));
}

function addressWord(address: string): string {
  if (!/^0x[0-9a-fA-F]{40}$/.test(address)) {
    throw new Error(`CDAT: licensee is not an address: ${address}`);
  }
  return word(address);
}

/** ABI-encode a dynamic string. Returns the hex of the length word plus padded bytes. */
function encodeString(value: string): string {
  const bytes = Buffer.from(value, "utf8");
  const padded = Buffer.alloc(Math.ceil(bytes.length / 32) * 32);
  bytes.copy(padded);
  return uintWord(bytes.length) + padded.toString("hex");
}

/** Calldata for CdatLicense.mint(address,string,string,uint64,uint256). */
export function encodeCdatMint(args: CdatMintArgs): string {
  const dataset = encodeString(args.datasetId);
  const terms = encodeString(args.terms);
  const headWords = 5;
  const datasetOffset = headWords * 32;
  const termsOffset = datasetOffset + dataset.length / 2;
  const head =
    addressWord(args.licensee) +
    uintWord(datasetOffset) +
    uintWord(termsOffset) +
    uintWord(args.expiresAt) +
    uintWord(args.amountUsdCents);
  return "0x" + CDAT_MINT_SELECTOR + head + dataset + terms;
}

/** Calldata for CdatLicense.burn(uint256). */
export function encodeCdatBurn(onchainId: number): string {
  return "0x" + CDAT_BURN_SELECTOR + uintWord(onchainId);
}
