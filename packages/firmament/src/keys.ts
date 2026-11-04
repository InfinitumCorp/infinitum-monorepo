import { generateKeyPairSync, createPrivateKey, createPublicKey, sign, verify, KeyObject } from "node:crypto";

/** An Ed25519 signing identity. Public keys are shared as base64url-encoded raw 32-byte keys. */
export interface Signer {
  publicKey: string;
  sign(message: string): string;
}

export function generateSigner(): Signer & { exportPrivateKey(): string } {
  const { privateKey, publicKey } = generateKeyPairSync("ed25519");
  return fromKeyObjects(privateKey, publicKey);
}

export function signerFromPrivateKey(pkcs8Base64: string): Signer & { exportPrivateKey(): string } {
  const privateKey = createPrivateKey({ key: Buffer.from(pkcs8Base64, "base64"), format: "der", type: "pkcs8" });
  return fromKeyObjects(privateKey, createPublicKey(privateKey));
}

function fromKeyObjects(privateKey: KeyObject, publicKey: KeyObject) {
  const jwk = publicKey.export({ format: "jwk" });
  const pub = jwk.x as string; // raw Ed25519 key, base64url
  return {
    publicKey: pub,
    sign: (message: string) => sign(null, Buffer.from(message), privateKey).toString("base64url"),
    exportPrivateKey: () => (privateKey.export({ format: "der", type: "pkcs8" }) as Buffer).toString("base64"),
  };
}

export function verifySignature(publicKey: string, message: string, signature: string): boolean {
  try {
    const key = createPublicKey({ key: { kty: "OKP", crv: "Ed25519", x: publicKey }, format: "jwk" });
    return verify(null, Buffer.from(message), key, Buffer.from(signature, "base64url"));
  } catch {
    return false;
  }
}
