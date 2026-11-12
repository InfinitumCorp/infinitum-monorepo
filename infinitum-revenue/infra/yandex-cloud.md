# Yandex Cloud: Infinitum product servers (Russia)

Created 2026-11-11 for the Infinitum product's Russia footprint.

## Where

- Cloud: `cloud-squall-481` (`b1ge7a8mfl3d020u4gho`)
- Folder: `default` (`b1g3bonfmtl53fs762er`)
- Billing: individual account, RUB, linked 2026-11-11 (4,000 ₽ starter grant)

## Servers

| Name | Zone | Platform | vCPU | RAM | Boot disk | OS |
|---|---|---|---|---|---|---|
| infinitum-product-1 | ru-central1-a | standard-v3 (Ice Lake), 100% | 2 | 4 GB | 10 GB network-ssd | Ubuntu 24.04 LTS |
| infinitum-product-2 | ru-central1-d | standard-v3 (Ice Lake), 100% | 2 | 4 GB | 10 GB network-ssd | Ubuntu 24.04 LTS |
| infinitum-product-3 | ru-central1-e | standard-v3 (Ice Lake), 100% | 2 | 4 GB | 10 GB network-ssd | Ubuntu 24.04 LTS |

- Each has a dynamic public IPv4 (NAT) on the zone's `default-ru-central1-*` subnet.
- Label: `product=infinitum`.
- Zone ru-central1-b is skipped on purpose (Yandex status warnings at creation time).
- Public IPs are not listed here because this repo is public. Get them with:
  `yc compute instance list --folder-id b1g3bonfmtl53fs762er`

## Access

- SSH key only, user `yc-user`. The key is `yandex_infinitum` (ed25519) on William's PC. The private key is never committed.
- Host shortcuts are in `~/.ssh/config` on that PC: `ssh infinitum-product-1` (and -2, -3).

## Firewall

All three servers use security group `infinitum-product-sg` (`enpcv3ntnffkgvkgv0a5`), attached 2026-11-11:

| Direction | Port | From / to | Why |
|---|---|---|---|
| in | 22/tcp | William's admin IP only (/32) | SSH |
| in | 80, 443/tcp | anywhere | web |
| in | ICMP | anywhere | ping |
| in | any | the same group | server-to-server |
| out | any | anywhere | updates, GitHub, registries |

Everything else inbound is dropped (verified from Cloud Shell). If the admin IP changes, SSH stops working: update the port-22 rule from the console or Cloud Shell, which do not need SSH.

## Cost (console calculator, VAT incl., per server per month)

| Item | ₽ |
|---|---|
| 2 vCPU | 1,785.60 |
| 4 GB RAM | 950.40 |
| 10 GB SSD | ~143 |
| Public IP | 189.73 |
| **Per server** | **~3,069** |
| **3 servers** | **~9,207** |

## Quotas hit at creation (new-account defaults, per zone)

- network-ssd: 10 GB, network-hdd: 20 GB, vCPU: 2, RAM: 16 GB, instances: 12 per cloud.
- That is why the boot disks are 10 GB and there is one server per zone.
- To grow disks or add a second server in a zone, request an increase on the console's Quotas page first.

## scalvs-ru (moved to russia-web 2026-11-11)

- Served by `russia-web` over HTTPS (see below). It ran on infinitum-product-1/2/3 from 2026-11-11 until it moved the same day.
- Retired from product-1/2/3 on 2026-11-11: runner services removed, runners `yandex-ru-1/2/3` deregistered (only `russia-web` remains), the `runner` user, nginx and `/var/www` removed.

## Fabric Russia (`fabric-russia`, since 2026-11-11)

Dedicated server for Fabric's Russia homeserver. Fabric's existing Cloudflare setup stays as it is; this is an additional Russia deployment, not a move.

