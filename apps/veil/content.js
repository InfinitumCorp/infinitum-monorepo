/**
 * VEIL content script scaffold.
 * Runs in the page context. Production would inject privacy helpers.
 */
(() => {
  const cleaned = location.href.replace(/([?&])(utm_[^=]+|fbclid|gclid)=[^&]+/g, "$1").replace(/[?&]$/, "");
  if (cleaned !== location.href) {
    console.log("VEIL would clean URL:", cleaned);
  }
})();
