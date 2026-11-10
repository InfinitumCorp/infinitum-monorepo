# THRONE][OS™ editions

**Infinitum Corporation®**

> **CONFIDENTIAL — MASTER edition.** THRONE][OS™ MASTER is for William Aaron Turner only. Nobody else receives MASTER signing keys, policy profiles, wallpaper sets, preinstalled military apps, or MASTER builds.

Two editions are specified. MASTER is confidential. SENTINEL is the proposed name for a later security-minded edition; CITADEL is the fallback name if SENTINEL feels too close to a watchdog product.

## Comparison

| Topic | MASTER | SENTINEL |
| --- | --- | --- |
| Who gets it | William Aaron Turner only, one device at a time | Security-minded users and, later, staff or clients |
| Confidentiality | Confidential. Do not distribute. | May be shared only if William later authorizes a second edition |
| Tone | Maximum hardening, described without fake certifications | Hardened defaults, still usable every day |
| Best hardware | Unlockable Pixel 8a or newer with GrapheneOS; the TECNO KM4 is an interim host | Any modern Android phone via the launcher and Agent |
| Device owner | Yes, after a planned factory reset, if William accepts the wipe | Optional, off by default |
| Duress wipe | On, once a real lockscreen-path mechanism exists (native on GrapheneOS) | Off unless the user turns it on |
| Google apps | Kept, because William asked; sandboxed Play if MASTER moves to GrapheneOS | Optional; stock Play if they stay on ordinary Android |
| Telemetry | No Infinitum telemetry. Transsion analytics disabled. No crash beacons. | No Infinitum telemetry. OEM analytics disabled where the Agent can reach them. |
| MIL apps | ÆTHER MIL™ preinstalled. No other MIL Android APK was identified. | No military editions. Civilian counterparts only (FabriC++™, VEIL, Cumulus). |
| Signing | Separate MASTER signing key, not in the SENTINEL pipeline | Separate SENTINEL key so MASTER packages cannot be sideloaded from this track |

## Features by edition

| Feature | MASTER | SENTINEL |
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
| Sandboxed Google Play | When MASTER is on GrapheneOS | No, unless that user also runs GrapheneOS |

The full design, including KM4 vs Pixel feasibility, is in [DESIGN.md](DESIGN.md). Open decisions are in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md).
