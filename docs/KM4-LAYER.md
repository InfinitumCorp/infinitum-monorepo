# THRONE][OS™ on the TECNO KM4

**Infinitum Corporation®**

Path A from the design: a launcher, a guard, and a vault on locked HiOS 15. The repo name stays `throne-os`. Nothing here flashes firmware, unlocks the bootloader, or claims a certification.

The product name is **THRONE][OS™**. The KM4 layer uses the white Infinitum field: background `#FFFFFF`, ink `#161616`, secondary `#5C5C5C`, thin system type, and no color accents. Press-kit fonts and logo binaries are not in git. The apps draw a geometric lattice in vectors.

## What is installed

| App | Package | Role |
| --- | --- | --- |
| THRONE][OS™ | `com.infinitum.throne.launcher` | Home screen. Pages INFINITUM and OTHERS. Black glyphs on white. No widgets, ads, analytics, or network permission. |
| THRONE][OS™ Guard | `com.infinitum.throne.guard` | Local audit and settings links. One optional, user-started Private DNS reachability check. |
| THRONE][OS™ Vault | `com.infinitum.throne.vault` | Encrypts text on the phone and can hand ciphertext to Google Messages. No network permission. |

Editions are build flavors. **MASTER** shows ÆTHER MIL (`com.infinitum.aether`) on INFINITUM. **SENTINEL** hides that package on both pages. A bad edition flag fails closed to SENTINEL. Debug APKs are signed with the Android debug key. They are not MASTER signing keys, and those keys are not in this repository.

The INFINITUM page, in order, when the package is installed: VEIL VPN, Cumulus Mail, SCALVS, FabriC++, ÆTHER MIL (MASTER only), Architect, Conjure, VEIL Browser, `com.infinitum.eidetic.cam`, `com.infinitum.hrvst.cam`, Cumulus ID (`id.cumuluscloud.app`), Guard, and Vault. Everything else launchable is OTHERS. The launcher hides itself.

Icons use the adaptive-icon monochrome layer on Android 13 and later when the app supplies one. Otherwise the glyph is a desaturate-and-threshold fallback. Full-bleed color icons can become coarse silhouettes.

## Build

JDK 17 or 21, Android SDK 35.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew test assembleDebug
```

Debug APKs:

- `apps/throne-launcher/build/outputs/apk/master/debug/`
- `apps/throne-guard/build/outputs/apk/master/debug/`
- `apps/throne-vault-notes/build/outputs/apk/master/debug/`

The `sentinel` flavor writes the same layout with `sentinel` in the path. GitHub Actions (`.github/workflows/android-debug.yml`) runs the unit tests, assembles the debug APKs, and uploads them. If the repository variable `CI_RUNNER` is set, the job uses that runner label (for example `infinitum-ci`). If it is empty, the job uses `ubuntu-latest`.

## Install

On Windows, with one phone in `adb devices` and USB debugging on:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\km4-apply.ps1
```

`-Edition sentinel` installs the SENTINEL debug APKs. The script installs the three debug APKs with `adb install -r`, tries to grant the Home role, and opens the Home picker if that grant fails. It prints the remaining manual steps.

It does not wipe the device, unlock the bootloader, root the phone, flash partitions, or remove packages. A signature mismatch on reinstall is reported and left alone.

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
| SENTINEL omits ÆTHER MIL from the home grid | Enforced at build time. |
| App backup of these three packages | `android:allowBackup="false"`. |
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
