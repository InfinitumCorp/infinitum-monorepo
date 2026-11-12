# THRONE][OS™ on the TECNO KM4

**Infinitum Corporation®**

Path A from the design: a launcher, a guard, and a vault on locked HiOS 15. The repo name stays `throne-os`. Nothing here flashes firmware, unlocks the bootloader, or claims a certification.

The product name is **THRONE][OS™**. The KM4 layer uses the white Infinitum field: background `#FFFFFF`, ink `#161616`, secondary `#5C5C5C`, thin type, and no color accents. The home screen opens with the THRONE][OS™ title and the edition name. The large Infinitum Corporation lockup is not on that screen.

## What is installed

| App | Package | Role |
| --- | --- | --- |
| THRONE][OS™ | `com.infinitum.throne.launcher` | Home screen. Pages INFINITUM and OTHERS. Black glyphs on white. No widgets, ads, analytics, or network permission. |
| THRONE][OS™ Guard | `com.infinitum.throne.guard` | Local audit and settings links. One optional, user-started Private DNS reachability check. |
| THRONE][OS™ Vault | `com.infinitum.throne.vault` | Encrypts text on the phone and can hand ciphertext to Google Messages. No network permission. |
| THRONE][OS™ Suite | `com.infinitum.throne.suite` | Opens a catalog product in its installed app, a secure WebView of its site, or Coming soon. No tracker SDK. The launcher itself has no network permission. |
| THRONE][OS™ Eidetic | `com.infinitum.eidetic.cam` | Consent-gated camera. Frames go to a human reviewer only after a loopback policy check. |
| THRONE][OS™ Harvest | `com.infinitum.hrvst.cam` | Camera capture stored as a Harvest Packet in an encrypted Personal Silo. No network permission. |

Editions are build flavors. **^KETER** is William's edition and includes the military catalog entries. **ARCH//ANGEL** hides those entries on both pages. A bad edition flag fails closed to ARCH//ANGEL. Debug APKs are signed with the Android debug key. They are not ^KETER signing keys, and those keys are not in this repository.

## Product catalog

`apps/throne-suite/src/main/assets/products.json` is the only product list. The launcher packages that same file and lists it on INFINITUM. An installed native package opens that app. If it is not installed and the entry has an https URL, the suite opens that URL in a WebView. If it has no URL, the suite shows Coming soon. OTHERS is every other launchable app. Guard and Vault stay on OTHERS.

The shell allows https only, blocks mixed content and third-party cookies, denies camera, microphone, and location prompts, and does not add a JavaScript bridge or an analytics SDK. Safe Browsing is off so page URLs are not sent to Google. Top-level navigation stays on the product host. Products whose sites already offer Cumulus://ID may also navigate to `id.cumuluscloud.app`. A user-gesture popup to an allowed host loads in the same WebView. Trusted Web Activity is not used: checked hosts had no `/.well-known/assetlinks.json`, and a Chrome fallback would leave this shell.

Checked on 10 Nov 2026. Sibling InfinitumCorp repositories were not readable from this environment. Public APKs and live pages were checked instead.

| Catalog name | Edition | Opens as | Native package | When the app is not installed |
| --- | --- | --- | --- | --- |
| Conjure™ | both | placeholder | `com.conjure.app` | Coming soon. No product page responded. |
| FABRIC™ | both | web shell | `app.infinitumfabric.client`, also `com.infinitum.fabric` | https://infinitumfabric.app . The icon is the black wordmark ++ ™ on a white rounded tile. |
| SCALVS.ai | both | web shell | `app.scalvs` | https://scalvs.app |
| :\ARCHITECT\™ | both | web shell | `com.infinitum.architect` | https://rchtch.app . `cndctr.app` is an alias of this same site. |
| Firmament Network™ | both | web shell | none | https://firmnetwork.app |
| FIRM WALLET | both | web shell | none | https://firmwallet.app |
| Cumulus://Cloud | both | web shell | none | https://cumuluscloud.app and `drive.cumuluscloud.app` |
| Cumulus://Mail | both | web shell | `app.cumuluscloud.mail` | https://mail.cumuluscloud.app |
| Cumulus://ID | both | web shell | `id.cumuluscloud.app` | https://id.cumuluscloud.app . The live page title is +SPCTR™. |
| VEIL</>™ VPN | both | web shell | `com.infinitum.veilvpn` | https://infinitumveilvpn.app . The icon is the black wordmark V \| ™ on a white rounded tile, not the site mark. |
| VEIL browser | both | web shell | `app.infinitum.veil.browser` | https://infinitumveil.app |
| ei·det·ic™ | both | web shell | `com.infinitum.eidetic.cam` | https://infinitumcorporation.ai/products/eidetic |
| [HRVST] | both | web shell | `com.infinitum.hrvst.cam` | https://infinitumcorporation.ai/products/hrvst |
| Zerkalo | both | placeholder | none | Coming soon. No public page was found. |
| Flower | both | web shell | none | https://infinitumcorporation.ai/products/flower |
| POLLEN | both | web shell | none | https://infinitumcorporation.ai/products/pollen |
| Infinitum Academy | both | web shell | none | https://infinitum-academy.whop.site |
| Infinitum Corporation | both | web shell | none | https://infinitumcorporation.ai |
| Infinitum Defense | both | web shell | none | https://infinitumdefense.com |
| ÆTHER MIL | ^KETER | web shell | `com.infinitum.aether` | https://infinitumaether.app |
| 1MIND | ^KETER | placeholder | none | Coming soon. No public page was found. |
| ODIN° | ^KETER | web shell | none | https://infinitumcorporation.ai/products/odin |
| ODIN DAEMON | ^KETER | web shell | none | https://infinitumcorporation.ai/products/daemon . That page is titled DAEMON™. |
| >_ARGUS™ Military Edition | ^KETER | web shell | none | https://infinitumcorporation.ai/products/argus |
| E(O)N™ MIL | ^KETER | web shell | none | https://infinitumcorporation.ai/products/eon |

"Opens as" is the kind when that native package is not installed. An installed package opens as native instead.

Home icons sit in white rounded-squircle tiles. Catalog marks already in the tree stay as supplied: VEIL VPN is V | ™, FABRIC++ is ++™, Architect is a single entry, and the launcher adaptive icon is the supplied T]™ mark. The bundled face is Liberation Sans. Other installed apps use the monochrome layer painted black, or a black threshold when that layer is missing. A dock stays at the bottom of every launcher screen, including both pages, search, and About. The call button stays centered and opens the dialer with `ACTION_DIAL`. It does not request `CALL_PHONE`. Camera sits to the left of that button, a black circle with a white camera glyph, and opens `STILL_IMAGE_CAMERA`, then the default `ACTION_IMAGE_CAPTURE` app, then `com.transsion.camera` or any other capture handler. Messages sits to the right of the call button: a black circle with a white speech-bubble glyph, the same size as the call button. It opens the default SMS app (`Telephony.Sms.getDefaultSmsPackage`, then Google Messages, then any `smsto:` handler). METATRON, labeled METATRON, sits in Guard's previous INFINITUM slot and opens https://mttrn.app, or the installed METATRON app when one is present. The launcher does not show Guard. Grid tiles are white squircles with a drawn shadow and a hairline edge. Catalog marks stay the supplied black artwork. Other apps draw their adaptive-icon foreground inside that tile.

