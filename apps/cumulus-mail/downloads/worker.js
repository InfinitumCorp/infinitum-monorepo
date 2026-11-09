// downloads.cumuluscloud.app: the page comes from static assets; /files/* streams installers from R2.

const TYPES = {
  exe: "application/vnd.microsoft.portable-executable",
  dmg: "application/x-apple-diskimage",
  zip: "application/zip",
  AppImage: "application/vnd.appimage",
  deb: "application/vnd.debian.binary-package",
  apk: "application/vnd.android.package-archive",
  json: "application/json; charset=utf-8",
};

export const fileName = (pathname) => {
  const m = /^\/files\/([A-Za-z0-9][A-Za-z0-9._-]{0,120})$/.exec(pathname);
  return m && !m[1].includes("..") ? m[1] : null;
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    // Stable links (/get/windows, /get/android, ...) point at the current version of each installer.
    const get = /^\/get\/([a-z0-9-]{1,40})$/.exec(url.pathname);
    if (get) {
      const manifest = await env.DOWNLOADS.get("manifest.json").then((o) => (o ? o.json() : null)).catch(() => null);
      const file = manifest?.files?.find((f) => f.key === get[1]);
      return file
        ? Response.redirect(new URL(`/files/${encodeURIComponent(file.name)}`, url).toString(), 302)
        : new Response("That download isn't available yet.", { status: 404, headers: { "content-type": "text/plain; charset=utf-8" } });
    }
    if (!url.pathname.startsWith("/files/")) return env.ASSETS.fetch(request);
    if (request.method !== "GET" && request.method !== "HEAD") return new Response("Method not allowed", { status: 405, headers: { allow: "GET, HEAD" } });
    const name = fileName(url.pathname);
    if (!name) return new Response("Not found", { status: 404 });

    const isManifest = name === "manifest.json";
    const obj = request.method === "HEAD"
      ? await env.DOWNLOADS.head(name)
      : await env.DOWNLOADS.get(name, { range: request.headers, onlyIf: request.headers });
    if (!obj) return new Response("Not found", { status: 404 });

    const headers = new Headers();
    obj.writeHttpMetadata(headers);
    const ext = name.split(".").pop();
    headers.set("content-type", TYPES[ext] || "application/octet-stream");
    headers.set("etag", obj.httpEtag);
    headers.set("accept-ranges", "bytes");
    headers.set("x-content-type-options", "nosniff");
    headers.set("access-control-allow-origin", "*");
    if (isManifest) {
      headers.set("cache-control", "no-cache");
    } else {
      // Installer names carry their version, so a name's bytes never change.
      headers.set("cache-control", "public, max-age=86400");
      headers.set("content-disposition", `attachment; filename="${name}"`);
    }

    if (request.method === "HEAD") {
      headers.set("content-length", String(obj.size));
      return new Response(null, { headers });
    }
    if (!("body" in obj) || !obj.body) return new Response(null, { status: 304, headers }); // onlyIf failed: not modified
    if (obj.range && request.headers.has("range")) {
      const { offset = 0, length = obj.size - offset } = obj.range;
      headers.set("content-range", `bytes ${offset}-${offset + length - 1}/${obj.size}`);
      headers.set("content-length", String(length));
      return new Response(obj.body, { status: 206, headers });
    }
    headers.set("content-length", String(obj.size));
    return new Response(obj.body, { headers });
  },
};
