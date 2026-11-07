import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  CUMULUS_DOMAIN_DEFAULTS,
  assertExactOrigin,
  cidIssuerMetadata,
  cumulusCorsHeaders,
  cumulusDomains,
  exactCorsOrigin,
} from "./domains.ts";

const defaults = {
  CUMULUS_ROOT_DOMAIN: undefined,
  CUMULUS_ID_HOST: undefined,
  CUMULUS_CLOUD_HOST: undefined,
  CUMULUS_MAIL_HOST: undefined,
};

describe("Cumulus domain", () => {
  it("uses cumuluscloud.app and the id, cloud and mail hosts by default", () => {
    const domains = cumulusDomains(defaults);
    assert.equal(domains.rootDomain, CUMULUS_DOMAIN_DEFAULTS.rootDomain);
    assert.equal(domains.mailDomain, "cumuluscloud.app");
    assert.equal(domains.idHost, "id.cumuluscloud.app");
    assert.equal(domains.cloudHost, "cloud.cumuluscloud.app");
    assert.equal(domains.mailHost, "mail.cumuluscloud.app");
    assert.deepEqual(cidIssuerMetadata(domains), {
      issuer: "https://id.cumuluscloud.app",
      jwks_uri: "https://id.cumuluscloud.app/.well-known/jwks.json",
      id_host: "id.cumuluscloud.app",
    });
    assert.deepEqual(domains.corsOrigins, [
      "https://id.cumuluscloud.app",
      "https://cloud.cumuluscloud.app",
      "https://mail.cumuluscloud.app",
    ]);
    assert.equal(domains.corsOrigins.includes("*"), false);
  });

  it("derives hosts from the root domain and accepts an explicit host under that root", () => {
    const derived = cumulusDomains({ ...defaults, CUMULUS_ROOT_DOMAIN: "Example.Test" });
    assert.equal(derived.idHost, "id.example.test");
    assert.equal(derived.issuer, "https://id.example.test");
    assert.equal(derived.mailDomain, "example.test");
    const explicit = cumulusDomains({
      ...defaults,
      CUMULUS_ROOT_DOMAIN: "cumuluscloud.app",
      CUMULUS_ID_HOST: "login.cumuluscloud.app",
    });
    assert.equal(explicit.issuer, "https://login.cumuluscloud.app");
    assert.equal(explicit.jwksUri, "https://login.cumuluscloud.app/.well-known/jwks.json");
    assert.equal(explicit.corsOrigins[0], "https://login.cumuluscloud.app");
  });

  it("rejects wildcards, URLs and hosts outside the root domain", () => {
    for (const bad of ["*", "*.cumuluscloud.app", "https://id.cumuluscloud.app", "id.cumuluscloud.app/jwks", "id.cumuluscloud.app:443", ""]) {
      assert.throws(() => cumulusDomains({ ...defaults, CUMULUS_ID_HOST: bad }), /exact hostname/, bad);
    }
    assert.throws(
      () => cumulusDomains({ ...defaults, CUMULUS_ID_HOST: "id.cumuluscloud.app.evil.com" }),
      /subdomains/,
    );
    assert.throws(() => cumulusDomains({ ...defaults, CUMULUS_CLOUD_HOST: "cloud.evil.com" }), /subdomains/);
  });
});

describe("exact CORS origins", () => {
  const allowed = cumulusDomains(defaults).corsOrigins;

  it("echoes only an allowlisted origin and never a wildcard", () => {
    assert.equal(exactCorsOrigin("https://id.cumuluscloud.app", allowed), "https://id.cumuluscloud.app");
    assert.equal(exactCorsOrigin("https://mail.cumuluscloud.app/", allowed), "https://mail.cumuluscloud.app");
    for (const blocked of [
      "*",
      "https://*.cumuluscloud.app",
      "https://id.cumuluscloud.app.evil.com",
      "https://evil.com",
      "https://notid.cumuluscloud.app",
      "null",
      "https://id.cumuluscloud.app/path",
      "https://user:pass@id.cumuluscloud.app",
    ]) {
      assert.equal(exactCorsOrigin(blocked, allowed), null, blocked);
    }
    const headers = cumulusCorsHeaders("https://cloud.cumuluscloud.app", allowed);
    assert.equal(headers["Access-Control-Allow-Origin"], "https://cloud.cumuluscloud.app");
    assert.equal(JSON.stringify(headers).includes("*"), false);
    const denied = cumulusCorsHeaders("*", allowed);
    assert.equal(denied["Access-Control-Allow-Origin"], undefined);
    assert.equal(Object.values(denied).includes("*"), false);
    assert.equal(cumulusCorsHeaders("https://cloud.cumuluscloud.app", ["*"])["Access-Control-Allow-Origin"], undefined);
  });

  it("rejects wildcard and path origins in the allowlist", () => {
    assert.equal(assertExactOrigin("https://id.cumuluscloud.app"), "https://id.cumuluscloud.app");
    assert.equal(assertExactOrigin("http://localhost:3000"), "http://localhost:3000");
    assert.throws(() => assertExactOrigin("*"), /exact/);
    assert.throws(() => assertExactOrigin("https://*.cumuluscloud.app"), /exact/);
    assert.throws(() => assertExactOrigin("https://id.cumuluscloud.app/jwks"), /path/);
  });
});
