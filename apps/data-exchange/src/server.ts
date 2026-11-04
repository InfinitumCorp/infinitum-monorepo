/**
 * Minimal HTTP API for the Data Exchange.
 * Run with: node --experimental-strip-types src/server.ts
 *
 * Endpoints (JSON):
 * POST /datasets          — package a new dataset (body matches packageDataset params)
 * GET  /datasets          — list listed datasets
 * POST /sell              — { datasetId, buyerId, amountUsd }  (mints a CDAT license)
 * GET  /sales             — list sales
 * GET  /cdat              — list CDAT license tokens
 * GET  /cdat/:tokenId     — one CDAT token plus its on-chain mint intent
 * GET  /payouts/:cid      — pending payouts for a CID
 */
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { DataExchange } from "@infinitum/data-exchange";
import { Ledger, generateSigner } from "@infinitum/firmament";

const ledger = new Ledger();
const signer = generateSigner();
ledger.authorize(signer.publicKey);

const dx = new DataExchange(ledger, signer);

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => resolve(Buffer.concat(chunks).toString()));
    req.on("error", reject);
  });
}

async function handle(req: IncomingMessage, res: ServerResponse) {
  const url = new URL(req.url ?? "/", "http://localhost");
  res.setHeader("Content-Type", "application/json");

  try {
    if (req.method === "POST" && url.pathname === "/datasets") {
      const body = JSON.parse(await readBody(req));
      const result = await dx.packageDataset(body);
      dx.catalog.updateStatus(result.dataset.id, "listed");
      res.end(JSON.stringify(result));
      return;
    }

    if (req.method === "GET" && url.pathname === "/datasets") {
      res.end(JSON.stringify(dx.listForSale()));
      return;
    }

    if (req.method === "POST" && url.pathname === "/sell") {
      const body = JSON.parse(await readBody(req));
      const sale = await dx.sell(body.datasetId, body.buyerId, body.amountUsd);
      if (!sale) {
        res.end(JSON.stringify({ error: "not found or not listed" }));
        return;
      }
      const token = sale.cdatTokenId ? dx.cdat.get(sale.cdatTokenId) : undefined;
      res.end(JSON.stringify({ sale, cdat: token ?? null }));
      return;
    }

    if (req.method === "GET" && url.pathname === "/sales") {
      res.end(JSON.stringify(dx.marketplace.listSales()));
      return;
    }

    if (req.method === "GET" && url.pathname === "/cdat") {
      res.end(JSON.stringify(dx.cdat.list()));
      return;
    }

    if (req.method === "GET" && url.pathname.startsWith("/cdat/")) {
      const tokenId = decodeURIComponent(url.pathname.slice("/cdat/".length));
      const token = dx.cdat.get(tokenId);
      if (!token) {
        res.statusCode = 404;
        res.end(JSON.stringify({ error: "not found" }));
        return;
      }
      res.end(JSON.stringify({
        token,
        active: dx.cdat.isActive(tokenId),
        mint: dx.cdat.mintIntent(token),
      }));
      return;
    }

    if (req.method === "GET" && url.pathname.startsWith("/payouts/")) {
      const cid = url.pathname.split("/")[2];
      res.end(JSON.stringify({
        cid,
        pendingUsd: dx.revshare.pendingForCid(cid),
        payouts: dx.revshare.listPayouts(cid),
      }));
      return;
    }

    res.statusCode = 404;
    res.end(JSON.stringify({ error: "not found" }));
  } catch (err) {
    res.statusCode = 500;
    res.end(JSON.stringify({ error: String(err) }));
  }
}

const port = Number(process.env.PORT ?? 8787);
createServer(handle).listen(port, () => {
  console.log(`Data Exchange API listening on http://localhost:${port}`);
});
