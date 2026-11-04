import { createHash, randomBytes, randomUUID } from "node:crypto";
import type { Ledger, Signer, Entry } from "@infinitum/firmament";
import {
  verifyRegistration,
  verifyAssertion,
  type RegistrationResponse,
  type AssertionResponse,
} from "./webauthn.ts";
import { generateTotpSecret, verifyTotp, otpauthUri } from "./totp.ts";
import { verifyIdToken, type SsoConfig } from "./oidc.ts";

/* ------------------------------------------------------------------ *
 * Product registry: every app a CID can sign in to and grant access to.
 * ------------------------------------------------------------------ */

export const INFINITUM_APPS = [
  "veil", "fabric", "throne", "eon", "scalvs", "cndctr", "spectre", "metatron",
  "cumulus", "cumulus-mail", "firm-wallet", "aether", "argus", "hrvst", "eidetic",
  "orbis", "pollen", "flower", "nemesis",
] as const;
export type AppId = (typeof INFINITUM_APPS)[number];

/** What every new CID comes with, per the Cumulus://ID page. */
export const FREE_ENTITLEMENTS = {
  storageGb: 15, // shared by Cumulus Drive and Cumulus://Mail
  mail: true,
  firmWallet: true,
  scalvs: "free",
  cndctr: "free",
  metatronDashboard: true,
} as const;

export const MAIL_DOMAIN = "cumulusmail.cloud";

/* ------------------------------------------------------------------ *
 * Data model
 * ------------------------------------------------------------------ */

export interface Account {
  cid: string;
  username: string;
  /** e.g. alex@cumulusmail.cloud */
  mailAddress: string;
  /** Ecosystem handle, e.g. @alex.veil */
  handle: string;
  orgId?: string;
  /** Link to the organization identity provider, for SSO members. */
  federated?: { issuer: string; subject: string };
  createdAt: string;
  entitlements: typeof FREE_ENTITLEMENTS;
  totp?: { secret: string; confirmed: boolean; lastStep: number };
}

export interface Authenticator {
  id: string;
  cid: string;
  kind: "passkey" | "security-key";
  label: string;
  credentialId: string;
  publicKeyCose: string;
  signCount: number;
  backedUp: boolean;
  addedAt: string;
  lastUsedAt?: string;
}

export interface Session {
  id: string;
  cid: string;
  tokenHash: string;
  device: string;
  method: "passkey" | "security-key" | "sso" | "passkey+totp";
  createdAt: string;
  expiresAt: string;
  revokedAt?: string;
}

export interface Grant {
  cid: string;
  app: AppId;
  scopes: string[];
  grantedAt: string;
  /** Firmament entry hash proving when this grant was made. */
  ledgerHash: string;
}

export interface Organization {
  id: string;
  name: string;
  admins: string[];
  sso?: SsoConfig & { emailDomain?: string };
}

interface Challenge {
  value: string;
  cid?: string;
  purpose: "register" | "login";
  expiresAt: number;
}

interface PendingMfa {
  cid: string;
  authenticatorId: string;
  device: string;
  expiresAt: number;
}

/** Storage boundary. In-memory here; a Postgres implementation on Cumulus Database slots in behind it. */
export class CidStore {
  accounts = new Map<string, Account>();
  byUsername = new Map<string, string>();
  byFederation = new Map<string, string>();
  authenticators = new Map<string, Authenticator>();
  byCredentialId = new Map<string, string>();
  sessions = new Map<string, Session>();
  byTokenHash = new Map<string, string>();
  grants = new Map<string, Grant>(); // key: `${cid}|${app}`
  orgs = new Map<string, Organization>();
  challenges = new Map<string, Challenge>();
  pendingMfa = new Map<string, PendingMfa>();
}

/* ------------------------------------------------------------------ */

export interface CidConfig {
  ledger: Ledger;
  signer: Signer;
  /** WebAuthn relying party ID, e.g. "infinitum.id". */
  rpId: string;
  /** Origins allowed to run passkey ceremonies (web, VEIL, #THRONE shells). */
  origins: string[];
  store?: CidStore;
  clock?: () => Date;
  sessionDays?: number;
}

