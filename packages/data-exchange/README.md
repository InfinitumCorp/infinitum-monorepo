# @infinitum/data-exchange

Opt-in Cumulus Data Exchange backend.

## Features
- Explicit consent recorded (Firmament-ready)
- Dataset catalog per product
- Marketplace for data brokers
- 85% revenue share to data owner
- CDAT license token support (stub)

## Usage
```ts
import { DataExchange } from "@infinitum/data-exchange";

const dx = new DataExchange();
const { dataset } = dx.packageDataset({ ... });
dx.catalog.updateStatus(dataset.id, "listed");
const sale = dx.sell(dataset.id, "broker_id", 100);
```

## Product integration
Each product (SCALVS, Cumulus, Spectre, etc.) provides a collector that:
1. Checks user consent via CID
2. Anonymizes / aggregates data
3. Calls packageDataset()

Private by default. Military/ODIN° data excluded.
Government workloads never enter the public exchange.
