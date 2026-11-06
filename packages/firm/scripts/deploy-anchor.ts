/**
 * Deploy FirmamentAnchor to Base.
 * Scaffold: prints the transaction that would be sent.
 * Production would use a funded wallet and eth_sendRawTransaction.
 */
import { prepareAnchorCall } from "../src/anchor-client.ts";

const ANCHOR = process.env.ANCHOR_CONTRACT ?? "0x0000000000000000000000000000000000000000";
const ROOT = process.env.MERKLE_ROOT ?? "0x" + "0".repeat(64);

const call = prepareAnchorCall(ANCHOR, ROOT);
console.log(JSON.stringify({ action: "anchor", ...call }, null, 2));
