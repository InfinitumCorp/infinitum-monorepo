# VEIL Packaging

## Unpacked load
Select the `apps/veil` directory in `chrome://extensions` (Developer mode).

## Zip for distribution
From `apps/veil`:
```sh
zip -r ../../veil-extension.zip manifest.json background.js content.js sidebar.html sidebar.js lib icons
```

## Icons
`icons/icon-{16,32,48,128}.png` are generated from the `icon.svg` design:
`node scripts/make-icons.js`.

## CRX (optional)
Use the Chrome Web Store developer dashboard or `crx3` tooling after signing.

Current version: 0.4.0