export class CidError extends Error {
  readonly code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

const USERNAME = /^[a-z0-9][a-z0-9._-]{2,29}$/;
const RESERVED = new Set(["admin", "root", "support", "security", "odin", "infinitum", "postmaster", "abuse"]);
const CHALLENGE_TTL_MS = 5 * 60_000;
const hashToken = (t: string) => createHash("sha256").update(t).digest("hex");

export class CidService {
  readonly store: CidStore;
  private readonly cfg: Required<Omit<CidConfig, "store">>;

  constructor(config: CidConfig) {
    this.store = config.store ?? new CidStore();
    this.cfg = {
      ledger: config.ledger,
      signer: config.signer,
      rpId: config.rpId,
      origins: config.origins,
      clock: config.clock ?? (() => new Date()),
      sessionDays: config.sessionDays ?? 30,
    };
  }

  private now() {
    return this.cfg.clock();
  }

  private record(type: Entry["type"], actor: string, subject: string, payload: Record<string, unknown>) {
    return this.cfg.ledger.append({ type, product: "cid", actor, subject, payload }, this.cfg.signer);
  }

  private account(cid: string): Account {
    const a = this.store.accounts.get(cid);
    if (!a) throw new CidError("not_found", "No such CID");
    return a;
  }

  /* ---------------- Accounts ---------------- */

  async createAccount(input: { username: string; orgId?: string; accountType?: string }): Promise<Account> {
    if (input.accountType === "odin" || input.accountType === "military") {
      throw new CidError("odin_separate", "ODIN° accounts are issued separately, outside consumer accounts");
    }
    const username = input.username.trim().toLowerCase();
    if (!USERNAME.test(username)) {
      throw new CidError("invalid_username", "Usernames are 3-30 characters: letters, numbers, dot, dash, underscore");
    }
    if (RESERVED.has(username)) throw new CidError("reserved", "That username is reserved");
    if (this.store.byUsername.has(username)) throw new CidError("taken", "That username is taken");

    const account: Account = {
      cid: `cid_${randomUUID()}`,
      username,
      mailAddress: `${username}@${MAIL_DOMAIN}`,
      handle: `@${username}.veil`,
      orgId: input.orgId,
      createdAt: this.now().toISOString(),
      entitlements: FREE_ENTITLEMENTS,
    };
    this.store.accounts.set(account.cid, account);
    this.store.byUsername.set(username, account.cid);
    // Only the event is recorded; the CID is never sold and personal details stay off the ledger.
    await this.record("identity", account.cid, account.cid, { event: "account.created", org: input.orgId ?? null });
    return account;
  }

  getAccount(cid: string): Account {
    return this.account(cid);
  }

  /* ---------------- Passkeys & security keys ---------------- */

  private issueChallenge(purpose: Challenge["purpose"], cid?: string): string {
    const value = randomBytes(32).toString("base64url");
    this.store.challenges.set(value, { value, cid, purpose, expiresAt: this.now().getTime() + CHALLENGE_TTL_MS });
    return value;
  }

  private takeChallenge(clientDataJSON: string, purpose: Challenge["purpose"]): Challenge {
    let challenge: string;
    try {
      challenge = JSON.parse(Buffer.from(clientDataJSON, "base64url").toString("utf8")).challenge;
    } catch {
      throw new CidError("bad_request", "Malformed client data");
    }
    const c = this.store.challenges.get(challenge);
    this.store.challenges.delete(challenge); // single use
    if (!c || c.purpose !== purpose || c.expiresAt < this.now().getTime()) {
      throw new CidError("challenge_expired", "Sign-in request expired. Please try again.");
    }
    return c;
  }

