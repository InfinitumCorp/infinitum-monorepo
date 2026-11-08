/* VEIL web app. Classic script so it also runs from file:///android_asset inside the Android app. */
(function () {
  "use strict";
  var TRACKERS = window.VEIL_TRACKERS || [];
  var $ = function (id) { return document.getElementById(id); };

  function stripTrackers(href) {
    var u;
    try { u = new URL(href); } catch (e) { return null; }
    var changed = false;
    TRACKERS.forEach(function (t) {
      if (u.searchParams.has(t)) { u.searchParams.delete(t); changed = true; }
    });
    return changed ? u.toString() : null;
  }
  window.veilStripTrackers = stripTrackers;

  // Inside VEIL for Android (bundled hub) or the Android WebView.
  var inApp = location.protocol === "file:" || /; wv\)/.test(navigator.userAgent);
  if (inApp) {
    document.documentElement.classList.add("in-app"); // hides every .dl-only block
    $("in-app").hidden = false;
    $("get").hidden = true;
    $("mode-chip").textContent = "Anonymous session";
  } else {
    // Lead with the download that fits this device: the APK on Android, the extension on desktop.
    var ua = navigator.userAgent;
    if (/Android/i.test(ua)) {
      $("rec-android").hidden = false;
    } else if (!/iPhone|iPad|iPod/i.test(ua)) {
      $("hero-primary").href = "downloads/veil-extension.zip";
      $("hero-primary-label").textContent = "Get the Chrome extension";
      $("hero-secondary").href = "downloads/veil-browser.apk";
      $("hero-secondary-label").textContent = "Download for Android";
      $("rec-chrome").hidden = false;
      $("apk-link").classList.remove("solid");
      $("ext-link").classList.add("solid");
      $("final-android").classList.remove("solid");
      $("final-chrome").classList.add("solid");
      $("final-android").parentNode.insertBefore($("final-chrome"), $("final-android"));
    }
  }

  $("clean-btn").addEventListener("click", function () {
    var raw = $("link-in").value.trim();
    var out = $("link-out");
    if (!raw) { out.textContent = ""; return; }
    var withScheme = /^[a-z]+:\/\//i.test(raw) ? raw : "https://" + raw;
    var cleaned = stripTrackers(withScheme);
    if (cleaned === null) {
      try { new URL(withScheme); out.textContent = "No tracking parameters found."; }
      catch (e) { out.textContent = "That doesn't look like a link."; }
      $("copy-btn").hidden = true;
      return;
    }
    out.textContent = cleaned;
    $("copy-btn").hidden = !navigator.clipboard;
  });
  $("link-in").addEventListener("keydown", function (e) { if (e.key === "Enter") $("clean-btn").click(); });
  $("copy-btn").addEventListener("click", function () {
    navigator.clipboard.writeText($("link-out").textContent).then(function () {
      $("copy-btn").textContent = "Copied";
      setTimeout(function () { $("copy-btn").textContent = "Copy"; }, 1500);
    });
  });

  if (window.VEIL_BUILD) $("build").textContent = window.VEIL_BUILD;

  if (!inApp && "serviceWorker" in navigator) {
    navigator.serviceWorker.register("sw.js").catch(function () {});
  }
})();
