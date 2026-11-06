/**
 * Uniswap V2-style router calldata helpers for FIRM DEX.
 */
export function encodeGetAmountsOut(amountIn: string, path: string[]): string {
  const amount = BigInt(amountIn).toString(16).padStart(64, "0");
  const pathOffset = "0000000000000000000000000000000000000000000000000000000000000040";
  const pathLen = path.length.toString(16).padStart(64, "0");
  const pathData = path.map((p) => p.replace(/^0x/, "").padStart(64, "0")).join("");
  return "0xd06ca61f" + amount + pathOffset + pathLen + pathData;
}

export function encodeSwapExactTokensForTokens(
  amountIn: string,
  minAmountOut: string,
  path: string[],
  to: string,
  deadline: number,
): string {
  const pathData = path.map((p) => p.replace(/^0x/, "").padStart(64, "0")).join("");
  return (
    "0x38ed1739" +
    BigInt(amountIn).toString(16).padStart(64, "0") +
    BigInt(minAmountOut).toString(16).padStart(64, "0") +
    "00000000000000000000000000000000000000000000000000000000000000a0" +
    to.replace(/^0x/, "").padStart(64, "0") +
    deadline.toString(16).padStart(64, "0") +
    path.length.toString(16).padStart(64, "0") +
    pathData
  );
}