## Build

JDK 17 or 21, Android SDK 35.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew test assembleDebug
```

Debug APKs, flavor `keter`:

- `apps/throne-launcher/build/outputs/apk/keter/debug/`
- `apps/throne-guard/build/outputs/apk/keter/debug/`
- `apps/throne-vault-notes/build/outputs/apk/keter/debug/`
- `apps/throne-suite/build/outputs/apk/keter/debug/`
- `apps/nether/build/outputs/apk/keter/debug/`
- `apps/eidetic-cam/build/outputs/apk/keter/debug/`
- `apps/hrvst-cam/build/outputs/apk/keter/debug/`

^KETER APK names are `throne-launcher-keter-debug.apk`, `throne-guard-keter-debug.apk`, `throne-vault-notes-keter-debug.apk`, `throne-suite-keter-debug.apk`, `nether-keter-debug.apk`, `eidetic-cam-keter-debug.apk`, and `hrvst-cam-keter-debug.apk`. The `archangel` flavor writes the same layout with `archangel` in the path, including `throne-suite-archangel-debug.apk`. GitHub Actions (`.github/workflows/android-debug.yml`) runs the unit tests, assembles the debug APKs, and uploads `throne-keter-debug-apks` and `throne-archangel-debug-apks`, each including the camera apps. The camera apps use the same flavor ids and show **^KETER** and **ARCH//ANGEL**. If the repository variable `CI_RUNNER` is set, the job uses that runner label (for example `infinitum-ci`). If it is empty, the job uses `ubuntu-latest`.

## Install

On Windows, with one phone in `adb devices` and USB debugging on:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\km4-apply.ps1
```

