# Rules for Claude Code and all coding agents on THRONE][OS

## AUTO PUSH TO GITHUB (standing rule from William, 2026-11-11)
- After every change, commit and push to GitHub automatically. Don't ask first.
- Work on branch `cursor/throne-km4-layer-9d8d` (PR #1, titled "Throne OS. ^KETER"). Keep that PR title.
- Pull before you start so you're current with the cloud agents also pushing here.
- Never force-push or rewrite pushed history. Never merge a PR without William's explicit OK.
- Never commit secrets (keys, passwords, tokens, .env files). The shared debug keystore in `keystore/` is intentional.

## Product notes
- Editions: ^KETER (William's, shown as ^KETERv1) and ARCH//ANGEL (public). ^KETER and MIL apps stay off public sites.
- White, minimal, iOS "hydrated" style; black lettering on white rounded tiles; official Infinitum names and logos only.
- Never enable system grayscale on the phone.
- Nether (`app.infinitum.nether`, also a section inside Cumulus://Suite) is the password vault. It replaces Vault on the home grid. Leave `com.infinitum.throne.vault` installed and do not read or delete its private files. Nether has no INTERNET permission. The `{n}` mark is `libs/throne-ui/src/main/res/drawable-nodpi/ic_mark_nether.png` and the standalone adaptive icon is `design/nether/android` when that folder is present. Do not redraw those files.