- VM `fabric-russia` (created as `infinitum-fabric-ru`, renamed 2026-11-11), zone ru-central1-d, 2 vCPU, 4 GB RAM, 20 GB SSD, Ubuntu 24.04, same `infinitum-product-sg` firewall. SSH: `ssh fabric-russia`.
- Quota raised 2026-11-11 (approved instantly): 6 vCPU and 50 GB SSD per zone in a/d/e. Zone b is closed to new VMs; zone f has a 0-disk quota (an unused `default-ru-central1-f` subnet exists).
- A first copy ran on infinitum-product-1 while the quota was blocked. Deleted 2026-11-11 (containers, network, `/opt/fabric` and the Synapse/Postgres images); product-1 went from 4.6 GB to 3.5 GB used and still serves scalvs-ru.

Setup on fabric-russia:

- `/opt/fabric`: Docker Compose with `matrixdotorg/synapse:v1.164.2` and `postgres:16`, matching InfinitumCorp/fabric `server/docker-compose.yml`.
- `server_name: ru.infinitumfabric.app` (permanent: it becomes part of every user ID).
- Closed registration (no token → 403), federation listener removed (→ 404), empty `federation_domain_whitelist` and `trusted_key_servers`.
- Synapse listens on `127.0.0.1:8008` only. It is not reachable from the internet until nginx + TLS on a real hostname are added.
- DB password lives only in `/opt/fabric/.env` on the server (mode 600).

Public access (since 2026-11-11):

- Static IP 81.26.185.34 (Yandex address `fl8u8jbn8a1vp9hpmjdt`, made static).
- Cloudflare DNS: `A ru.infinitumfabric.app → 81.26.185.34`, DNS only (not proxied), TTL 300. Record id `c455c1d5f5bf0fad34f8b222e57c9f90` in zone infinitumfabric.app.
- nginx + Let's Encrypt (certbot webroot, registered without an email; auto-renews via `certbot.timer` with an nginx reload hook). Cert valid to 2027-02-09.
- `https://ru.infinitumfabric.app`: `/.well-known/matrix/client` returns the base URL; only `/_matrix/client` and `/_synapse/client` reach Synapse. `/_synapse/admin`, federation and everything else return 404. HTTP redirects to HTTPS.
- Checked from outside: client versions 200, registration without a token 403, admin 404, federation 404.

## Conjure Russia (`conjure-russia`, since 2026-11-11)

Conjure's own server in Russia. No Claude: `ANTHROPIC_API_KEY` is never set here. With no `CONJURE_LLM_*` values it runs on the mock provider.

- VM `conjure-russia`, zone ru-central1-a, 2 vCPU, 4 GB RAM, 20 GB SSD, Ubuntu 24.04, `infinitum-product-sg` firewall, labels `product=conjure,region=russia`. SSH: `ssh conjure-russia`.
- Static IP 51.250.92.75 (Yandex address `conjure-russia-ip`).
- Code: InfinitumCorp/Conjure at `1da489e` (main, "staff sign in with Cumulus ID") in `/opt/conjure/releases/<sha>`, `current` symlink. Node v24.11.0 (checksum-verified), `npm ci --omit=dev`.
- systemd `conjure.service` as the non-root `conjure` user, `127.0.0.1:4317` (app) and `:4318` (preview), sandboxed like the AWS deploy. Data: `/var/lib/conjure`, `/var/lib/conjure-data`.
- `/etc/conjure/env` (root-only, never committed) holds a random `CONJURE_ACCESS_TOKEN`, so sign-in is required. Slots to fill on the server: `CID_OWNERS` (your Cumulus ID; until set nobody can sign in), optional `CID_ISSUER` (Russia mirror), and the `CONJURE_LLM_*` gateway values to leave mock mode. Then `sudo systemctl restart conjure`.
- Mode on start: "offline: mock provider, sign-in required".
- `CID_OWNERS` set 2026-11-11 to William's account email (Conjure matches it against the Cumulus ID profile's email, username, handle or cid). If his Cumulus ID uses a different email, replace it with his `cid_...` and restart.

Public access (since 2026-11-11):