  /** Options for navigator.credentials.create() in the browser. */
  startAuthenticatorRegistration(cid: string, kind: Authenticator["kind"] = "passkey") {
    const account = this.account(cid);
    const existing = [...this.store.authenticators.values()].filter((a) => a.cid === cid);
    return {
      challenge: this.issueChallenge("register", cid),
      rp: { id: this.cfg.rpId, name: "Infinitum" },
      user: { id: Buffer.from(cid).toString("base64url"), name: account.mailAddress, displayName: account.username },
      pubKeyCredParams: [{ type: "public-key", alg: -8 }, { type: "public-key", alg: -7 }, { type: "public-key", alg: -257 }],
      authenticatorSelection: {
        residentKey: kind === "passkey" ? "required" : "discouraged",
        userVerification: kind === "passkey" ? "required" : "preferred",
      },
      excludeCredentials: existing.map((a) => ({ type: "public-key", id: a.credentialId })),
      attestation: "none",
      timeout: CHALLENGE_TTL_MS,
    };
  }

  async finishAuthenticatorRegistration(
    cid: string,
    response: RegistrationResponse,
    meta: { kind?: Authenticator["kind"]; label?: string } = {},
  ): Promise<Authenticator> {
    const challenge = this.takeChallenge(response.clientDataJSON, "register");
    if (challenge.cid !== cid) throw new CidError("challenge_expired", "Registration request does not match this account");
    const kind = meta.kind ?? "passkey";
    let verified;
    try {
      verified = verifyRegistration(response, {
        challenge: challenge.value,
        origins: this.cfg.origins,
        rpId: this.cfg.rpId,
        requireUserVerification: kind === "passkey",
      });
    } catch (err) {
      throw new CidError("webauthn_failed", (err as Error).message);
    }
    if (this.store.byCredentialId.has(verified.credentialId)) {
      throw new CidError("duplicate", "This key is already registered");
    }
    const auth: Authenticator = {
      id: `auth_${randomUUID()}`,
      cid,
      kind,
      label: meta.label ?? (kind === "passkey" ? "Passkey" : "Security key"),
      credentialId: verified.credentialId,
      publicKeyCose: verified.publicKeyCose,
      signCount: verified.signCount,
      backedUp: verified.backedUp,
      addedAt: this.now().toISOString(),
    };
    this.store.authenticators.set(auth.id, auth);
    this.store.byCredentialId.set(auth.credentialId, auth.id);
    await this.record("audit", cid, cid, { event: "authenticator.added", kind, authenticator: auth.id });
    return auth;
  }

  listAuthenticators(cid: string): Omit<Authenticator, "publicKeyCose">[] {
    return [...this.store.authenticators.values()]
      .filter((a) => a.cid === cid)
      .map(({ publicKeyCose: _k, ...rest }) => rest);
  }

  async removeAuthenticator(cid: string, authenticatorId: string) {
    const auth = this.store.authenticators.get(authenticatorId);
    if (!auth || auth.cid !== cid) throw new CidError("not_found", "No such key on this account");
    if (this.listAuthenticators(cid).length === 1 && !this.account(cid).federated) {
      throw new CidError("last_key", "Add another passkey or security key before removing your last one");
    }
    this.store.authenticators.delete(authenticatorId);
    this.store.byCredentialId.delete(auth.credentialId);
    await this.record("audit", cid, cid, { event: "authenticator.removed", authenticator: authenticatorId });
  }

  /** Options for navigator.credentials.get(). Omit the username for discoverable passkey sign-in. */
  startSignIn(username?: string) {
    let allowCredentials: { type: string; id: string }[] = [];
    if (username) {
      const cid = this.store.byUsername.get(username.toLowerCase());
      // Unknown usernames get the same response shape so accounts can't be enumerated.
      if (cid) {
        allowCredentials = [...this.store.authenticators.values()]
          .filter((a) => a.cid === cid)
          .map((a) => ({ type: "public-key", id: a.credentialId }));
      }
    }
    return {
      challenge: this.issueChallenge("login"),
      rpId: this.cfg.rpId,
      allowCredentials,
      userVerification: "preferred",
      timeout: CHALLENGE_TTL_MS,
    };
  }

