/**
 * VEIL sidebar logic.
 * Asks Conductor to perform the query with page context.
 * Falls back to a local echo if Conductor is unreachable.
 */
const CONDUCTOR_URL = "http://localhost:3001";

const q = document.getElementById("q");
const out = document.getElementById("out");
const status = document.getElementById("status");

document.getElementById("ask").onclick = async () => {
  const text = q.value.trim();
  if (!text) return;
  status.textContent = "Thinking…";
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const intent = `${text} (page: ${tab?.title ?? "unknown"} — ${tab?.url ?? ""})`;
    const r = await fetch(`${CONDUCTOR_URL}/api/perform`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ intent, autonomy: "rehearsal" }),
    });
    if (!r.ok) throw new Error(`Conductor ${r.status}`);
    const data = await r.json();
    out.textContent = JSON.stringify(data, null, 2);
    status.textContent = "Ready";
  } catch (err) {
    out.textContent = `Conductor unreachable. Local echo:\n${text}\n\n${String(err)}`;
    status.textContent = "Offline";
  }
};

chrome.runtime.onMessage.addListener((msg) => {
  if (msg.type === "status") status.textContent = msg.text;
});
