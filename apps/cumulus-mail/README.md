# Cumulus://Mail

The mailbox that comes with every Cumulus://ID, the way Gmail comes with a Google account.
Sign up in any Infinitum product and you get `username@cumulusmail.cloud`, Cumulus://Cloud storage
and a FIRM Wallet, all on the free tier. The account suite is in `@infinitum/cumulus`; mailbox rules
are in `@infinitum/mail`. This app serves them.

**Unlisted.** Mail is not on the public site. Every response sends `X-Robots-Tag: noindex`,
`robots.txt` disallows everything, and people reach it from inside other products (the account
menu in +SPCTR, links in notices).

```sh
node --experimental-strip-types apps/cumulus-mail/src/server.ts
```

Open http://localhost:3010 and sign in with a passkey.

| Variable | Purpose |
|---|---|
| `PORT` | Listener, default 3010 |
| `MAIL_ORIGIN` | Public origin; passkeys are bound to it |
| `MAIL_DATA_DIR` | Where stores and the Firmament ledger live, default `./data` |
| `XAI_API_KEY` | Turns on SCALVS briefs |
| Social sign-in | Same variables as +SPCTR; callback `{MAIL_ORIGIN}/api/auth/oauth/{provider}/callback` |

## What's in it

- **Sign-in with Cumulus://ID.** Passkeys, authenticator-app MFA, social sign-in. Access needs the
  `cumulus-mail` grant, so revoking Mail in the CID permissions dashboard cuts it off at once.
- **Real delivery.** Mail between Cumulus addresses lands in the recipient's inbox and threads
  replies. Unknown Cumulus addresses bounce. Outside addresses are queued for the outbound relay
  (`PostOffice.outboundQueue()`); the SMTP bridge itself is not built yet.
- **Login security mail.** Sign-ins from a new device, new or removed keys, MFA changes, linked
  providers and "signed out everywhere" each send a notice from `security@cumulusmail.cloud`.
  Product sender names (`security`, `wallet`, `cloud`, `welcome`, `postmaster`, …) are reserved and
  can't be registered.
- **Shared 15 GB.** Mail and Cloud draw from one quota. A full account can't send; a full recipient bounces.
- **FIRM Wallet.** Every CID's wallet opens with the account. Send USDC or FIRM directly, or attach a
  payment to a message (one Cumulus recipient). The money moves first and all at once, so a message
  never shows a payment that failed. Each payment is a Firmament `payment` entry (amounts and wallet
  IDs only, no mail content). The server never holds wallet private keys.

## Local test data

`scripts/dev-seed.ts` creates two test accounts with a virtual passkey and prints a local session
cookie, so the signed-in app can be checked without a hardware key. It refuses https origins.
