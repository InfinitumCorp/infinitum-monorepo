# THRONE][OS™

**Infinitum Corporation®**

Private design documents for THRONE][OS™, the software layer of a THRONE PHONE™.

> **CONFIDENTIAL — ^KETER edition.** THRONE][OS™ ^KETER is for William Aaron Turner only. Nobody else receives the ^KETER signing keys, policy profile, wallpaper set, preinstalled military apps, or ^KETER builds. Do not copy ^KETER material into ARCH//ANGEL or any public channel.

This repository holds design documents only. It contains no source code, no signing keys, no passwords, and no credentials.

## Overview

THRONE][OS™ is Infinitum Corporation® branded Android software: a home screen, a hardening agent, and the Infinitum product suite, aligned with VEIL, Cumulus, and the rest of the Nexus. It is not a from-scratch operating system in version one. It is not a government-certified military product. “Military grade” is not a claim this project prints unless a named lab evaluates a named module, device, and configuration.

The first host under study is a TECNO Spark Go 2 (TECNO_KM4, KM4-RU, Android 15 / HiOS 15). On that phone the honest path is a launcher and agent on top of a locked bootloader, plus always-on VEIL VPN. The stronger ^KETER build, with OS-level exploit mitigations and a validated hardware root of trust, belongs on an unlockable Pixel running GrapheneOS.

## Editions

| Edition | Who it is for | Intent |
| --- | --- | --- |
| **^KETER** | William Aaron Turner only, one device at a time | Maximum hardening. **Confidential.** |
| **ARCH//ANGEL** | Public edition | Hardened defaults without military apps. |

See [docs/EDITIONS.md](docs/EDITIONS.md) for the full comparison.

## Branding

Infinitum Corporation® visual direction for THRONE][OS™, matching the other Infinitum products:

- White field (`#FFFFFF`). Black ink (`#161616`) and dark gray (`#5C5C5C`). Thin, clean type. Generous whitespace. No color accents.
- Monochrome glyphs: black on the white field. The KM4 apps draw a thin geometric lattice because press-kit fonts and logo binaries are not in this repository.
- Marks: THRONE][OS™ and THRONE PHONE™. The `][` mark distinguishes this phone software from Throne OS™ on the public #THRONE™ computer line (Book, Tower, Rack, and #THRONE MIL™).

## Documents

- [docs/DESIGN.md](docs/DESIGN.md) — full design, as of 10 Nov 2026, Europe/Moscow
- [docs/EDITIONS.md](docs/EDITIONS.md) — ^KETER vs ARCH//ANGEL
- [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) — decisions still waiting on William
- [docs/KM4-LAYER.md](docs/KM4-LAYER.md) — launcher, guard, and vault on the TECNO KM4

## Status

The KM4 layer is source in this repository: `apps/throne-launcher`, `apps/throne-guard`, `apps/throne-vault-notes`, `apps/throne-suite`, `apps/nether`, `apps/eidetic-cam`, and `apps/hrvst-cam`. The suite catalog is `apps/throne-suite/src/main/assets/products.json`. It has not been flashed. There are no signing keys and no certification claims. Build and install steps are in [docs/KM4-LAYER.md](docs/KM4-LAYER.md).

Eidetic (`com.infinitum.eidetic.cam`) captures a frame only for an enrolled person with written consent on file, asks the loopback eidetic desk to check the declared purpose, and queues the frame for a human reviewer. Harvest (`com.infinitum.hrvst.cam`) stores a capture as a Harvest Packet in an encrypted Personal Silo and shares a packet only with an explicit grant. Both show ^KETER or ARCH//ANGEL. The camera is foreground-only, with a visible CAMERA ACTIVE line while it is bound.

## Nether

Nether is the offline password vault for this phone. The standalone app is `app.infinitum.nether`. Cumulus://Suite opens the same vault from its Nether section. Crypto, storage, the generator, TOTP, and the screens live in `libs/nether-core` and `libs/nether-ui`. The vault file is AES-256-GCM. The key comes from Argon2id (Bouncy Castle). A copy of the key can be wrapped by the Android Keystore, StrongBox when the phone has it, and unlocked with BiometricPrompt. Nether has no INTERNET permission. Vault (`com.infinitum.throne.vault`) stays installed; the launcher hides it and puts Nether in that grid slot. Nether does not read Vault's private files.

Site: https://infinitumcorporation.ai/
