# Cloudflare: Infinitum product servers (USA)

Decision (William, 2026-11-12): every Infinitum product runs on Cloudflare. Users signing in from anywhere except Russia connect to US servers. Russia keeps its own copies on Yandex Cloud (see `yandex-cloud.md`).

## How "US servers" works on Cloudflare

Each product Worker sets Workers placement to an explicit US region:

```jsonc
"placement": { "region": "gcp:us-east4" }   // Ashburn, Virginia
```

The Worker's code then runs in Cloudflare data centres next to us-east4, wherever the visitor is. Static files (HTML, JS, images) are still served from the data centre nearest the visitor. That is Cloudflare's behaviour for static assets and it only makes pages load faster; sign-in, APIs and data all run in the US.

Set on 2026-11-12 (pushed to `main`, so Workers Builds deploys it):

| Repo | Worker(s) | Domain |
|---|---|---|
| architect | `architect` | rchtch.app |
| scalvs-ai | `scalvs-ai` | scalvs.app |
| firmament-launch | `firmament-launch` | firmnetwork.app, firmwallet.app |
| infinitum-academy | `infinitum-academy` | |
| infinitum-revenue | `infinitum-revenue` | revenue API |
| infinitum-nexus | `cumulus-id` (apps/spectre), `cumulus-mail`, `cumulus-downloads`, `firm-wallet`, `infinitum-veil-web` | id., mail., downloads.cumuluscloud.app, infinitumveil.app |
| cumulus-exchange | `cumulus-cloud` (cloud/worker) | cloud.cumuluscloud.app |
| cumulus-mail | `cumulus-mail-api` | |
| odin-daemon, odin, eon | `odin-daemon`, `odin`, `eon` | |

Not changed:

- Static-only sites (`cumulus-site` on cumuluscloud.app, `fabric-site` on infinitumfabric.app) have no Worker code, so there is nothing to place.
- `veil-crawler` (cron only, no visitors) and `cumulus-mail-inbound` (email handler): placement only affects fetch handlers.
- Personal sites (vyral-london, william-aaron-turner, prpgnd, capital-chronicles) are not Infinitum products.

Check a deployed Worker: `curl -sI https://<domain>/<api path> | grep -i cf-placement`. `remote-IAD` (or another US airport code) means the request ran in the US.

## Live status (2026-11-12)

Placement `gcp:us-east4` was set on the live Workers through the API (settings only, same code): architect, firmament-launch, infinitum-revenue (Workers Builds), cumulus-id, cumulus-mail, cumulus-downloads, cumulus-cloud, cumulus-site, firm-wallet, infinitum-veil-web, veil-vpn-api, veil-vpn-site, infinitum-corporation-site, infinitum-app-hub, infinitum-developer-site, infinitum-payments-site, infinitum-intake, infinitum-extensions, metatron-site, fabric-site, aether-site, aether-mil-web, infinitum-defense-site, odin-daemon, eon.

Not yet:

- `scalvs-ai`: the newest uploaded versions are previews from `feat/web-search`, so a settings-only edit is refused (error 10214). `main` already has the placement setting; it takes effect with the next signed-off production deploy (`npm run deploy`). `main` also carries SCALVS Search (#25), which is not live yet.
- `conjure-llm`: the settings edit is refused because the live script's `Budget` Durable Object class is not in its config (error 100402). Fix that config, then redeploy.
- `odin`, `infinitum-academy`: not deployed on this account.

## Russia → Russia copies

`infra/ru-geo-redirect.mjs` adds one Single Redirect rule per zone (ref `infinitum-ru-geo`): a request from Russia (`ip.src.country eq "RU"`) to the main hostname gets a 302 to the `ru.` copy; everyone else is untouched and stays on the US Workers. It keeps the zone's other redirect rules.

```bash
node infra/ru-geo-redirect.mjs                                   # dry run
CLOUDFLARE_API_TOKEN=... node infra/ru-geo-redirect.mjs --apply  # write the rules
```

Zones covered now (Russia copy live on `russia-web`): scalvs.app, infinitumcorporation.ai, infinitumveilvpn.app. Add a zone to `ROUTES` once its Russia copy answers.

**Applied 2026-11-12** to all three zones (each zone had no other redirect rules). DNS for every `ru.` name checked first: A record to `russia-web` (93.77.163.229), DNS only.

## Still to do

- **Data in Ireland, servers in the USA (decided 2026-11-12, William: "USE IRELAND AND USA").** Supabase stays in eu-west-1 (Ireland); the Workers run in us-east4. Each database round trip crosses the Atlantic (~70–80 ms), accepted. No US database is planned.
- **Labyrinth (US host built 2026-11-12, not serving yet):** Worker `lbrnth-host-us` (infinitum-nexus `apps/lbrnth-host/src/worker.ts`, placement us-east4) on `lbrnth-us.cumuluscloud.app`. Postgres: Supabase schema `lbrnth` (migrations `20261111_lbrnth_store` and `_host_role_grants` applied), login role `lbrnth_host`, Hyperdrive `lbrnth-us-db` (`03a6e0f7…`, caching off). Secrets `LBRNTH_MASTER_KEY` (its own key, not Russia's) and `CID_DIRECTORY_KEY` are set on the Worker. Blocked on:
  1. Cloudflare Access: solved without touching Access. Relays reach `lbrnth-host-us` over a service binding (`LBRNTH_US`), so the call stays inside Cloudflare and the public hostname stays behind the "All Workers" login. Live 2026-11-12 on `cumulus-id` (version `5e76d4d4`) and `architect` (`79793f3b`). `cumulus-mail` has the code and config on infinitum-nexus main (`fcf23de`) but is NOT redeployed: `cf deploy` refused because its placement was set through the API, and the override (`--no-strict`) needs the owner's OK. It goes out with Mail's next deploy.
  2. Done 2026-11-12: `cumulus-id` redeployed from infinitum-nexus main (version `7dba2fe0`, which also shipped #143, #144, #145 and #147) with `CID_DIRECTORY_KEY_SHA256_US` set. Checked: OIDC discovery, providers and `/` answer 200; the US key passes the directory key check; a wrong key gets 401 "Unknown directory client".
  3. Data: existing Labyrinth orgs live on `labyrinth-russia`. Before relays send global users to the US host (`LBRNTH_ENDPOINT=RU=https://lbrnth.cumuluscloud.app/api/labyrinth,*=https://lbrnth-us.cumuluscloud.app/api/labyrinth`), decide where each org's data lives and move it; otherwise those users see an empty Labyrinth.
- **Conjure:** stays on its own Russian server (`conjure-russia`) by decision on 2026-11-12. The old AWS EC2 stack (us-east-2) still has to be destroyed once AWS access is back.
- **Cumulus Mail's `ru.` mirror** is on AWS (`MAIL_EXTRA_ORIGINS` in infinitum-nexus `apps/cumulus-mail/cloudflare.config.ts`). Move it to `russia-web` on Yandex when AWS access is back, then retire it.
- **VEIL VPN nodes** cannot run on Cloudflare (WireGuard, IKEv2 and OpenVPN need inbound UDP). The US nodes `vpn-use2` and `vpn-usw1` stay on their VMs.
