# THRONE][OS™ design

**Infinitum Corporation®**

> **CONFIDENTIAL — ^KETER edition.** THRONE][OS™ ^KETER is for William Aaron Turner only. Nobody else receives the ^KETER signing keys, policy profile, wallpaper set, preinstalled military apps, or ^KETER builds.

A private design for THRONE][OS™, the software side of a THRONE PHONE™, for William Aaron Turner. It is honest about what a TECNO KM4 can do and what only a Pixel running GrapheneOS can do.

Design as of 10 Nov 2026, Europe/Moscow. This document is the Markdown form of the THRONE][OS™ design Page. The KM4 layer in [KM4-LAYER.md](KM4-LAYER.md) can be compiled as debug APKs. Nothing in this repository has been flashed, and nothing here is a certification.

## Contents

- [The honest verdict](#the-honest-verdict)
- [What THRONE][OS™ is](#what-throneos-is)
- [The phone we have](#the-phone-we-have)
- [What military grade can mean](#what-military-grade-can-mean)
- [Three build paths](#three-build-paths)
- [Two editions](#two-editions)
- [Feature set](#feature-set)
- [Infinitum products and MIL apps](#infinitum-products-and-mil-apps)
- [Architecture](#architecture)
- [Brand and logo concepts](#brand-and-logo-concepts)
- [Phased build plan](#phased-build-plan)
- [Open decisions](#open-decisions)
- [Sources](#sources)

## The honest verdict

The KM4 cannot become a certified military operating system.

THRONE][OS™ ^KETER on this TECNO is a branded launcher, a hardening agent, always-on VEIL VPN, and Infinitum’s own apps on top of locked HiOS 15. That is real hardening, not theater, and it is also not NIAP, FIPS, or DISA certification. The build nobody else should have, with OS-level exploit mitigations and a hardware root of trust that has actually been validated, belongs on an unlockable Pixel running GrapheneOS.

William Aaron Turner, 10 Nov 2026:

> Use our Infinitum branding and create better logos as well. We need tons of security feature that align with our products for our OS. REAL encryption, hardened, no trackers. This OS will be the master version. For me, William Aaron Turner. The master of INFINITUM CORPORATION. Nobody will have this build.

## What THRONE][OS™ is

THRONE][OS™ is the software layer of a THRONE PHONE™: Infinitum’s own home screen, policy agent, and product suite, aligned with VEIL, Cumulus, and the rest of the Nexus. It is a private design for William Aaron Turner, President of Infinitum Corporation®.

The public #THRONE™ line on infinitumcorporation.ai is already a family of AI computers (Book, Tower, Rack) that ship with Throne OS™, a hardened Linux system. #THRONE MIL™ is the rugged laptop and transit-case server for authorized defense users, shipping with the ODIN°™ stack and ÆTHER MIL™. THRONE][OS™ and THRONE PHONE™ are the handheld counterparts of that line, using the `][` mark so the phone OS is not confused with Throne OS™ on the computers.

| | |
| --- | --- |
| Owner | William Aaron Turner, sole holder of the ^KETER build |
| Company | Infinitum Corporation® |
| Marks | THRONE][OS™, THRONE PHONE™, related to #THRONE™ / Throne OS™ |
| Editions | ^KETER (William only, confidential) and ARCH//ANGEL (security-minded edition for others) |
| Date | Design as of 10 Nov 2026, Europe/Moscow |
| Site | https://infinitumcorporation.ai/ |

## The phone we have

The current THRONE PHONE candidate is the handset already on William’s desk: a TECNO Spark Go 2 sold as model TECNO_KM4, product KM4-RU, running Android 15 with HiOS 15. Public spec sheets list a Unisoc T7250 (2× Cortex-A75 at 1.8 GHz plus 6× Cortex-A55 at 1.6 GHz, Mali-G57, 12 nm), 3 or 4 GB of LPDDR4X, eMMC 5.1 storage, a 6.67-inch 720×1600 120 Hz panel, a 5000 mAh battery, dual nano-SIM, and USB-C. Community flashing write-ups map the same KM4 to Unisoc Tiger T616 / UMS9230. Those two names should be treated as a discrepancy to resolve on-device (`getprop ro.board.platform` and `ro.soc.model`), not as two different phones.

| | |
| --- | --- |
| Model | TECNO Spark Go 2 / TECNO_KM4 / KM4-RU |
| OS today | Android 15, HiOS 15, verified boot still OEM-signed if the loader stays locked |
| SoC | Unisoc T7250 in spec sheets; Tiger T616 / UMS9230 in flashing tools |
| Memory | 3 or 4 GB RAM, eMMC storage — tight for a heavy custom ROM |
| Architecture | ARMv8.2 Cortex-A75/A55. No ARM Memory Tagging Extension, which arrives with ARMv8.5 / ARMv9 |
| StrongBox | Unknown on this Unisoc. ÆTHER MIL already verified TEE-backed keys on this KM4; it uses StrongBox only when the hardware presents one |
| Bootloader | Very likely locked. Unisoc platforms are widely reported as not officially unlockable. Unofficial tools exist and have also bricked KM4 units while rewriting the dynamic super partition |

Unlocking, if a method even works on this unit, wipes userdata, trips the verified-boot state, and may void the warranty. Relocking a custom image with a healthy Android Verified Boot chain is poorly supported on Unisoc compared with Pixel. For a daily driver that must keep cellular service in the KM4-RU configuration, unlocking to flash a generic system image is the riskier path, not the safer one.

## What military grade can mean

“Military grade” is marketing language unless it maps to a named standard and a named evaluation. Infinitum should not print FIPS, NIAP, Common Criteria, or DISA STIG marks on THRONE][OS™ until a laboratory has evaluated a specific module, device, and configuration. The table below is the honest mapping.

| Standard | What it actually is | On the KM4 | On a Pixel |
| --- | --- | --- | --- |
| FIPS 140-3 | Validation of a specific cryptographic module, not of an app or a skin. Pixel Android 15 lists Android BoringCrypto as CMVP certificate 5244, with Titan M2 / Trusty also under CAVP and related evaluations. | No known FIPS module listing for this Unisoc or for HiOS. Apps can still use the Android Keystore / TEE. | Use the already-validated platform modules. A THRONE app that calls those modules is not itself FIPS certified. |
| NIAP MDFPP | Protection Profile for Mobile Device Fundamentals. Pixel devices on Android 15 are listed as VID11545 against MDFPP 3.3 plus related modules (Bluetooth, WLAN, MDM agent, TLS). | No NIAP listing found for TECNO KM4 or HiOS 15. | The certified target is Google Android on Pixel, not GrapheneOS and not a custom launcher. |
| DISA STIG, Android 15 | Configuration guidance for Google Android 15 used with CUI or below, in COPE or COBO, built from the MDFPP 3.3 STIG template. It assumes Android Enterprise work-profile or fully managed device controls, including Common Criteria Mode on Pixel. | Some individual settings can be mirrored with a device owner. The phone cannot be called STIG-compliant. | Many STIG items are achievable on stock Pixel Android Enterprise. GrapheneOS is a different OS and is not the STIG target. |
| GrapheneOS hardening | hardened_malloc, hardened libc, hardware MTE on Pixel 8 and later, sandboxed Google Play, duress PIN, scrambled PIN, per-app network and sensor permissions, USB-C port control, auto-reboot, improved verified boot with a relocked custom root of trust. | Not portable to TECNO. GrapheneOS supports Pixel 6 through Pixel 10a only. | This is the closest public production system to the ^KETER brief. It is still not a government certification. |

^KETER should be described as maximum hardening for a single owner, informed by those standards, not as a certified military product. ARCH//ANGEL should be described as security-minded. Both lines stay out of export-control and accreditation claims until counsel and a lab say otherwise.

## Three build paths

There are three technically real ways to put THRONE][OS™ on a phone. They are not equal, and they should not be sold as if they were.

### Path A. Launcher plus hardening agent

A THRONE][OS™ launcher replaces the HiOS home screen with an INFINITUM screen and an Others screen. A THRONE Agent app applies whatever Android will allow without rewriting the OS: always-on VPN through VEIL, Private DNS, a restricted profile of settings, leftover Transsion package disables, optional device-owner policies, and quick launch into VEIL, Cumulus, and ÆTHER MIL. The bootloader stays locked, so OEM verified boot stays intact. This is the only path that is safe to start on the KM4 without wiping it.

Full device-owner power (`adb shell dpm set-device-owner` against a DevicePolicyManager receiver) is stronger: USB data off, camera policy, password quality, screen capture off, application hiding, always-on VPN lock, remote wipe. Android will accept that command only on an unprovisioned device with no accounts. William’s KM4 already has accounts, so true device-owner mode means a factory reset, then installing the Agent, then setting device owner, then signing back into Google and the rest. That is a deliberate ^KETER step, not a background tweak.

### Path B. Custom ROM or Treble GSI

Project Treble generic system images, or a from-source HiOS replacement, require an unlocked bootloader and working Unisoc device trees, vendor blobs, and radio firmware. Official OEM unlocking on Transsion Unisoc devices is widely reported as unavailable at the SoC level. Unofficial unlock and flash tools exist; an XDA report on a KM4 that was unlocked that way describes a failed super-partition rewrite and a “NO VALID OS FOUND” brick. Even a successful GSI would drop HiOS radio integration, lose a clean verified-boot lock, and still would not provide GrapheneOS mitigations or Pixel’s Titan secure element. Path B is research, not the ^KETER daily driver.

### Path C. Pixel plus GrapheneOS

GrapheneOS official production support, as of this design, covers Pixel 6 through Pixel 10a, including Pixel 10, 10 Pro, 10 Pro XL, 10 Pro Fold, and 10a. Current-generation advice is Pixel 8 and later: seven years of updates from launch, hardware memory tagging, pointer authentication, and branch target identification. Buy an unlockable, non-carrier-locked device. After install, the bootloader is relocked against GrapheneOS keys, so verified boot is stronger than an unlocked experiment and different from stock Google Android. This is the recommended hardware for the true ^KETER build. ARCH//ANGEL can stay on ordinary Android phones via Path A.

Stay on the KM4 for now:

- No wipe, no brick risk, cellular and HiOS radio stay as they are
- ÆTHER MIL is already installed and was verified on this exact KM4
- Google apps William wants to keep today continue to work as they do now
- Launcher, Agent, VEIL, and branding can ship while a Pixel is sourced

Move ^KETER to a Pixel:

- Requires buying an unlockable Pixel 8a or newer (Pixel 9a or 10-series if purchasing new)
- Install and relock take a dedicated session; eSIM and carrier quirks need a check
- Sandboxed Google Play keeps Gmail and Play, but some privileged Play features go away
- The KM4 can remain an ARCH//ANGEL or travel handset rather than the ^KETER device

## Two editions

^KETER is William’s personal build, including the KM4 he has now. It is confidential. Nobody else receives its signing keys, policy profile, wallpaper set, or preinstalled military apps. ARCH//ANGEL is the public edition other people can run. It keeps the hardened defaults and leaves the military apps out.

The comparison tables live in [EDITIONS.md](EDITIONS.md).

## Feature set

Features are grouped so the KM4 can be judged separately from a Pixel. Partial on the KM4 means the Agent or VEIL can approximate the control without owning the OS. Pixel plus GrapheneOS is the column where the control is native. Edition columns are in [EDITIONS.md](EDITIONS.md).

### Encryption

| Control | KM4 | Pixel and GrapheneOS |
| --- | --- | --- |
| File-based encryption | Yes. Android 15 encrypts credentials and device storage by default. | Yes, with GrapheneOS improvements around the lockscreen and profiles. |
| Hardware-backed keys | TEE yes for ÆTHER MIL on this phone. StrongBox is used only if the Unisoc presents it. | Titan secure element / StrongBox. Pixel Titan M2 has FIPS and Common Criteria listings on Google’s certification page. |
| Encrypted backups | App-level only. Disable unencrypted Google backups in the Agent. Seedvault-style backups are not built into HiOS. | Seedvault encrypted backups. GrapheneOS does not send device backups to Google. |
| End-to-end messaging | FabriC++ (Matrix E2E) and ÆTHER Talk inside ÆTHER MIL (Noise with an ML-KEM-768 hybrid session). | Same Infinitum apps, on a harder OS base. |
| VPN encryption in transit | VEIL VPN (`com.infinitum.veilvpn`) as always-on VPN with Android’s lockdown/kill switch. | Same, and GrapheneOS can also deny network to any app that should not talk at all. |

### Hardened

| Control | KM4 | Pixel and GrapheneOS |
| --- | --- | --- |
| Verified boot | Keep the loader locked. Unlocking to flash a GSI would weaken this. | Relock against GrapheneOS keys after install. |
| hardened_malloc and hardened libc | No. These are OS allocator changes, not something a launcher can add. | Yes, native in GrapheneOS. |
| Exploit mitigations (MTE, PAC, BTI) | No hardware MTE on this 12 nm Cortex-A75/A55 chip. | Yes on Pixel 8 and later, used by default in GrapheneOS. |
| USB lockdown | Charging-only after device owner (`setUsbDataSignalingEnabled(false)` and related restrictions). Until then, a settings discipline only. | Native USB-C port control, defaulting away from file transfer when locked. |
| Auto-reboot | Agent can schedule a reboot after a set idle time. Not as deep as an OS policy. | Native unused-device auto-reboot. |
| Duress PIN or wipe | A panic tile or Agent password can call wipe only after device owner is set, and not from the real lockscreen credential path. Do not advertise this as GrapheneOS-equivalent. | Native duress PIN and password that irreversibly wipe the device, including installed eSIMs. |
| Per-app network permission | Not a stock Android toggle. Approximate with VEIL / a local VPN filter, which is weaker than an OS permission. | Native GrapheneOS network permission. |
| Sensor kill switches | Device owner can disable the camera globally. Microphone, location, and extra sensors are permission and settings work. This phone has no physical camera or mic switches. | Per-app sensors permission, plus Pixel hardware privacy switches on many models. |
| Scrambled PIN | A launcher cannot replace Android’s real keyguard. Claiming a scrambled pad on HiOS would be dishonest. | Native GrapheneOS PIN scrambling. |
| Lockdown mode | AOSP lockdown (hold power, disable biometrics and notifications until PIN) may be present or hidden by HiOS. Confirm on-device before claiming it. | Present, with GrapheneOS lockscreen hardening around it. |

### No trackers

| Control | KM4 | Pixel and GrapheneOS |
| --- | --- | --- |
| Infinitum telemetry | None in THRONE][OS™, the Agent, or the preinstalled Infinitum apps as designed. | Same. |
| Transsion / HiOS analytics | Disable leftover packages the earlier cleanup did not remove, including known telemetry such as `com.transsion.trancare`, `com.transsion.hamal`, `com.transsion.statisticalsales`, `com.transsion.tecnospot`, and `com.hoffnung` (Athena / shalltry.com). Privileged packages can return after an OEM update. | No Transsion code. GrapheneOS does not include OEM analytics. |
| Tracker-blocking DNS | Private DNS (DNS over TLS) and VEIL’s resolver. Not a substitute for per-app network denial. | Same, plus Vanadium and per-app network permission. |
| Google services | William asked to keep Google apps. On HiOS they remain privileged Play services. The Agent should not pretend they are sandboxed. | Optional sandboxed Google Play: ordinary apps with no privileged OS role, confinable to one profile. |

## Infinitum products and MIL apps

Military editions on the public site are reserved for authorized defense users, sit outside the token economy, and have no commercial twin under the same name. Only one of them is a confirmed Android application with a package name, a repository, and an install already on this KM4. Other MIL names below are real Infinitum products, but they are hardware, backends, or suite layers, not phone APKs that this search could verify. Nothing else was invented.

### Confirmed Android MIL app

| | |
| --- | --- |
| Name | ÆTHER MIL™ / ÆTHER Mobile MIL™ |
| Label on device | ÆTHER MIL |
| Package | `com.infinitum.aether` |
| What it is | Hardened field communications and mesh networking; the communications layer of ODIN°™. Secure voice, text and data, with no commercial edition. |
| Sources | Already installed on the KM4; product page https://infinitumcorporation.ai/products/aether (title ÆTHER MIL™, system table lists ÆTHER Mobile MIL™ as the organization-controlled Android application); private repo InfinitumCorp/aether README and `android/app/build.gradle.kts` (`applicationId = com.infinitum.aether`); public site https://infinitumaether.app |
| What the repo says works today | TEE-backed (StrongBox when present) identity in ÆTHER Vault™, single-hop ÆTHER Mesh™, ÆTHER Talk™ text with a hybrid post-quantum Noise session, device certificates, safety numbers, Key Drop, burner numbers, `FLAG_SECURE` in release, no app-data backup. Verified on a TECNO KM4 (Android 15, arm64): all 5 on-device checks passed with TEE-backed keys. |
| ^KETER | Preinstalled, pinned on the INFINITUM screen, allowed to run in the background (HiOS otherwise freezes it) |
| ARCH//ANGEL | Do not preinstall. The civilian counterpart is FabriC++™ |

### Related MIL products, not confirmed as phone apps

| Product | What the sources say | Phone APK found? |
| --- | --- | --- |
| ODIN°™ | Complete military command, intelligence and autonomy suite. Military use only. Repo InfinitumCorp/odin is an exercise backend (TypeScript). | No Android package found |
| >_ARGUS MIL™ | Observe layer of ODIN°™: sensor fusion, tracking, advisory alerts. Product page plus InfinitumCorp/argus-military-reference and argus-odin-reference (Python reference implementations, military use only). | No Android package found |
| E(O)N MIL™ | Wearable electronic-warfare and counter-drone platform. Product page; InfinitumCorp/eon is described as an E(O)N™ MIL exercise backend and console, simulation only. | No Android package found |
| #THRONE MIL™ | Rugged laptops and transit-case servers that ship with the ODIN°™ stack and ÆTHER MIL™. This is hardware, not a phone image. | Not a phone app |
| DAEMON MIL™ series | CHERUBIM™, SERAPHIM™ and AZRAIL MIL™ aircraft, plus SLUG™, POLLEN™, Flower™, ORBIS™. Military hardware inside ODIN°™. | Not phone apps |
| MIL-branded companions | The site names Cumulus://ID MIL™, Cumulus://Cloud MIL™, Firmament Network MIL™, [HRVST] MIL™, and SCALVS.ai MIL™ as suite pieces. No separate Android package IDs turned up in the product HTML or the GitHub org search. | Unconfirmed; open question |

### Civilian Infinitum apps already on the KM4

| App | Package | Role on THRONE][OS™ |
| --- | --- | --- |
| VEIL VPN | `com.infinitum.veilvpn` | Always-on VPN and kill switch on both editions |
| VEIL Browser | `app.infinitum.veil.browser` | Default browser on both editions |
| Cumulus Mail | `app.cumuluscloud.mail` | Private mail on both editions |
| Sign in with Cumulus://ID | `id.cumuluscloud.app` | Identity on both editions; MIL credential branding stays off ARCH//ANGEL |
| FabriC++ | `com.infinitum.fabric` | Civilian E2E messaging, voice and video; the non-military counterpart to ÆTHER MIL |
| Architect | `com.infinitum.architect` | AI execution workspace on both editions |
| SCALVS | `app.scalvs` | AI workforce client on both editions |
| Conjure | `com.conjure.app` | Live prediction client on both editions |
| ÆTHER MIL | `com.infinitum.aether` | ^KETER only |

GitHub counterparts used for descriptions: InfinitumCorp/veil-vpn, InfinitumCorp/veil-phase1 (VEIL Browser), InfinitumCorp/cumulus-mail, InfinitumCorp/fabric (Android and iPhone Matrix messaging), InfinitumCorp/architect, InfinitumCorp/scalvs-ai, InfinitumCorp/Conjure, InfinitumCorp/aether.

## Architecture

THRONE][OS™ is three pieces on top of a base OS William already has, or on GrapheneOS if ^KETER moves. No path rewrites the Unisoc boot chain in version one.

```
William Aaron Turner (sole ^KETER owner)
        |                         |
        v                         v
THRONE][OS™ launcher          THRONE Agent
 INFINITUM + Others      DevicePolicyManager
        |                         |
        |            +------------+
        |            |
        v            v
 Civilian suite     Base OS (locked HiOS 15, or GrapheneOS on Pixel)
 VEIL Browser,      always-on VEIL VPN + kill switch
 Cumulus, Fabric,
 Architect, SCALVS,
 Conjure
        |
        v
 ÆTHER MIL (^KETER only)
 All app traffic through VEIL
```

**Launcher.** Replaces only the home screen. Screen one, INFINITUM, holds VEIL VPN, VEIL Browser, Cumulus Mail, Cumulus://ID, Fabric, Architect, SCALVS, Conjure, and on ^KETER, ÆTHER MIL. Screen two, Others, holds Phone, Messages, Camera, Clock, Calculator, Settings, Google apps William is keeping, plus X, Telegram, MAX, Outlook, Happ, and Miracast. Quick tiles arm VEIL and open Cumulus.

**Agent.** A first-party device policy controller. Without device owner it still drives Private DNS, VPN checks, unused-app reboot prompts, and package disables. With device owner it can lock USB data, password quality, screen capture, camera policy, application hiding, always-on VPN, and wipe.

**VEIL.** The network enforcement point on both editions. Always-on VPN plus Android’s VPN lockdown so apps cannot bypass the tunnel when VEIL drops. Happ remains installed because William asked to keep it; ^KETER should still prefer VEIL as the system VPN so two tunnels do not fight.

## Brand and logo concepts

Existing Infinitum marks are a square lattice of two overlapping diamonds that form an X, paired with an angular cut INFINITUM wordmark and CORPORATION underneath. Those assets live in the Infinitum press kit (white and black lattice marks and wordmarks) and the public site brand set.

Production chrome for the KM4 layer, set by William: a white field (`#FFFFFF`), black and dark-gray ink, thin type, generous whitespace, and no color accents. The earlier gold and red studies below are not what the launcher, guard, or vault ship. Press-kit fonts and logo binaries stay out of git. The apps draw a thin geometric lattice and set THRONE][OS™ in the system sans.

| Concept | Mark | Type | Palette |
| --- | --- | --- | --- |
| Lattice throne | The existing diamond lattice seated on a short gold plinth, as if the mark were a throne seen from the front. No extra crown, no eagles. | The angular INFINITUM letters unused here. Caption in tracked caps: THRONE][OS™ under the plinth, CORPORATION dropped. | White or gold lattice and plinth on void black. Ivory for light mode. Red pinstripe only on the ^KETER splash. |
| Double slash | Two parallel gold bars, slightly raked, cut through the lattice like a portcullis or a rank stripe. The bars are the // in the name. | THRONE in the existing angular face, then //OS in a narrower cut of the same face, white or gold on black. | Black field, white Infinitum mark and word, optional gold // bars, ivory secondary text. Red reserved for ^KETER builds and duress/wipe screens. |
| Sealed lattice | The lattice inside a thin circular seal, broken at the lower right by a small registration notch so it does not look like a generic police badge. | Around the ring: INFINITUM CORPORATION above, THRONE][OS™ below. ^KETER seals add a serial field; ARCH//ANGEL seals do not. | White lattice on black for both editions. ^KETER inner ring in red. ARCH//ANGEL inner ring in ivory or gold only. |
| Command wordmark | No new pictogram. The current horizontal lockup (lattice plus INFINITUM CORPORATION) with a second line, THRONE][OS™, set in the same angular face and optical size as CORPORATION. | Best for the boot splash, settings About screen, and printed one-pagers. Pairs with any of the three marks above as the app icon. | Clean white wordmark on black for the splash and chrome. Optional gold on black for a lockscreen that matches the present wallpaper. |

Recommended pairing: Double slash as the launcher icon, command wordmark on the splash and About screen, sealed lattice only if William wants a ^KETER-specific crest that ARCH//ANGEL is not allowed to draw. Do not invent a new letter style. The current Infinitum face is already distinctive.

## Phased build plan

Nothing in this plan flashes firmware, contacts a vendor, or changes another repository. Each phase waits on William’s say-so. Cyber work stays on Infinitum’s own apps and on Android public APIs.

1. **Phase 0. Design freeze.** This document. Confirm edition names, the Pixel question, and whether a factory reset for device owner is acceptable on ^KETER.
2. **Phase 1. Launcher on the KM4.** Ship THRONE][OS™ as an Android launcher: INFINITUM and Others screens, white field, black monochrome glyphs, ÆTHER MIL pinned only on ^KETER. No wipe. HiOS stays underneath. The source for this phase, the guard, and the vault is in [KM4-LAYER.md](KM4-LAYER.md).
3. **Phase 2. Agent without device owner.** Private DNS, VEIL always-on checks, disable leftover Transsion analytics packages, reboot reminder, status dashboard. Works on the phone as it stands today.
4. **Phase 3. Optional ^KETER device owner.** A planned factory reset, no accounts, install the Agent, `dpm set-device-owner`, then restore Google and Infinitum apps. Enables USB lockdown, capture blocks, password quality, hidden packages, and a policy-level wipe. Skip this until William explicitly accepts the wipe.
5. **Phase 4. ^KETER on Pixel and GrapheneOS.** Source an unlockable Pixel 8a or newer. Install GrapheneOS, relock, enable sandboxed Play if Google apps stay, install the Infinitum suite and ÆTHER MIL, apply the ^KETER policy profile. This is the build that can honestly carry the stronger hardening claims.
6. **Phase 5. ARCH//ANGEL package.** The public edition: the same launcher and Agent without ÆTHER MIL, without duress wipe by default, and without ^KETER signing keys.

## Open decisions

The numbered questions for William are in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md).

## Sources

Product and package facts come from Infinitum’s own site source and from the InfinitumCorp GitHub organization, read only. Device and standard facts come from the public pages below, checked on 10 Nov 2026.

- Infinitum Corporation: https://infinitumcorporation.ai/
- ÆTHER MIL product page: https://infinitumcorporation.ai/products/aether
- ODIN° product page: https://infinitumcorporation.ai/products/odin
- >_ARGUS MIL product page: https://infinitumcorporation.ai/products/argus
- E(O)N MIL product page: https://infinitumcorporation.ai/products/eon
- #THRONE and Throne OS product page: https://infinitumcorporation.ai/products/throne
- GrapheneOS supported devices: https://grapheneos.org/faq
- GrapheneOS features: https://grapheneos.org/features
- Pixel security certifications: https://support.google.com/pixelphone/answer/11062200
- Set up a device owner for testing: https://source.android.com/docs/devices/admin/testing-setup
- Android device control policies: https://developer.android.com/work/dpc/device-management
- TECNO Spark Go 2 (KM4) specifications: https://en.kalvo.com/tecno-spark-go-2-180425.html
- KM4 unlock and super-partition brick report: https://xdaforums.com/t/closed-tecno-spark-go-2-km4-unbrick-flashing-failure-report.4783276/
- Transsion / Unisoc bootloader notes: https://deepwiki.com/zenfyrdev/bootloader-unlock-wall-of-shame/2.20-tecno-infinix-and-itel

Private GitHub, read only: InfinitumCorp/aether (README and `android/app/build.gradle.kts`), InfinitumCorp/argus-military-reference, InfinitumCorp/argus-odin-reference, InfinitumCorp/eon, InfinitumCorp/odin, InfinitumCorp/fabric, InfinitumCorp/veil-vpn, InfinitumCorp/veil-phase1, InfinitumCorp/cumulus-mail, InfinitumCorp/architect, InfinitumCorp/scalvs-ai, InfinitumCorp/Conjure.