  async finishSignIn(
    response: AssertionResponse,
    device: string,
  ): Promise<{ status: "ok"; token: string; session: Session } | { status: "mfa_required"; mfaToken: string }> {
    const challenge = this.takeChallenge(response.clientDataJSON, "login");
    const authId = this.store.byCredentialId.get(response.credentialId);
    const auth = authId ? this.store.authenticators.get(authId) : undefined;
    if (!auth) throw new CidError("unknown_key", "This key isn't registered to a CID");

    let result;
    try {
      result = verifyAssertion(response, auth, { challenge: challenge.value, origins: this.cfg.origins, rpId: this.cfg.rpId });
    } catch (err) {
      await this.record("audit", auth.cid, auth.cid, { event: "signin.failed", authenticator: auth.id, reason: (err as Error).message });
      throw new CidError("webauthn_failed", (err as Error).message);
    }
    auth.signCount = result.signCount;
    auth.lastUsedAt = this.now().toISOString();

    const account = this.account(auth.cid);
    // A user-verified passkey (biometric or PIN on the device) is already two factors.
    // A security key tap without verification needs the authenticator-app code too.
    if (account.totp?.confirmed && !result.userVerified) {
      const mfaToken = randomBytes(24).toString("base64url");
      this.store.pendingMfa.set(mfaToken, { cid: account.cid, authenticatorId: auth.id, device, expiresAt: this.now().getTime() + CHALLENGE_TTL_MS });
      return { status: "mfa_required", mfaToken };
    }
    const issued = await this.issueSession(account.cid, device, auth.kind);
    return { status: "ok", ...issued };
  }

  async completeMfa(mfaToken: string, code: string) {
    const pending = this.store.pendingMfa.get(mfaToken);
    if (!pending || pending.expiresAt < this.now().getTime()) {
      this.store.pendingMfa.delete(mfaToken);
      throw new CidError("challenge_expired", "Sign-in request expired. Please try again.");
    }
    const account = this.account(pending.cid);
    const step = account.totp ? verifyTotp(account.totp.secret, code, this.now(), account.totp.lastStep) : null;
    if (step === null) {
      await this.record("audit", account.cid, account.cid, { event: "mfa.failed" });
      throw new CidError("mfa_failed", "That code didn't match");
    }
    account.totp!.lastStep = step;
    this.store.pendingMfa.delete(mfaToken);
    return this.issueSession(account.cid, pending.device, "passkey+totp");
  }

  /* ---------------- Multifactor (authenticator app) ---------------- */

  enrollTotp(cid: string) {
    const account = this.account(cid);
    if (account.totp?.confirmed) throw new CidError("already_enabled", "Multifactor is already on");
    const secret = generateTotpSecret();
    account.totp = { secret, confirmed: false, lastStep: -1 };
    return { secret, uri: otpauthUri(secret, account.mailAddress) };
  }

  async confirmTotp(cid: string, code: string) {
    const account = this.account(cid);
    if (!account.totp) throw new CidError("not_enrolled", "Start multifactor setup first");
    const step = verifyTotp(account.totp.secret, code, this.now(), account.totp.lastStep);
    if (step === null) throw new CidError("mfa_failed", "That code didn't match");
    account.totp = { ...account.totp, confirmed: true, lastStep: step };
    await this.record("audit", cid, cid, { event: "mfa.enabled", method: "totp" });
  }

  async disableTotp(cid: string, code: string) {
    const account = this.account(cid);
    if (!account.totp?.confirmed) throw new CidError("not_enrolled", "Multifactor is not on");
    if (verifyTotp(account.totp.secret, code, this.now(), account.totp.lastStep) === null) {
      throw new CidError("mfa_failed", "That code didn't match");
    }
    account.totp = undefined;
    await this.record("audit", cid, cid, { event: "mfa.disabled", method: "totp" });
  }

  /* ---------------- Sessions ---------------- */

