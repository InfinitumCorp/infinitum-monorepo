# Data Exchange API

Minimal HTTP server for the Cumulus Data Exchange.

```sh
cd apps/data-exchange
node --experimental-strip-types src/server.ts
```

## Endpoints

- `POST /datasets` — package and list a dataset
- `GET /datasets` — listed datasets
- `POST /sell` — `{ "datasetId": "...", "buyerId": "...", "amountUsd": 100 }`
- `GET /sales`
- `GET /payouts/:cid`

Consent grants are recorded on an in-memory Firmament ledger.
