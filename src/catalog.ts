/**
 * Infinitum revenue catalog: every product, every way it earns money.
 *
 * All prices are PROPOSED launch prices in US cents. They are a starting point
 * for pricing review, not decisions. Change them here; `npm run stripe:sync`
 * pushes products and prices to Stripe (matched by lookup key).
 *
 * Sales channels:
 *  - self_serve: customer pays through Checkout.
 *  - sales_assisted: requires an approved org (KYB / purpose review) before checkout.
 *  - contract: never self-serve. Quoted, contracted and invoiced (government, military).
 */

export type Channel = "self_serve" | "sales_assisted" | "contract";
export type Interval = "month" | "year";
export type OrgKind = "individual" | "business" | "government" | "military";

export interface Meter {
  /** Stable id; also the Stripe Billing Meter event name. */
  id: string;
  product: ProductId;
  unit: string;
  /** Overage / pay-as-you-go price per unit, in cents (may be fractional). */
  unitPriceCents: number;
}

export interface Plan {
  id: string;
  product: ProductId;
  name: string;
  channel: Channel;
  /** Recurring price in cents per interval (per seat when perSeat). 0 = free. */
  priceCents: number;
  interval: Interval;
  perSeat?: boolean;
  /** Included units per billing period, keyed by meter id. */
  included?: Record<string, number>;
  /** When true, usage beyond `included` is billed at the meter price. Otherwise it is blocked (or paid from prepaid credits). */
  overage?: boolean;
  /**
   * Plans in other products that come with this one. While an org holds this plan, each bundled plan
   * applies to its product unless the org has its own subscription there.
   */
  bundle?: string[];
  features?: string[];
  /** Only these org kinds may hold the plan. Defaults to all except military. */
  allowedKinds?: OrgKind[];
  trialDays?: number;
}

export interface HardwareSku {
  id: string;
  product: ProductId;
  name: string;
  priceCents: number;
  /** Fraction of the price taken at order time for pre-orders (0..1). 1 = pay in full. */
  depositRatio: number;
  /** Optional monthly lease / robotics-as-a-service alternative. */
  leasePlanId?: string;
  channel: Channel;
}

export interface MarketplaceTerms {
  product: ProductId;
  /** Share of net licence revenue paid to the data contributor, in basis points. */
  contributorShareBps: number;
  /** Fee added on top of a mission (bounty) budget, in basis points. */
  missionFeeBps: number;
  /** Days earnings are held before they can be paid out (refund / dispute window). */
  holdDays: number;
}

export const PRODUCTS = [
  "scalvs", "cndctr", "spectre", "metatron", "fabric", "cumulus", "eidetic", "hrvst",
  "veil", "throne", "eon", "orbis", "pollen", "flower", "slug", "daemon",
  "firmament", "firm_wallet", "services", "odin", "academy",
] as const;
export type ProductId = (typeof PRODUCTS)[number];

const ALL_CIVIL: OrgKind[] = ["individual", "business", "government"];
const ORGS_ONLY: OrgKind[] = ["business", "government"];

/**
 * What a Cumulus://ID™ (CID) brings: one sign-in for every Infinitum app, plus Cumulus://Mail™
 * (name@cumuluscloud.app) and Cumulus://Drive™ storage. Any plan that "comes with Cumulus://ID"
 * lists these features.
 */
export const CUMULUS_ID_FEATURES = ["cumulus_id", "cumulus_id_sso", "cumulus_mail", "cumulus_drive"] as const;

/** The $FIRM Suite™: the apps in it, and the Cumulus://ID™ services that come with it. */
export const FIRM_SUITE = {
  planId: "firm_suite",
  apps: ["$FIRM Wallet™", "$FIRM™ any-token swap", "$Firmament Network™"],
  comesWith: { account: "Cumulus://ID™ (CID)", services: ["Cumulus://Mail™", "Cumulus://Drive™", "Single sign-on to every Infinitum app"] },
} as const;

