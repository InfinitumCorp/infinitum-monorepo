/**
 * Minimal CBOR (RFC 8949) codec covering what WebAuthn uses: unsigned/negative ints,
 * byte strings, text strings, arrays, maps and the simple values false/true/null.
 */
export type CborValue = number | Uint8Array | string | CborValue[] | Map<CborValue, CborValue> | boolean | null;

export function decodeCbor(bytes: Uint8Array, start = 0): { value: CborValue; offset: number } {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let pos = start;

  const readLength = (info: number): number => {
    if (info < 24) return info;
    if (info === 24) return view.getUint8(pos++);
    if (info === 25) { const v = view.getUint16(pos); pos += 2; return v; }
    if (info === 26) { const v = view.getUint32(pos); pos += 4; return v; }
    if (info === 27) {
      const v = view.getBigUint64(pos); pos += 8;
      if (v > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error("CBOR: integer too large");
      return Number(v);
    }
    throw new Error("CBOR: indefinite lengths are not supported");
  };

  const item = (): CborValue => {
    if (pos >= bytes.length) throw new Error("CBOR: unexpected end of data");
    const initial = bytes[pos++];
    const major = initial >> 5;
    const info = initial & 0x1f;
    switch (major) {
      case 0: return readLength(info);
      case 1: return -1 - readLength(info);
      case 2: { const n = readLength(info); const v = bytes.slice(pos, pos + n); if (v.length !== n) throw new Error("CBOR: truncated bytes"); pos += n; return v; }
      case 3: { const n = readLength(info); const v = new TextDecoder("utf-8", { fatal: true }).decode(bytes.subarray(pos, pos + n)); pos += n; return v; }
      case 4: { const n = readLength(info); const arr: CborValue[] = []; for (let i = 0; i < n; i++) arr.push(item()); return arr; }
      case 5: { const n = readLength(info); const m = new Map<CborValue, CborValue>(); for (let i = 0; i < n; i++) { const k = item(); m.set(k, item()); } return m; }
      case 7:
        if (info === 20) return false;
        if (info === 21) return true;
        if (info === 22) return null;
        throw new Error("CBOR: unsupported simple value");
      default:
        throw new Error(`CBOR: unsupported major type ${major}`);
    }
  };

  const value = item();
  return { value, offset: pos };
}

export function encodeCbor(value: CborValue): Uint8Array {
  const out: number[] = [];
  const head = (major: number, n: number) => {
    if (n < 24) out.push((major << 5) | n);
    else if (n < 0x100) out.push((major << 5) | 24, n);
    else if (n < 0x10000) out.push((major << 5) | 25, n >> 8, n & 0xff);
    else out.push((major << 5) | 26, (n >>> 24) & 0xff, (n >>> 16) & 0xff, (n >>> 8) & 0xff, n & 0xff);
  };
  const enc = (v: CborValue) => {
    if (v === false) out.push(0xf4);
    else if (v === true) out.push(0xf5);
    else if (v === null) out.push(0xf6);
    else if (typeof v === "number") {
      if (!Number.isInteger(v)) throw new Error("CBOR: only integers are supported");
      v >= 0 ? head(0, v) : head(1, -1 - v);
    } else if (typeof v === "string") { const b = new TextEncoder().encode(v); head(3, b.length); out.push(...b); }
    else if (v instanceof Uint8Array) { head(2, v.length); out.push(...v); }
    else if (Array.isArray(v)) { head(4, v.length); v.forEach(enc); }
    else { head(5, v.size); for (const [k, val] of v) { enc(k); enc(val); } }
  };
  enc(value);
  return Uint8Array.from(out);
}
