// Pushes catalog products, prices and billing meters to Stripe. Run with a TEST key first.
import Stripe from "stripe";
import { syncCatalogToStripe } from "../src/payments.js";

const key = process.env.STRIPE_SECRET_KEY;
if (!key) throw new Error("Set STRIPE_SECRET_KEY");
if (key.startsWith("sk_live") && process.env.CONFIRM_LIVE !== "yes") throw new Error("Live key detected. Re-run with CONFIRM_LIVE=yes.");
await syncCatalogToStripe(new Stripe(key));
