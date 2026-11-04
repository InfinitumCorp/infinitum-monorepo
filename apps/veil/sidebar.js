/**
 * VEIL sidebar logic.
 * Scaffold: echoes the query and page context.
 * Production would call SCALVS / Conductor and use Tor for private fetches.
 */
const q = document.getElementById("q");
const out = document.getElementById("out");
const status = document.getElementById("status");

document.getElementById("ask").onclick = async () => {
  const text = q.value.trim();
  if (!text) return;
  status.textContent = "Thinking…";
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const context = { url: tab?.url, title: tab?.title, query: text };
    // Scaffold response
    out.textContent = JSON.stringify({ reply: `VEIL scaffold received: ${text}`, context }, null, 2);
    status.textContent = "Ready";
  } catch (err) {
    out.textContent = String(err);
    status.textContent = "Error";
  }
};

chrome.runtime.onMessage.addListener((msg) => {
  if (msg.type === "status") status.textContent = msg.text;
});
