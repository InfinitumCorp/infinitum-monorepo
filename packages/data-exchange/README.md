# @infinitum/data-exchange

Opt-in Cumulus Data Exchange backend.

## Features
- Explicit consent recorded on Firmament
- Dataset catalog per product
- Marketplace for data brokers
- 85% revenue share to the data owner
- One soulbound CDAT license token per sale (`CdatLicense.sol` + off-chain issuer)

## Usage
```ts
import { DataExchange } from "@infinitum/data-exchange";

const dx = new DataExchange();
const { dataset } = await dx.packageDataset({ /* ... */ });
dx.catalog.updateStatus(dataset.id, "listed");
const sale = await dx.sell(dataset.id, "broker_id", 100);
const cdat = dx.cdat.get(sale.cdatTokenId);
```

`cdat.mintIntent(token)` is the `CdatLicense.mint` call to submit on Base.
License and payment records are appended to Firmament when a ledger and signer are passed to `DataExchange`.

## Product integration
Each product collector:
1. Checks user consent via CID
2. Anonymizes / aggregates data
3. Calls packageDataset()

Private by default. Military/ODIN° data excluded.
Government workloads never enter the public exchange.
