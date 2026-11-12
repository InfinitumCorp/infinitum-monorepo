#!/usr/bin/env node
// Sends visitors from Russia to the Russia copies on Yandex Cloud; everyone else stays on the
// Cloudflare Workers, which run on US servers (placement region gcp:us-east4).
//
// One Single Redirect rule per zone, ref "infinitum-ru-geo", added to the zone's
// http_request_dynamic_redirect entrypoint. Other redirect rules in the zone are kept.
//
//   node infra/ru-geo-redirect.mjs            # dry run: prints what would change
//   CLOUDFLARE_API_TOKEN=... node infra/ru-geo-redirect.mjs --apply
//
// The token needs Zone > Single Redirect: Edit (or Zone Rulesets: Edit) on these zones.
// Add a product here once its Russia copy answers on its ru. hostname.

const ROUTES = [
  { zone: "scalvs.app", hosts: ["scalvs.app", "www.scalvs.app"], to: "ru.scalvs.app" },
  { zone: "infinitumcorporation.ai", hosts: ["infinitumcorporation.ai", "www.infinitumcorporation.ai"], to: "ru.infinitumcorporation.ai" },
  { zone: "infinitumveilvpn.app", hosts: ["infinitumveilvpn.app", "www.infinitumveilvpn.app"], to: "ru.infinitumveilvpn.app" },
];
const REF = "infinitum-ru-geo";
const PHASE = "http_request_dynamic_redirect";
const API = "https://api.cloudflare.com/client/v4";
const apply = process.argv.includes("--apply");
const token = process.env.CLOUDFLARE_API_TOKEN;

export function ruleFor({ hosts, to }) {
  const set = hosts.map((h) => JSON.stringify(h)).join(" ");
  return {
    ref: REF,
    description: `Russia -> ${to} (Yandex Cloud); everyone else stays on the US Workers`,
    expression: `(ip.src.country eq "RU" and http.host in {${set}})`,
    action: "redirect",
    action_parameters: {
      from_value: {
        status_code: 302, // temporary: a visitor who leaves Russia is not stuck on the ru. copy
        preserve_query_string: true,
        target_url: { expression: `concat("https://${to}", http.request.uri.path)` },
      },
    },
    enabled: true,
  };
}

export function merge(existing, rule) {
  const kept = (existing || []).filter((r) => r.ref !== REF).map(({ id, version, last_updated, ...r }) => r);
  return [rule, ...kept];
}

async function cf(path, init = {}) {
  const res = await fetch(API + path, { ...init, headers: { authorization: `Bearer ${token}`, "content-type": "application/json", ...init.headers } });
  const body = await res.json().catch(() => ({}));
  if (!res.ok && res.status !== 404) throw new Error(`${init.method || "GET"} ${path}: ${res.status} ${JSON.stringify(body.errors || body)}`);
  return { status: res.status, body };
}

async function main() {
  if (!token) {
    for (const r of ROUTES) console.log(`[dry run, no token] ${r.zone}:`, JSON.stringify(ruleFor(r), null, 2));
    console.log("Set CLOUDFLARE_API_TOKEN and pass --apply to write the rules.");
    return;
  }
  for (const r of ROUTES) {
    const z = await cf(`/zones?name=${encodeURIComponent(r.zone)}`);
    const zoneId = z.body.result?.[0]?.id;
    if (!zoneId) { console.log(`${r.zone}: zone not found on this account, skipped`); continue; }
    const ep = await cf(`/zones/${zoneId}/rulesets/phases/${PHASE}/entrypoint`);
    const rules = merge(ep.status === 404 ? [] : ep.body.result?.rules, ruleFor(r));
    if (!apply) { console.log(`${r.zone}: would write ${rules.length} rule(s); ours first:`, rules[0].expression); continue; }
    await cf(`/zones/${zoneId}/rulesets/phases/${PHASE}/entrypoint`, { method: "PUT", body: JSON.stringify({ rules }) });
    console.log(`${r.zone}: Russia -> ${r.to} rule written (${rules.length} rule(s) in the phase)`);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) main().catch((e) => { console.error(e.message); process.exit(1); });