`-Edition archangel` installs the ARCH//ANGEL debug APKs. The default is ^KETER, for William's KM4. The script installs the debug APKs (launcher, Guard, Vault, Suite, Nether, Eidetic and Harvest) with `adb install -r`, tries to grant the Home role, and opens the Home picker if that grant fails. It does not grant the camera permission. It prints the remaining manual steps.

It does not wipe the device, unlock the bootloader, root the phone, flash partitions, or remove packages. Debug APKs are signed with the shared debug key in `keystore/throne-debug.keystore` (alias `androiddebugkey`, store and key password `android`). That key is debug-only. CI and a local build produce the same signature, so a later build updates the installed app and Vault's data stays. The script does not uninstall. An APK already on the phone that was signed by an older one-off CI debug key cannot be replaced until that copy is removed by hand; Android rejects the signature change and leaves the app, including Vault, where it is.

## Manual steps the apps cannot do

1. Choose THRONE][OS™ in the Home picker if HiOS still asks.
2. In Guard, set a screen lock if it is missing.
3. Set Private DNS yourself. Suggested hostname: `dns.adguard-dns.com`. That resolver is not run by Infinitum. Guard cannot write the setting.
4. Install VEIL (`com.infinitum.veilvpn`) if it is missing. In VPN settings, turn on Always-on VPN and Block connections without VPN. Prefer VEIL as the only always-on VPN.
5. In Google Messages, turn on RCS Chat features yourself. Confirm encryption on a conversation, not only a “connected” status.
6. Hide lock-screen message previews in notification settings.
7. Turn developer options and USB debugging off when install work is finished.
8. Device-owner policy is not applied. Android refuses device owner while accounts exist, and getting there requires a factory reset this layer does not perform.

## Enforced and advisory

| Control | On this layer |
| --- | --- |
| Launcher cannot open sockets | Enforced. No network permission. |
| Vault cannot open sockets | Enforced. No network permission. |
| No widget host, ad app, or analytics SDK | Enforced by what is compiled. |
| ARCH//ANGEL omits ^KETER catalog entries from both pages | Enforced when the catalog is read. The JSON still lists them. |
| App backup of these packages | `android:allowBackup="false"`. |
| Vault and Guard block screenshots | `FLAG_SECURE`. |
| Private DNS | Advisory. The guide and the optional TCP/853 check do not change the setting. |
| Always-on VEIL and lockdown | Advisory. Clear only when the platform lets the guard read both flags and they name VEIL with lockdown on. Otherwise the guard says what it could and could not read. |
| Screen lock, encryption, developer options, USB debugging | Read, plus a settings link. Not changed by the app. |
| Accessibility, device admins, unknown-app installers, camera, microphone, location, per-app dangerous permissions | Read, plus a settings link. |
| Named Transsion analytics packages | Listed when installed. Not disabled. An OEM update can restore a privileged package. |
| RCS in Google Messages | Not readable and not switchable. The guard only opens Messages and explains the path. |
| SMS and MMS | Not encrypted by Messages. Fallback from RCS is outside this layer. |
| Vault ciphertext | Encrypted on this phone before it is shared. Messages then stores and may back up that ciphertext as ordinary text. |
| USB lockdown, policy wipe, hidden packages | Not in this layer. They need device owner, which needs a wipe William has not accepted. |
| hardened_malloc, MTE, relocked verified boot | Not available on the KM4. Those belong to the Pixel and GrapheneOS build. |

## Secure messaging

Google Messages (`com.google.android.apps.messaging`, named build `20261022`) is not modified. There is no root and no patch. Guard checks four things and states two limits:

- Whether Messages is the default SMS app. The button asks Android to offer that change. Guard does not become the SMS app and does not request `READ_SMS` or `SEND_SMS`.
- Whether the installed version date is `20261022` or newer. A newer build is fine. An unreadable version is reported as a note.
- How to turn on RCS Chat features. Guard cannot see the switch. End-to-end encryption applies to RCS chats with other RCS users when that conversation shows it. Metadata and Google’s RCS service are outside this claim.
- That plain SMS and MMS are not encrypted, including RCS fallback.
- Whether the global lock-screen flags hide sensitive notifications. Per-channel preview state inside Messages is not visible, because Guard is not a notification listener.

Vault is a separate app so the encryption code does not sit in the process that can open a socket.

**Passphrase messages.** UTF-8 passphrase, PBKDF2-HMAC-SHA256, 310,000 iterations, random 16-byte salt, AES-256-GCM with a random 12-byte nonce and a 128-bit tag. The iteration count is stored in the message and authenticated, and decryption refuses counts outside 100,000–600,000 so a pasted blob cannot force a huge derivation. Eight characters is the minimum the app will seal. A short passphrase is the weak point. Sending the passphrase by SMS removes the encryption.

**Contact messages.** Ephemeral X25519, shared secret rejected if it is all zeros, HKDF-SHA256, AES-256-GCM. The long-term private key is generated in Android Keystore when that device accepts an X25519 agreement key. Otherwise the PKCS8 private key is wrapped by a non-exportable Android Keystore AES-256-GCM key. StrongBox is not required and is not claimed. The public key is shown as a QR code and as `throne-vault:x25519:v1:…`, with a SHA-256 safety number. There is no camera scanner. Compare the safety number in person. A public key that arrives by SMS can be replaced in transit. The recipient’s static key can decrypt old messages if it is later copied. This is not the Signal protocol and not ÆTHER Talk.

**Notes on the device.** Sealed with the Keystore AES key. Not shared. A factory reset destroys them. Plaintext from Secure Compose is cleared from the form after encryption and is not written to the notebook. A kept copy is ciphertext only.

The shared text starts with `THRONEVAULT1.P.` or `THRONEVAULT1.X.` and a warning line. Share targets Google Messages when it is installed. Incoming text can be shared back into the vault. The vault does not read the SMS database, so it will not notice a ciphertext by itself.

310,000 PBKDF2 iterations is a mobile choice, not an OWASP certificate and not a FIPS module. A lab has not evaluated this construction.

## What this is not

This is not NIAP, FIPS, Common Criteria, or DISA STIG. It is not GrapheneOS. It does not sandbox Play services. It does not provide a duress PIN, a scrambled lock screen, or per-app network denial. The honest place for those controls is an unlockable Pixel on GrapheneOS, as the design describes. The KM4 bootloader stays locked.