export const METERS: Meter[] = [
  { id: "scalvs_agent_task", product: "scalvs", unit: "agent task", unitPriceCents: 2 },
  { id: "cndctr_execution_credit", product: "cndctr", unit: "execution credit", unitPriceCents: 10 },
  { id: "cndctr_verified_outcome", product: "cndctr", unit: "verified outcome", unitPriceCents: 50 },
  { id: "spectre_agent_credit", product: "spectre", unit: "AI agent credit", unitPriceCents: 5 },
  { id: "spectre_deal_rehearsal", product: "spectre", unit: "deal rehearsal", unitPriceCents: 2500 },
  { id: "metatron_prediction_run", product: "metatron", unit: "prediction run", unitPriceCents: 25 },
  { id: "cumulus_storage_gb_month", product: "cumulus", unit: "GB-month", unitPriceCents: 2 },
  { id: "cumulus_egress_gb", product: "cumulus", unit: "GB egress", unitPriceCents: 5 },
  { id: "cumulus_vcpu_hour", product: "cumulus", unit: "vCPU-hour", unitPriceCents: 4 },
  { id: "cumulus_gpu_hour", product: "cumulus", unit: "GPU-hour", unitPriceCents: 220 },
  { id: "cumulus_inference_mtok", product: "cumulus", unit: "1M inference tokens", unitPriceCents: 60 },
  { id: "eidetic_verified_search", product: "eidetic", unit: "authorised search", unitPriceCents: 50 },
  { id: "firmament_attestation", product: "firmament", unit: "attestation", unitPriceCents: 0.1 },
  { id: "fabric_business_minutes", product: "fabric", unit: "PSTN/bridge minute", unitPriceCents: 1 },
];

