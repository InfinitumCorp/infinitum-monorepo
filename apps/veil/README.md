# VEIL</>

Tor-first secure browser sidebar for the Infinitum ecosystem (Chrome MV3 extension).
This is the extension edition used to build and test VEIL features ahead of the
Tor Browser-derived desktop engine described on the product page.

## Status
v0.4.0, roadmap Phase I (browser foundation) with the rewards ledger from Phase IV.

- **Two workspaces.** Anonymous (default): Tor, tracker stripping and New Identity only.
  Signed-in: Cumulus://ID, Ask (:\ARCHITCT\), FIRM Wallet and $VEIL rewards.
  Every switch is confirmed in the panel and clears cookies and site data first.
- **Tor.** SOCKS5 proxy to a local Tor service on 127.0.0.1:9050 (DNS goes over Tor),
  WebRTC limited to the proxy, connectivity check against check.torproject.org.
- **New Identity.** Clears cookies, caches and site storage and closes every tab.
  History, passwords and downloads are untouched.
- **Tracker stripping.** Removes utm_*, fbclid, gclid and similar parameters, on by default.
- **$VEIL rewards** at the published launch rate: 0.1 $VEIL per eligible active minute,
  max 240 minutes (24 $VEIL) per UTC day. A minute counts only with a trusted keyboard,
  mouse or touch event on the active tab of the focused window, in the signed-in workspace.
  Pages report three booleans and nothing about the site. Claims produce receipts that
  stay `pending-settlement` until the $VEIL claim contract is deployed.
- **Ask** shares the current tab's title and address only when you tick the box.

## Load
`chrome://extensions` → Developer mode → Load unpacked → `apps/veil`

## Develop
```sh
cd apps/veil
node --test test/            # unit + background tests (fake chrome.*)
node scripts/make-icons.js   # regenerate icons/ from the icon.svg design
```

## Configuration (chrome.storage.local)
| Key | Default | Purpose |
| --- | --- | --- |
| `cidOrigin` | `http://localhost:3000` | Cumulus://ID origin (Spectre server) for sign-in and `/api/me` |
| `architectUrl` | `http://localhost:3001` | :\ARCHITCT\ endpoint for Ask |
