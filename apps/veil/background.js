/**
 * VEIL background service worker.
 * Opens the side panel when the action is clicked.
 */
chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});

chrome.runtime.onInstalled.addListener(() => {
  console.log("VEIL installed");
});