- Cloudflare DNS-only A records (TTL 300) in zone infinitumcorporation.ai → 51.250.92.75: `conjure-ru` (record id `a527c0a9b55e74f90e009082e3aecc3c`) and `conjure-ru-preview` (`84250d9ab1c33da047266d83e050baac`).
- nginx + Let's Encrypt, one cert for both names (webroot, no email, auto-renews via `certbot.timer` with an nginx reload hook). Valid to 2027-02-09. HTTP redirects to HTTPS; HSTS on the app host.
- `https://conjure-ru.infinitumcorporation.ai` → 127.0.0.1:4317, `https://conjure-ru-preview.infinitumcorporation.ai` → 127.0.0.1:4318 (streaming: proxy buffering off).
- Checked from outside: `/healthz` 200, `/` 303 to `/login`, `/login` 200 with "Sign in with Cumulus ID" and "Create a Cumulus ID", `/api/status` 401 without a session, HTTP 301 to HTTPS. Preview `/` returns 400 "bad path" from Conjure itself (it only serves deliverable paths).
- Cumulus ID: id.cumuluscloud.app loads from this server (200, ~0.3 s), so no mirror is needed from the data centre. Consumer ISPs in Russia were not tested. Sign-in was rejected ("didn't come from an Infinitum product") because the `conjure` client accepted only loopback redirects. Fixed in InfinitumCorp/infinitum-nexus#130 (adds this origin), merged 2026-11-11 with #131 (main's typecheck fix). id.cumuluscloud.app (Worker `cumulus-id`) deployed from main `6bc07f8` on 2026-11-11, version `be96d455` (previous `61335397`, for rollback). Checked after deploy: Conjure Russia's `/auth/cid/start` → authorize 302 (accepted), a lookalike origin 400, scalvs and fabric still 302, OIDC discovery and `/api/auth/providers` 200.

## Russia codebases (since 2026-11-11)

Each Russia deployment has its own public repo holding what differs for Russia (setup, TLS, backups, checks). The app code stays in the main repo, pinned by version or commit. No secrets in either: they are generated on the servers.