  private async issueSession(cid: string, device: string, method: Session["method"]) {
    const token = randomBytes(32).toString("base64url");
    const now = this.now();
    const session: Session = {
      id: `ses_${randomUUID()}`,
      cid,
      tokenHash: hashToken(token), // raw tokens are never stored
      device,
      method,
      createdAt: now.toISOString(),
      expiresAt: new Date(now.getTime() + this.cfg.sessionDays * 86_400_000).toISOString(),
    };
    this.store.sessions.set(session.id, session);
    this.store.byTokenHash.set(session.tokenHash, session.id);
    await this.record("audit", cid, cid, { event: "session.created", session: session.id, method });
    return { token, session };
  }

  /** Called by every Infinitum product on each request. Returns null for unknown, expired or revoked tokens. */
  authenticate(token: string): { account: Account; session: Session } | null {
    const id = this.store.byTokenHash.get(hashToken(token));
    const session = id ? this.store.sessions.get(id) : undefined;
    if (!session || session.revokedAt || new Date(session.expiresAt) <= this.now()) return null;
    return { account: this.account(session.cid), session };
  }

  listSessions(cid: string): Omit<Session, "tokenHash">[] {
    return [...this.store.sessions.values()]
      .filter((s) => s.cid === cid && !s.revokedAt && new Date(s.expiresAt) > this.now())
      .map(({ tokenHash: _t, ...rest }) => rest);
  }

  async revokeSession(cid: string, sessionId: string, actor = cid) {
    const s = this.store.sessions.get(sessionId);
    if (!s || s.cid !== cid) throw new CidError("not_found", "No such session");
    if (!s.revokedAt) {
      s.revokedAt = this.now().toISOString();
      await this.record("audit", actor, cid, { event: "session.revoked", session: sessionId });
    }
  }

  /** "Sign out everywhere", optionally keeping the current device signed in. */
  async revokeAllSessions(cid: string, keepSessionId?: string, actor = cid) {
    let count = 0;
    for (const s of this.store.sessions.values()) {
      if (s.cid === cid && !s.revokedAt && s.id !== keepSessionId) {
        s.revokedAt = this.now().toISOString();
        count++;
      }
    }
    await this.record("audit", actor, cid, { event: "session.revoked_all", count });
    return count;
  }

  /* ---------------- Permissions dashboard ---------------- */

  private assertApp(app: string): asserts app is AppId {
    if (!(INFINITUM_APPS as readonly string[]).includes(app)) throw new CidError("unknown_app", `Unknown Infinitum app: ${app}`);
  }

  /** Grant an app access, app by app. Every grant is recorded on Firmament Network. */
  async grant(cid: string, app: string, scopes: string[]): Promise<Grant> {
    this.account(cid);
    this.assertApp(app);
    if (scopes.length === 0 || scopes.some((s) => !/^[a-z0-9-]+:[a-z0-9-]+$/.test(s))) {
      throw new CidError("invalid_scope", "Scopes look like 'contacts:read'");
    }
    const key = `${cid}|${app}`;
    const merged = [...new Set([...(this.store.grants.get(key)?.scopes ?? []), ...scopes])].sort();
    const entry = await this.record("grant", cid, cid, { app, scopes: merged });
    const grant: Grant = { cid, app, scopes: merged, grantedAt: entry.timestamp, ledgerHash: entry.hash };
    this.store.grants.set(key, grant);
    return grant;
  }

  /** Revoke some scopes, or all access when scopes are omitted. Recorded on Firmament. */
  async revoke(cid: string, app: string, scopes?: string[]) {
    this.assertApp(app);
    const key = `${cid}|${app}`;
    const current = this.store.grants.get(key);
    if (!current) return;
    const remaining = scopes ? current.scopes.filter((s) => !scopes.includes(s)) : [];
    const entry = await this.record("revoke", cid, cid, { app, revoked: scopes ?? current.scopes, remaining });
    if (remaining.length === 0) this.store.grants.delete(key);
    else this.store.grants.set(key, { ...current, scopes: remaining, grantedAt: entry.timestamp, ledgerHash: entry.hash });
  }

