// Publish installers to downloads.cumuluscloud.app.
//   node publish.mjs <folder> [--dry-run]
// Uploads every recognised installer in <folder> to the R2 bucket cumulus-downloads, then
// updates files/manifest.json (merging with what is already published, by platform key).
// Needs wrangler signed in to the Cloudflare account (CLOUDFLARE_ACCOUNT_ID set).
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readdirSync, readFileSync, statSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const BUCKET = "cumulus-downloads";
const SITE = "https://downloads.cumuluscloud.app";

/** Which download a file is, from its name (CumulusMail-<version>-<os>-<arch>.<ext>). */
export function classify(name) {
  const version = /^CumulusMail-(\d+\.\d+\.\d+)/.exec(name)?.[1];
  if (!version) return null;
  const arm = /arm64|aarch64/i.test(name);
  const rules = [
    [/\.exe$/i, arm ? "windows-arm64" : "windows"],
    [/\.dmg$/i, "mac"],
    [/-mac-.*\.zip$/i, "mac-zip"],
    [/\.AppImage$/, arm ? "linux-appimage-arm64" : "linux-appimage-x64"],
    [/\.deb$/i, arm ? "linux-deb-arm64" : "linux-deb-x64"],
    [/\.apk$/i, "android"],
  ];
  const hit = rules.find(([re]) => re.test(name));
  return hit ? { key: hit[1], version } : null;
}

async function main() {
  const [dir, flag] = process.argv.slice(2);
  if (!dir) throw new Error("usage: node publish.mjs <folder> [--dry-run]");
  const dry = flag === "--dry-run";
  const found = readdirSync(dir)
    .map((name) => ({ name, info: classify(name), path: join(dir, name) }))
    .filter((f) => f.info && statSync(f.path).isFile());
  if (!found.length) throw new Error(`No installers named CumulusMail-<version>-... in ${dir}`);

  // Uploads retry, since big files over a shaky connection sometimes drop.
  const wrangler = (...args) => {
    for (let attempt = 1; ; attempt++) {
      try { return execFileSync("npx", ["wrangler", ...args], { stdio: ["ignore", "inherit", "inherit"], shell: process.platform === "win32" }); }
      catch (e) { if (attempt >= 5) throw e; console.log(`retrying (${attempt}/4)...`); }
    }
  };
  const published = (name) => fetch(`${SITE}/files/${encodeURIComponent(name)}`, { method: "HEAD" }).then((r) => (r.ok ? Number(r.headers.get("content-length")) : -1)).catch(() => -1);

  const entries = [];
  for (const { name, info, path } of found) {
    const data = readFileSync(path);
    const entry = { key: info.key, name, version: info.version, size: data.length, sha256: createHash("sha256").update(data).digest("hex") };
    if (!dry && (await published(name)) === entry.size) {
      console.log(`already published ${name}`);
    } else {
      console.log(`${dry ? "would upload" : "uploading"} ${name} (${entry.key}, ${entry.size} bytes)`);
      if (!dry) wrangler("r2", "object", "put", `${BUCKET}/${name}`, "--file", path, "--remote");
    }
    entries.push(entry);
  }

  return fetch(`${SITE}/files/manifest.json`, { cache: "no-store" })
    .then((r) => (r.ok ? r.json() : { files: [] }))
    .catch(() => ({ files: [] }))
    .then((current) => {
      const byKey = new Map((current.files || []).map((f) => [f.key, f]));
      for (const e of entries) byKey.set(e.key, e);
      const manifest = { updatedAt: new Date().toISOString(), files: [...byKey.values()].sort((a, b) => a.key.localeCompare(b.key)) };
      const file = join(mkdtempSync(join(tmpdir(), "cm-dl-")), "manifest.json");
      writeFileSync(file, JSON.stringify(manifest, null, 2));
      console.log(JSON.stringify(manifest, null, 2));
      if (!dry) wrangler("r2", "object", "put", `${BUCKET}/manifest.json`, "--file", file, "--content-type", "application/json", "--remote");
    });
}

if (process.argv[1]?.endsWith("publish.mjs")) {
  Promise.resolve().then(main).catch((e) => { console.error(e.message); process.exit(1); });
}
