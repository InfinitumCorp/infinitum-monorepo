// Vercel entry (Node.js runtime, Fluid Compute). All routes are rewritten here by vercel.json.
import { handle } from "hono/vercel";
import { bootFromEnv } from "../src/boot.js";

const booted = bootFromEnv();
export default async function handler(req: Request) {
  const { app } = await booted;
  return handle(app)(req);
}
