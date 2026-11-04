#!/usr/bin/env bash
# Compile FirmToken.sol, ProjectToken.sol, and Anchor.sol with solc if available.
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$DIR/artifacts"
mkdir -p "$OUT"

if ! command -v solc >/dev/null; then
  echo "solc not found. Install solc or use a Foundry/Hardhat setup."
  exit 1
fi

solc --bin --abi --optimize -o "$OUT" "$DIR/contracts/FirmToken.sol" "$DIR/contracts/ProjectToken.sol" "$DIR/contracts/Anchor.sol"
echo "Compiled to $OUT"
