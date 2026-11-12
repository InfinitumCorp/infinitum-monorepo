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

## Russia → Russia copies

`infra/ru-geo-redirect.mjs` adds one Single Redirect rule per zone (ref `infinitum-ru-geo`): a request from Russia (`ip.src.country eq "RU"`) to the main hostname gets a 302 to the `ru.` copy; everyone else is untouched and stays on the US Workers. It keeps the zone's other redirect rules.

```bash
node infra/ru-geo-redirect.mjs                                   # dry run
CLOUDFLARE_API_TOKEN=... node infra/ru-geo-redirect.mjs --apply  # write the rules
```

Zones covered now (Russia copy live on `russia-web`): scalvs.app, infinitumcorporation.ai, infinitumveilvpn.app. Add a zone to `ROUTES` once its Russia copy answers.

**Not applied yet:** this session had no Cloudflare token. Run the `--apply` command once with a token that has Single Redirect (Zone Rulesets) Edit on those zones.

## Still to do

- **Supabase is in eu-west-1 (Ireland).** US Workers reach it across the Atlantic (~70–80 ms per query). For a fully US stack, create a US Supabase project (us-east-1) and move the schemas, then set every Worker's placement to that region.
- **Labyrinth:** `lbrnth.cumuluscloud.app` points at `labyrinth-russia` for everyone. It needs a US copy (Cloudflare Container running `apps/lbrnth-host` from infinitum-nexus, Postgres on Supabase) before global users can move off the Russia server.
- **Conjure:** stays on its own Russian server (`conjure-russia`) by decision on 2026-11-12. The old AWS EC2 stack (us-east-2) still has to be destroyed once AWS access is back.
- **Cumulus Mail's `ru.` mirror** is on AWS (`MAIL_EXTRA_ORIGINS` in infinitum-nexus `apps/cumulus-mail/cloudflare.config.ts`). Move it to `russia-web` on Yandex when AWS access is back, then retire it.
- **VEIL VPN nodes** cannot run on Cloudflare (WireGuard, IKEv2 and OpenVPN need inbound UDP). The US nodes `vpn-use2` and `vpn-usw1` stay on their VMs.