export const PLANS: Plan[] = [
  // SCALVS.ai — AI workforce
  { id: "scalvs_free", product: "scalvs", name: "SCALVS.ai Free", channel: "self_serve", priceCents: 0, interval: "month", included: { scalvs_agent_task: 100 }, allowedKinds: ALL_CIVIL },
  { id: "scalvs_pro", product: "scalvs", name: "SCALVS.ai Pro", channel: "self_serve", priceCents: 2900, interval: "month", included: { scalvs_agent_task: 2000 }, overage: true, trialDays: 14, allowedKinds: ALL_CIVIL },
  { id: "scalvs_team", product: "scalvs", name: "SCALVS.ai Team", channel: "self_serve", priceCents: 4900, interval: "month", perSeat: true, included: { scalvs_agent_task: 5000 }, overage: true, allowedKinds: ORGS_ONLY },
  { id: "scalvs_private", product: "scalvs", name: "SCALVS.ai Private Tenant", channel: "sales_assisted", priceCents: 250000, interval: "month", included: { scalvs_agent_task: 250000 }, overage: true, features: ["isolated_tenant", "sso", "audit_export"], allowedKinds: ORGS_ONLY },

  // :\ARCHITECT\ (catalog id "cndctr") — AI execution workspace
  { id: "cndctr_free", product: "cndctr", name: ":\\ARCHITECT\\ Free", channel: "self_serve", priceCents: 0, interval: "month", included: { cndctr_execution_credit: 50 }, allowedKinds: ALL_CIVIL },
  { id: "cndctr_pro", product: "cndctr", name: ":\\ARCHITECT\\ Pro", channel: "self_serve", priceCents: 3900, interval: "month", included: { cndctr_execution_credit: 500, cndctr_verified_outcome: 20 }, overage: true, features: ["autopilot"], trialDays: 14, allowedKinds: ALL_CIVIL },
  { id: "cndctr_business", product: "cndctr", name: ":\\ARCHITECT\\ Business", channel: "self_serve", priceCents: 9900, interval: "month", perSeat: true, included: { cndctr_execution_credit: 2000, cndctr_verified_outcome: 100 }, overage: true, features: ["autopilot", "standing_permissions", "sso"], allowedKinds: ORGS_ONLY },

  // +SPCTR — intelligent CRM
  { id: "spectre_starter", product: "spectre", name: "+SPCTR Starter", channel: "self_serve", priceCents: 4900, interval: "month", perSeat: true, included: { spectre_agent_credit: 500 }, overage: true, trialDays: 14, allowedKinds: ORGS_ONLY },
  { id: "spectre_growth", product: "spectre", name: "+SPCTR Growth", channel: "self_serve", priceCents: 9900, interval: "month", perSeat: true, included: { spectre_agent_credit: 2000, spectre_deal_rehearsal: 2 }, overage: true, features: ["deal_rehearsal", "shared_truth_ledger"], allowedKinds: ORGS_ONLY },
  { id: "spectre_enterprise", product: "spectre", name: "+SPCTR Enterprise", channel: "sales_assisted", priceCents: 15900, interval: "month", perSeat: true, included: { spectre_agent_credit: 5000, spectre_deal_rehearsal: 10 }, overage: true, features: ["deal_rehearsal", "shared_truth_ledger", "autonomous_close", "sso"], allowedKinds: ORGS_ONLY },

  // METATRON¹⁰⁰ — consumer command suite (bundle of consumer products)
  { id: "metatron_personal", product: "metatron", name: "METATRON¹⁰⁰ Personal", channel: "self_serve", priceCents: 1900, interval: "month", included: { metatron_prediction_run: 100 }, overage: true, trialDays: 30, allowedKinds: ["individual"] },
  { id: "metatron_family", product: "metatron", name: "METATRON¹⁰⁰ Family", channel: "self_serve", priceCents: 3900, interval: "month", included: { metatron_prediction_run: 300 }, overage: true, features: ["up_to_6_members"], allowedKinds: ["individual"] },
  { id: "metatron_business", product: "metatron", name: "METATRON¹⁰⁰ Business", channel: "self_serve", priceCents: 14900, interval: "month", included: { metatron_prediction_run: 1500 }, overage: true, features: ["fleet_view", "device_dashboards"], allowedKinds: ORGS_ONLY },
  { id: "metatron_annual", product: "metatron", name: "METATRON¹⁰⁰ Personal (annual)", channel: "self_serve", priceCents: 19000, interval: "year", included: { metatron_prediction_run: 1200 }, overage: true, allowedKinds: ["individual"] },

  // FABRIC — secure comms (consumer is free; business pays per seat)
  { id: "fabric_personal", product: "fabric", name: "FABRIC Personal", channel: "self_serve", priceCents: 0, interval: "month", allowedKinds: ALL_CIVIL },
  { id: "fabric_business", product: "fabric", name: "FABRIC Business", channel: "self_serve", priceCents: 800, interval: "month", perSeat: true, included: { fabric_business_minutes: 500 }, overage: true, features: ["admin_console", "retention_policies", "compliance_export"], allowedKinds: ORGS_ONLY },

  // Cumulus://Cloud — drive plans + pay-as-you-go cloud
  { id: "cumulus_drive_free", product: "cumulus", name: "Cumulus Drive Free (5 GB)", channel: "self_serve", priceCents: 0, interval: "month", included: { cumulus_storage_gb_month: 5 }, allowedKinds: ALL_CIVIL },
  { id: "cumulus_drive_200", product: "cumulus", name: "Cumulus Drive 200 GB", channel: "self_serve", priceCents: 299, interval: "month", included: { cumulus_storage_gb_month: 200 }, allowedKinds: ALL_CIVIL },
  { id: "cumulus_drive_2tb", product: "cumulus", name: "Cumulus Drive 2 TB", channel: "self_serve", priceCents: 999, interval: "month", included: { cumulus_storage_gb_month: 2000 }, allowedKinds: ALL_CIVIL },
  { id: "cumulus_cloud_payg", product: "cumulus", name: "Cumulus Cloud (pay as you go)", channel: "self_serve", priceCents: 0, interval: "month", overage: true, allowedKinds: ALL_CIVIL },
  { id: "cumulus_sovereign", product: "cumulus", name: "Cumulus Sovereign Region", channel: "contract", priceCents: 0, interval: "year", overage: true, features: ["dedicated_region", "air_gap_option"], allowedKinds: ["government"] },

  // ei·det·ic — identity intelligence (approved organisations only, per-search)
  { id: "eidetic_enterprise", product: "eidetic", name: "ei·det·ic Enterprise", channel: "sales_assisted", priceCents: 200000, interval: "month", included: { eidetic_verified_search: 1000 }, overage: true, features: ["purpose_controls", "human_review_queue", "audit_ledger"], allowedKinds: ORGS_ONLY },

  // [HRVST] — free to contribute; Pro tools for creators; buyers pay per licence / mission
  { id: "hrvst_free", product: "hrvst", name: "[HRVST] Free", channel: "self_serve", priceCents: 0, interval: "month", allowedKinds: ALL_CIVIL },
  { id: "hrvst_pro", product: "hrvst", name: "[HRVST] Pro Contributor", channel: "self_serve", priceCents: 999, interval: "month", features: ["personal_silo_unlimited", "priority_missions", "advanced_filters"], allowedKinds: ["individual", "business"] },
  { id: "hrvst_buyer", product: "hrvst", name: "[HRVST] Buyer Access", channel: "sales_assisted", priceCents: 49900, interval: "month", features: ["post_missions", "bulk_licensing", "api"], allowedKinds: ORGS_ONLY },

  // $FIRM Suite™ — $FIRM Wallet™, any-token swap and $Firmament Network™, and it comes with a
  // Cumulus://ID™ (CID): the one Infinitum account, with Cumulus://Mail™ and Cumulus://Drive™ included.
  { id: "firm_suite", product: "firm_wallet", name: "$FIRM Suite", channel: "self_serve", priceCents: 0, interval: "month",
    features: ["firm_wallet", "any_token_swap", "firmament_network", ...CUMULUS_ID_FEATURES],
    bundle: ["cumulus_drive_free", "firmament_dev"], allowedKinds: ALL_CIVIL },

  // Firmament Network — attestation API + enterprise permissioned instances
  { id: "firmament_dev", product: "firmament", name: "Firmament Developer", channel: "self_serve", priceCents: 0, interval: "month", included: { firmament_attestation: 10000 }, overage: true, allowedKinds: ALL_CIVIL },
  { id: "firmament_permissioned", product: "firmament", name: "Firmament Permissioned Instance", channel: "sales_assisted", priceCents: 500000, interval: "month", included: { firmament_attestation: 10000000 }, overage: true, features: ["private_instance", "validator_licence"], allowedKinds: ORGS_ONLY },

  // Device software / care subscriptions
  { id: "throne_care", product: "throne", name: "#THRONE Care", channel: "self_serve", priceCents: 19900, interval: "year", features: ["accidental_damage", "next_day_replacement"], allowedKinds: ALL_CIVIL },
  { id: "throne_rack_software", product: "throne", name: "#THRONE Rack on-prem suite", channel: "sales_assisted", priceCents: 120000, interval: "month", features: ["onprem_scalvs", "onprem_spectre", "cumulus_node"], allowedKinds: ORGS_ONLY },
  { id: "eon_plus", product: "eon", name: "E(O)N Plus", channel: "self_serve", priceCents: 999, interval: "month", features: ["translation", "briefings", "spectrum_reports"], allowedKinds: ALL_CIVIL },
  { id: "orbis_fleet", product: "orbis", name: "ORBIS Fleet software", channel: "self_serve", priceCents: 4900, interval: "month", perSeat: true, features: ["3d_maps", "inspection_reports"], allowedKinds: ORGS_ONLY },
  { id: "pollen_fleet", product: "pollen", name: "POLLEN Swarm Operations", channel: "sales_assisted", priceCents: 49900, interval: "month", features: ["mission_planner", "fleet_ledger"], allowedKinds: ORGS_ONLY },
  { id: "slug_raas", product: "slug", name: "SLUG Robotics-as-a-Service", channel: "sales_assisted", priceCents: 180000, interval: "month", perSeat: true, features: ["unit_lease", "maintenance", "battery_swap"], allowedKinds: ORGS_ONLY },
  { id: "throne_book_lease", product: "throne", name: "#THRONE Book 24-month lease", channel: "self_serve", priceCents: 9900, interval: "month", allowedKinds: ALL_CIVIL },

  // Academy — training & certification
  { id: "academy_membership", product: "academy", name: "Infinitum Academy membership", channel: "self_serve", priceCents: 2900, interval: "month", allowedKinds: ALL_CIVIL },

  // Services retainer
  { id: "services_retainer", product: "services", name: "Managed services retainer", channel: "contract", priceCents: 0, interval: "month", allowedKinds: ["business", "government", "military"] },

  // ODIN° — military only, never self-serve, outside the token economy
  { id: "odin_suite", product: "odin", name: "ODIN° suite", channel: "contract", priceCents: 0, interval: "year", allowedKinds: ["military"] },
];

