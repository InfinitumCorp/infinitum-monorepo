# THRONE][OS™ editions

**Infinitum Corporation®**

> **CONFIDENTIAL — ^KETER edition.** THRONE][OS™ ^KETER is for William Aaron Turner only. Nobody else receives ^KETER signing keys, policy profiles, wallpaper sets, preinstalled military apps, or ^KETER builds.

Two editions are specified. ^KETER is confidential and is the edition for William Aaron Turner's own phone, including the TECNO KM4. ARCH//ANGEL is the public edition: hardened defaults without military apps.

## Comparison

| Topic | ^KETER | ARCH//ANGEL |
| --- | --- | --- |
| Who gets it | William Aaron Turner only, one device at a time. This is the edition for his KM4. | The public edition |
| Confidentiality | Confidential. Do not distribute. | Public. No military apps and no ^KETER signing material. |
| Tone | Maximum hardening, described without fake certifications | Hardened defaults, still usable every day |
| Best hardware | Unlockable Pixel 8a or newer with GrapheneOS; the TECNO KM4 is an interim host | Any modern Android phone via the launcher and Agent |
| Device owner | Yes, after a planned factory reset, if William accepts the wipe | Optional, off by default |
| Duress wipe | On, once a real lockscreen-path mechanism exists (native on GrapheneOS) | Off unless the user turns it on |
| Google apps | Kept, because William asked; sandboxed Play if ^KETER moves to GrapheneOS | Optional; stock Play if they stay on ordinary Android |
| Telemetry | No Infinitum telemetry. Transsion analytics disabled. No crash beacons. | No Infinitum telemetry. OEM analytics disabled where the Agent can reach them. |
| MIL apps | ÆTHER MIL™ preinstalled. No other MIL Android APK was identified. | No military editions. Civilian counterparts only (FabriC++™, VEIL, Cumulus). |
| Signing | Separate ^KETER signing key, not in the ARCH//ANGEL pipeline | Separate ARCH//ANGEL key so ^KETER packages cannot be sideloaded from this track |

## Features by edition

| Feature | ^KETER | ARCH//ANGEL |
| --- | --- | --- |
| THRONE][OS™ launcher, two screens (INFINITUM and Others) | Yes | Yes |
| White field, black monochrome glyphs, no color accents | Yes | Yes |
| VEIL VPN always-on and kill switch | Yes, locked if device owner is set | Yes, user-controlled |
| VEIL Browser default | Yes | Yes |
| Cumulus Mail and Cumulus://ID | Yes | Yes |
| FabriC++, Architect, SCALVS, Conjure | Yes | Yes |
| ÆTHER MIL™ (`com.infinitum.aether`) | Preinstalled | Not included |
| Transsion analytics disabled | Yes | Yes |
| Tracker-blocking DNS | Yes, through VEIL or Private DNS | Yes |
| USB data lockdown | On after device owner, or native on GrapheneOS | Optional |
| Auto-reboot when unused | On | Optional |
| Duress PIN or wipe | On when the lockscreen path exists | Off by default |
| Scrambled PIN pad | Native on GrapheneOS; not an honest KM4 claim | No |
| Per-app network and sensors | Native on GrapheneOS; VPN-filter approximation on the KM4 | Same approximation on ordinary Android |
| Sandboxed Google Play | When ^KETER is on GrapheneOS | No, unless that user also runs GrapheneOS |

The full design, including KM4 vs Pixel feasibility, is in [DESIGN.md](DESIGN.md). Open decisions are in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md).
