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

`dx.mintCalldata(tokenId)` is the ABI-encoded `CdatLicense.mint` call (`0x01d47bed`) to submit on Base.
Pass a snapshot path as the third `DataExchange` argument to persist the catalog, consents, sales, CDAT tokens, and payouts across restarts. `settlePayout(saleId)` marks the owner's 85% share paid.
License and payment records are appended to Firmament when a ledger and signer are passed to `DataExchange`.

## Product integration
Each product collector:
1. Checks user consent via CID
2. Anonymizes / aggregates data
3. Calls packageDataset()

Private by default. Military/ODIN° data excluded.
Government workloads never enter the public exchange.