export const HARDWARE: HardwareSku[] = [
  { id: "throne_book_14", product: "throne", name: '#THRONE Book 14"', priceCents: 199900, depositRatio: 1, leasePlanId: "throne_book_lease", channel: "self_serve" },
  { id: "throne_book_16", product: "throne", name: '#THRONE Book 16"', priceCents: 279900, depositRatio: 1, channel: "self_serve" },
  { id: "throne_tower", product: "throne", name: "#THRONE Tower", priceCents: 399900, depositRatio: 1, channel: "self_serve" },
  { id: "throne_rack_1u", product: "throne", name: "#THRONE Rack 1U", priceCents: 899900, depositRatio: 0.3, channel: "sales_assisted" },
  { id: "eon_wearable", product: "eon", name: "E(O)N wearable", priceCents: 49900, depositRatio: 0.2, channel: "self_serve" },
  { id: "orbis_kit", product: "orbis", name: "ORBIS + cradle", priceCents: 249900, depositRatio: 0.2, channel: "self_serve" },
  { id: "pollen_flower_kit", product: "pollen", name: "Flower station + 50 POLLEN aircraft", priceCents: 2499900, depositRatio: 0.3, channel: "sales_assisted" },
  { id: "flower_station", product: "flower", name: "Flower base station (portable)", priceCents: 899900, depositRatio: 0.3, channel: "sales_assisted" },
  { id: "slug_unit", product: "slug", name: "SLUG land drone", priceCents: 3900000, depositRatio: 0.3, leasePlanId: "slug_raas", channel: "sales_assisted" },
  { id: "daemon_warden_site", product: "daemon", name: "DAEMON WARDEN site package", priceCents: 0, depositRatio: 0, channel: "contract" },
  { id: "daemon_zenith_service", product: "daemon", name: "DAEMON ZENITH coverage service", priceCents: 0, depositRatio: 0, channel: "contract" },
];

