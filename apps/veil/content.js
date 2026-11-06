/**
 * VEIL content script.
 * 1. Strips tracking parameters from the page URL.
 * 2. In the signed-in workspace only, reports that a person is active so
 *    the background can credit eligible minutes. The report carries no URL,
 *    title or content, just three booleans.
 *
 * Keep TRACKERS identical to lib/privacy.js (privacy.test.js checks it).
 */
(() => {
  const TRACKERS = [
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
    "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid",
    "igshid", "li_fat_id", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "oly_enc_id",
    "oly_anon_id", "vero_id", "wickedid", "s_cid", "_ga", "_gl",
  ];

  function stripTrackers(href) {
    let u;
    try { u = new URL(href); } catch { return null; }
    let changed = false;
    for (const t of TRACKERS) {
      if (u.searchParams.has(t)) { u.searchParams.delete(t); changed = true; }
    }
    return changed ? u.toString() : null;
  }

  function cleanNow() {
    const cleaned = stripTrackers(location.href);
    if (cleaned) history.replaceState(history.state, "", cleaned);
    return cleaned;
  }

  // ---- Activity reporting (signed-in workspace only) ----
  const THROTTLE_MS = 15_000;
  const EVENTS = ["pointerdown", "keydown", "wheel", "touchstart"];
  let lastSent = 0;
  let listening = false;

  function onInput(e) {
    if (!e.isTrusted) return; // synthetic events never earn
    const now = Date.now();
    if (now - lastSent < THROTTLE_MS) return;
    lastSent = now;
    chrome.runtime.sendMessage({
      type: "activity",
      trusted: true,
      visible: document.visibilityState === "visible",
      focused: document.hasFocus(),
    }).catch(() => {});
  }

  function setListening(on) {
    if (on === listening) return;
    listening = on;
    for (const ev of EVENTS) {
      if (on) window.addEventListener(ev, onInput, { capture: true, passive: true });
      else window.removeEventListener(ev, onInput, { capture: true });
    }
  }

  chrome.storage.local.get(["autoClean", "workspace"]).then((s) => {
    if (s.autoClean) cleanNow();
    setListening(s.workspace === "authenticated");
  });

  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === "local" && changes.workspace) {
      setListening(changes.workspace.newValue === "authenticated");
    }
  });

  chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
    if (msg?.type === "cleanUrl") {
      const cleaned = cleanNow();
      sendResponse(cleaned ? { cleaned: true, url: cleaned } : { cleaned: false });
    }
  });
})();
