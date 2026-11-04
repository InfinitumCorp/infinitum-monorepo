/**
 * VEIL FIRM Wallet panel.
 * Scaffold: shows a placeholder address and balance.
 * Production would use the FIRM wallet package and an RPC endpoint.
 */
const addr = document.getElementById("addr");
const bal = document.getElementById("bal");

const stored = await chrome.storage.local.get(["firmAddress"]);
const address = stored.firmAddress ?? "0x" + "0".repeat(40);
addr.textContent = address;
bal.textContent = "Balance: 0 FIRM (scaffold)";