| Repo | Server | What it holds |
|---|---|---|
| [InfinitumCorp/fabric-ru](https://github.com/InfinitumCorp/fabric-ru) | fabric-russia | Synapse + Postgres compose, the closed/no-federation settings, `setup-server.sh`, `setup-tls.sh`, `setup-backups.sh`, `check.sh` |
| [InfinitumCorp/conjure-ru](https://github.com/InfinitumCorp/conjure-ru) | conjure-russia | `setup.sh` (pinned Conjure SHA, systemd, nginx, TLS; never Anthropic) and `check.sh` |
| [InfinitumCorp/scalvs-ru](https://github.com/InfinitumCorp/scalvs-ru) | russia-web | The scalvs.app Russia mirror (existing) |

- Verified 2026-11-11: fabric-ru's setup produces the live config from scratch (Postgres, client-only listener, closed, no federation) and is safe to re-run. conjure-ru's setup re-ran end to end on conjure-russia (same release, HTTPS kept). Both `check.sh` scripts pass against the live servers.
- CI per repo: shellcheck on push/PR. The live checks run only on manual dispatch, so no scheduled minutes are used.
- Defense products (AETHER, ARGUS, ODIN, E(O)N) get no Russia version or server.

## Russia copies of all products + VEIL VPN nodes (started 2026-11-11)

Decision (William, 2026-11-11): every product gets a Russia copy on Yandex while its current hosting stays as it is; infinitum-product-1/2/3 become VEIL</> VPN nodes.

Not copied to Russia: defense products (AETHER, ARGUS, ODIN, E(O)N, infinitumdefense.com, drone/autonomy), internal tools (1MIND, THRONE//OS, personal sites), and anything that would call Claude. The Firmament / $FIRM token offering is held for a sanctions and securities check first.

**russia-web** (new web server for the Russia copies)
- VM `russia-web`, ru-central1-e, 2 vCPU, 4 GB, 20 GB SSD, Ubuntu 24.04, `infinitum-product-sg`, labels `product=web,region=russia`. IP 93.77.163.229 (dynamic until the static-IP quota rises). SSH: `ssh russia-web`.
- nginx serves, by name: `ru.scalvs.app` (InfinitumCorp/scalvs-ru), `ru.infinitumcorporation.ai` (InfinitumCorp/infinitum-corporation-site) and `ru.infinitumveilvpn.app` (VEIL VPN site, `npm run build` -> `dist/site`).
- Cloudflare DNS-only A records (TTL 300) → 93.77.163.229: `ru.infinitumcorporation.ai`, `ru.infinitumveilvpn.app`, and `ru.scalvs.app` (changed from a CNAME to GitHub Pages on 2026-11-11).
- HTTPS since 2026-11-11: one Let's Encrypt cert per name (webroot, no email, `certbot.timer` with an nginx reload hook), valid to 2027-02-09. HTTP 301 to HTTPS, HSTS. Checked from outside: all three return 200 with the right page titles.
- GitHub runner `russia-web` (labels `yandex-ru,russia-web`) for InfinitumCorp/scalvs-ru, as the non-root `runner` user. scalvs-ru #5 (merged 2026-11-11) moved the deploy job onto it.
- scalvs-ru #6 (merged 2026-11-12): the post-deploy check now requests HTTPS locally (HTTP gets the 301). The first deploy after it ran on `russia-web` and passed.

**labyrinth-russia** (Labyrinth's Russia server, created 2026-11-12 on William's OK)
- VM `labyrinth-russia`, ru-central1-e, standard-v3 2 vCPU 100%, 4 GB, 20 GB network-ssd, Ubuntu 24.04, `infinitum-product-sg`, labels `product=labyrinth,region=russia`. Nothing installed yet.
- IP 93.77.161.140 (dynamic: `labyrinth-russia-ip` could not be reserved, the static-IP quota is still 2). The first NAT IP, 93.77.163.222, never took inbound traffic from the internet (only from inside Yandex), even after a restart; on 2026-11-12 the NAT was removed and re-added, and the new IP answers from outside. Same security group throughout (`infinitum-product-sg`, 80/443 open). Internal 10.131.0.27. Boot disk `bg0ocj0s5kgur9d6uo2n`.
- SSH: `ssh labyrinth-russia`. Direct SSH to the public IP times out from William's PC (the VM is fine, cloud-init finished and the key is in place), so the ssh config jumps through `russia-web` to the internal IP. Switch it back to the public IP once a static IP is attached and reachable.
- Zone ru-central1-e is now full for the default quota (6 vCPU, 50 GB SSD: product-3, russia-web, labyrinth-russia).

**VEIL VPN nodes**
- Security group `veil-vpn-sg` (`enp25m9l167b57r2sm1q`): SSH from the admin IP; 80/443; WireGuard UDP 51820; IKEv2 UDP 500/4500; OpenVPN UDP+TCP 1194; VLESS TCP 2053; Shadowsocks TCP+UDP 16080; agent TCP 9101 (the node's own nftables limits it to the control API); ICMP; all egress. Attached to infinitum-product-1/2/3 on 2026-11-11, alongside `infinitum-product-sg` (`enpcv3ntnffkgvkgv0a5`).
- Names: `vpn-ru1/ru2/ru3.infinitumveilvpn.app` -> infinitum-product-1/2/3 (84.201.173.68, 153.51.166.190, 93.77.160.57), matching the existing `vpn-<region>` nodes. DNS for these is not created yet.
- Staged on each node (2026-11-11): InfinitumCorp/veil-vpn cloned to `/opt/veil-vpn` (`d80886e`), and `/root/veil-node-ru.sh` (root-only) with that node's `NODE_ID`, `DOMAIN=vpn-ruN.infinitumveilvpn.app`, `CONTROL_IP` = Cloudflare's IPv4 ranges, and VLESS + Shadowsocks on. nginx is gone, so port 80 is free for provision.sh's certbot.
- To finish a node (William): create the `vpn-ruN` A record (DNS only) → the node's IP; on the node put the control API's `TOKEN_SECRET` and `AGENT_SECRET` in `/root/veil.env` (chmod 600); run `sudo bash /root/veil-node-ru.sh`. Its log `/root/veil-provision.log` ends with the node's NODES entry, which contains node secrets: add it to the control Worker's `NODES` only, never to a repo.

**Quotas**
- Static public IPs: limit 2, both used (fabric-russia, conjure-russia). Increase to 8 requested 2026-11-11 so russia-web, labyrinth-russia and the three nodes keep their IPs.

## Using the servers for builds

Today every InfinitumCorp repo is public, so GitHub-hosted runners are free, and the Grok Bot box already has an `infinitum-ci` runner registered per repo as the fallback. Using these paid servers for general CI would cost money for work GitHub does for free. Their value is what only a machine inside Russia can do:

1. **Deploy the Russia mirrors from inside Russia.** Build on GitHub-hosted runners as now, then a deploy job on a Yandex runner (label `yandex-ru`) pulls the artifact and serves it from these servers with nginx on 80/443. Candidates: `scalvs-ru` (today on GitHub Pages, which is unreliable in Russia) and the Cumulus ID mirror needed for "works in Russia without a VPN".
2. **Smoke-test from Russia.** A scheduled-on-deploy (not cron) job on a `yandex-ru` runner checks that id.cumuluscloud.app's mirror, scalvs-ru and other Russia-facing apps load and sign in from a Russian IP.
3. **Overflow builds.** Register them as extra `infinitum-ci` runners, used only if `CI_RUNNER=infinitum-ci` is ever set.

Rules for the runners:
- Trigger only on `push` to the default branch and `workflow_dispatch`, never `pull_request` from forks: the repos are public, so a fork PR could otherwise run code on these servers. Keep "Require approval for all outside collaborators" on in each repo's Actions settings.
- One runner per repo per server (personal account, so no org-level runners), run as a non-root `runner` user, ephemeral where possible.
- Raise the SSD quota to 30–50 GB per zone first; 7 GB free is too little for node_modules/Docker builds.
- Excluded from these runners: defense products (AETHER, infinitumdefense.com, E(O)N).

## Follow-ups

- Reserve static IPs if DNS will point at these servers (dynamic IPs change on stop/start).
- Product servers 1-3 have no backups (they now hold only the staged VEIL node setup).
- russia-web has no backup yet (its sites are rebuilt from their repos).

## Backups (since 2026-11-11)

- Snapshot schedule `daily-data-servers` (`fd8l3q6kcg93enulpjuj`), status ACTIVE: every day at 00:00 UTC (03:00 Moscow), keeps the 7 newest snapshots per disk.
- Disks: fabric-russia boot disk `fv47vc5b05rn5m6kevcm` (ru-central1-d) conjure-russia boot disk `fhmk1prc30bfksuvj8ml` (ru-central1-a), and labyrinth-russia boot disk `bg0ocj0s5kgur9d6uo2n` (ru-central1-e, added 2026-11-12).
- Snapshots are billed per GB stored (incremental after the first). Restore: create a disk from a snapshot, then a VM from that disk.
- Fabric's Postgres is snapshotted live (crash-consistent), so a clean dump is taken first: systemd timer `fabric-pgdump.timer` on fabric-russia runs `/usr/local/sbin/fabric-pgdump` daily at 23:40 UTC (`pg_dump -Fc` of the `synapse` DB via `docker compose exec`), writes `/var/backups/fabric/synapse-YYYYMMDD.dump` (root-only, mode 600), keeps 7. The 00:00 UTC snapshot then carries it. First run 2026-11-11: 288 KB, 176 tables, `pg_restore --list` reads it.
- Restore the DB from a dump: `docker compose exec -T db pg_restore -U synapse -d synapse --clean < /var/backups/fabric/synapse-YYYYMMDD.dump` (stop the `synapse` container first).
