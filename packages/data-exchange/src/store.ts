import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { ConsentRecord, DataBroker, Dataset, LicenseSale } from "./types.ts";
import type { CdatToken } from "./cdat.ts";
import type { Payout } from "./revshare.ts";

/** Mutable exchange snapshot. The Firmament ledger stays append-only beside this. */
export interface ExchangeSnapshot {
  datasets: Dataset[];
  consents: ConsentRecord[];
  sales: LicenseSale[];
  brokers: DataBroker[];
  tokens: CdatToken[];
  nextOnchainId: number;
  payouts: Payout[];
}

export async function readSnapshot(path: string): Promise<ExchangeSnapshot | undefined> {
  try {
    const text = await readFile(path, "utf8");
    return JSON.parse(text) as ExchangeSnapshot;
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code === "ENOENT") return undefined;
    throw err;
  }
}

export async function writeSnapshot(path: string, snapshot: ExchangeSnapshot): Promise<void> {
  await mkdir(dirname(path), { recursive: true });
  const tmp = `${path}.tmp`;
  await writeFile(tmp, JSON.stringify(snapshot));
  await rename(tmp, path);
}