// 85% of net licence revenue to the contributor on every marketplace, as published on
// infinitumcorporation.ai and in infinitum-nexus packages/data-exchange (SHARE = 0.85).
export const MARKETPLACES: MarketplaceTerms[] = [
  { product: "fabric", contributorShareBps: 8500, missionFeeBps: 0, holdDays: 14 },
  { product: "hrvst", contributorShareBps: 8500, missionFeeBps: 2000, holdDays: 14 },
  { product: "cumulus", contributorShareBps: 8500, missionFeeBps: 1500, holdDays: 14 },
  { product: "scalvs", contributorShareBps: 8500, missionFeeBps: 0, holdDays: 7 }, // agent template marketplace
];

export const CREDIT_PACKS = [
  { id: "credits_25", priceCents: 2500, creditCents: 2500 },
  { id: "credits_100", priceCents: 10000, creditCents: 10500 },
  { id: "credits_500", priceCents: 50000, creditCents: 55000 },
];

export const PAYOUT_MINIMUM_CENTS = 1000;

export const getPlan = (id: string) => PLANS.find((p) => p.id === id);
export const getMeter = (id: string) => METERS.find((m) => m.id === id);
export const getSku = (id: string) => HARDWARE.find((h) => h.id === id);
export const getMarketplace = (product: string) => MARKETPLACES.find((m) => m.product === product);

/**
 * A company that buys consented observations. `prospective` means they fit the
 * marketplace and are the account to approach. It is not a signed contract,
 * and it does not create an org, a balance, or a licence.
 */
export interface AnchorBuyer {
  id: string;
  name: string;
  site: string;
  products: ProductId[];
  /** Place classes these buyers ask for. A restricted class can still be licensed when the owner opts in; these buyers do not ask for one. */
  placeClasses: Array<"street" | "retail" | "field" | "public_building">;
  purposes: string[];
  status: "prospective";
}

export const ANCHOR_BUYERS: AnchorBuyer[] = [
  {
    id: "premise",
    name: "Premise",
    site: "https://premise.com",
    products: ["hrvst", "cumulus"],
    placeClasses: ["retail", "street", "public_building", "field"],
    purposes: ["retail_shelf_audit", "public_place_inspection"],
    status: "prospective",
  },
  {
    id: "field-agent",
    name: "Field Agent",
    site: "https://www.fieldagent.net",
    products: ["hrvst"],
    placeClasses: ["retail"],
    purposes: ["price_check", "display_check", "on_shelf_availability"],
    status: "prospective",
  },
  {
    id: "roamler",
    name: "Roamler",
    site: "https://www.roamler.com",
    products: ["hrvst", "cumulus"],
    placeClasses: ["retail"],
    purposes: ["fmcg_shelf_execution"],
    status: "prospective",
  },
];

export const getAnchorBuyer = (id: string) => ANCHOR_BUYERS.find((b) => b.id === id);

export const freePlanFor = (product: ProductId) =>
  PLANS.find((p) => p.product === product && p.priceCents === 0 && p.channel === "self_serve" && !p.overage);

export function canHold(plan: Plan, kind: OrgKind): boolean {
  const allowed = plan.allowedKinds ?? ALL_CIVIL;
  return allowed.includes(kind);
}