  can(cid: string, app: string, scope: string): boolean {
    return this.store.grants.get(`${cid}|${app}`)?.scopes.includes(scope) ?? false;
  }

  /** One permissions dashboard: which product can use what. */
  permissions(cid: string): Grant[] {
    return [...this.store.grants.values()].filter((g) => g.cid === cid).sort((a, b) => a.app.localeCompare(b.app));
  }

  /** Full Firmament history of this CID's grants and revocations. */
  async permissionHistory(cid: string) {
    const entries = await this.cfg.ledger.query({ product: "cid", subject: cid });
    return entries.filter((e) => e.type === "grant" || e.type === "revoke");
  }

  /* ---------------- Teams, organizations and SSO ---------------- */

  async createOrganization(name: string, adminCid: string): Promise<Organization> {
    const admin = this.account(adminCid);
    const org: Organization = { id: `org_${randomUUID()}`, name, admins: [adminCid] };
    this.store.orgs.set(org.id, org);
    admin.orgId = org.id;
    await this.record("identity", adminCid, org.id, { event: "org.created" });
    return org;
  }

  private org(orgId: string, adminCid?: string): Organization {
    const org = this.store.orgs.get(orgId);
    if (!org) throw new CidError("not_found", "No such organization");
    if (adminCid && !org.admins.includes(adminCid)) throw new CidError("forbidden", "Organization admins only");
    return org;
  }

  async configureSso(orgId: string, adminCid: string, sso: NonNullable<Organization["sso"]>) {
    const org = this.org(orgId, adminCid);
    org.sso = sso;
    await this.record("audit", adminCid, orgId, { event: "org.sso_configured", issuer: sso.issuer });
  }

  /** Sign in with the organization's identity provider; creates the member's CID on first sign-in. */
  async signInWithSso(orgId: string, idToken: string, device: string, nonce?: string) {
    const org = this.org(orgId);
    if (!org.sso) throw new CidError("sso_not_configured", "This organization hasn't set up single sign-on");
    let claims;
    try {
      claims = verifyIdToken(idToken, org.sso, { now: this.now(), nonce });
    } catch (err) {
      throw new CidError("sso_failed", (err as Error).message);
    }
    if (org.sso.emailDomain) {
      const domain = claims.email?.split("@")[1]?.toLowerCase();
      if (!claims.email_verified || domain !== org.sso.emailDomain.toLowerCase()) {
        throw new CidError("sso_failed", "Account is not part of this organization's domain");
      }
    }
    const fedKey = `${claims.iss}|${claims.sub}`;
    let cid = this.store.byFederation.get(fedKey);
    if (!cid) {
      const base = (claims.email?.split("@")[0] ?? claims.sub).toLowerCase().replace(/[^a-z0-9._-]/g, "").slice(0, 24) || "member";
      let username = base.length >= 3 ? base : `${base}-user`;
      for (let i = 2; this.store.byUsername.has(username) || RESERVED.has(username); i++) username = `${base}${i}`;
      const account = await this.createAccount({ username, orgId });
      account.federated = { issuer: claims.iss, subject: claims.sub };
      this.store.byFederation.set(fedKey, account.cid);
      cid = account.cid;
    }
    return this.issueSession(cid, device, "sso");
  }

  orgMembers(orgId: string, adminCid: string): Account[] {
    this.org(orgId, adminCid);
    return [...this.store.accounts.values()].filter((a) => a.orgId === orgId);
  }

  /** Admin control: sign a member out of every device (e.g. a lost laptop or offboarding). */
  async adminRevokeMemberSessions(orgId: string, adminCid: string, memberCid: string) {
    this.org(orgId, adminCid);
    if (this.account(memberCid).orgId !== orgId) throw new CidError("forbidden", "Not a member of this organization");
    return this.revokeAllSessions(memberCid, undefined, adminCid);
  }
}
