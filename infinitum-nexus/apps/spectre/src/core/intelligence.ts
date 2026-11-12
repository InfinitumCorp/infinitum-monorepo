import type { Activity, Deal, Stage } from "./model.ts";
import { OPEN_STAGES } from "./model.ts";

/**
 * Predictive pipeline and next best action.
 *
 * This is a transparent baseline model: every score comes with the factors behind it,
 * so reps can see the reasoning ("with the reasoning shown"). It learns stage win rates
 * from the workspace's own closed deals once there are enough of them, and falls back
 * to industry-typical priors before that. The METATRON¹⁰⁰ prediction engine can replace
 * the scorer behind the same interface.
 */

const DAY = 86_400_000;

/** Typical B2B stage-to-win rates used until the workspace has its own history. */
export const PRIOR_STAGE_WIN_RATE: Record<Stage, number> = {
  lead: 0.05,
  qualified: 0.15,
  discovery: 0.25,
  proposal: 0.4,
  negotiation: 0.6,
  won: 1,
  lost: 0,
};

/** Expected days a healthy deal spends in each open stage before it's considered stuck. */
export const EXPECTED_STAGE_DAYS: Record<Stage, number> = {
  lead: 14, qualified: 21, discovery: 21, proposal: 21, negotiation: 30, won: 0, lost: 0,
};

export interface Factor {
  label: string;
  /** Multiplier applied to the base probability (1 = no effect). */
  effect: number;
}

export interface Score {
  dealId: string;
  probability: number; // 0..1
  base: number;
  factors: Factor[];
  risk: "low" | "medium" | "high";
}

/**
 * Learn stage win rates from closed deals: for each stage, the share of deals that reached
 * it and went on to win. Needs at least `minClosed` closed deals, otherwise priors are used.
 */
export function learnStageRates(closedDeals: (Deal & { stagesVisited?: Stage[] })[], minClosed = 20): Record<Stage, number> {
  if (closedDeals.length < minClosed) return { ...PRIOR_STAGE_WIN_RATE };
  const order: Stage[] = ["lead", "qualified", "discovery", "proposal", "negotiation"];
  const rates = { ...PRIOR_STAGE_WIN_RATE };
  for (let i = 0; i < order.length; i++) {
    // A deal "reached" a stage if it visited it, or (without history) if it reached a later one.
    const reached = closedDeals.filter((d) => d.stagesVisited ? d.stagesVisited.includes(order[i]) : true);
    if (reached.length >= 5) {
      const won = reached.filter((d) => d.stage === "won").length;
      // Blend with the prior so small samples don't swing wildly.
      rates[order[i]] = (won + PRIOR_STAGE_WIN_RATE[order[i]] * 5) / (reached.length + 5);
    }
  }
  return rates;
}

export function scoreDeal(deal: Deal, activities: Activity[], now: Date, stageRates = PRIOR_STAGE_WIN_RATE): Score {
  const base = stageRates[deal.stage];
  if (!OPEN_STAGES.includes(deal.stage)) {
    return { dealId: deal.id, probability: base, base, factors: [], risk: "low" };
  }
  const factors: Factor[] = [];
  const mine = activities.filter((a) => a.dealId === deal.id && a.kind !== "task").sort((a, b) => b.at.localeCompare(a.at));

  // 1. Recency of contact.
  const last = mine[0] ? (now.getTime() - Date.parse(mine[0].at)) / DAY : Infinity;
  if (last === Infinity) factors.push({ label: "No activity logged yet", effect: 0.7 });
  else if (last > 30) factors.push({ label: `No contact in ${Math.round(last)} days`, effect: 0.55 });
  else if (last > 14) factors.push({ label: `Last contact ${Math.round(last)} days ago`, effect: 0.8 });
  else if (last <= 7) factors.push({ label: "In touch this week", effect: 1.1 });

  // 2. Customer engagement: are they replying or reaching out?
  const inbound30 = mine.filter((a) => a.inbound && now.getTime() - Date.parse(a.at) <= 30 * DAY).length;
  if (inbound30 >= 2) factors.push({ label: `Customer reached out ${inbound30} times in 30 days`, effect: 1.2 });
  else if (mine.length >= 3 && inbound30 === 0) factors.push({ label: "Customer hasn't replied in 30 days", effect: 0.75 });

  // 3. Meetings held.
  const meetings = mine.filter((a) => a.kind === "meeting").length;
  if (meetings >= 2) factors.push({ label: `${meetings} meetings held`, effect: 1.15 });

  // 4. Stuck in stage.
  const inStage = (now.getTime() - Date.parse(deal.stageEnteredAt)) / DAY;
  const expected = EXPECTED_STAGE_DAYS[deal.stage];
  if (inStage > expected * 2) factors.push({ label: `Stuck in ${deal.stage} for ${Math.round(inStage)} days`, effect: 0.7 });
  else if (inStage > expected) factors.push({ label: `In ${deal.stage} longer than usual (${Math.round(inStage)} days)`, effect: 0.88 });

  // 5. Close date slipped.
  const toClose = (Date.parse(deal.closeDate) - now.getTime()) / DAY;
  if (toClose < 0) factors.push({ label: `Close date passed ${Math.round(-toClose)} days ago`, effect: 0.6 });

  // 6. Multi-threading: deals with one contact are fragile.
  if (deal.contactIds.length === 0) factors.push({ label: "No contacts linked", effect: 0.8 });
  else if (deal.contactIds.length === 1 && ["proposal", "negotiation"].includes(deal.stage)) {
    factors.push({ label: "Single-threaded late in the deal", effect: 0.85 });
  } else if (deal.contactIds.length >= 3) factors.push({ label: `${deal.contactIds.length} people involved`, effect: 1.1 });

  const raw = factors.reduce((p, f) => p * f.effect, base);
  const probability = Math.min(0.97, Math.max(0.01, raw));
  const drag = factors.filter((f) => f.effect < 1).reduce((p, f) => p * f.effect, 1);
  const risk = drag < 0.5 ? "high" : drag < 0.8 ? "medium" : "low";
  return { dealId: deal.id, probability: Math.round(probability * 1000) / 1000, base, factors, risk };
}

export interface NextAction {
  dealId: string;
  action: string;
  why: string;
  priority: 1 | 2 | 3; // 1 = today
  channel: "call" | "email" | "meeting" | "task" | "update";
}

/** Recommend the right step for each relationship, with the reasoning shown. */
export function nextBestAction(deal: Deal, score: Score, activities: Activity[], now: Date): NextAction {
  const has = (label: string) => score.factors.find((f) => f.label.startsWith(label));
  const openTasks = activities.filter((a) => a.dealId === deal.id && a.kind === "task" && !a.done);
  const overdue = openTasks.find((t) => t.dueAt && Date.parse(t.dueAt) < now.getTime());

  if (overdue) return { dealId: deal.id, action: `Finish overdue task: ${overdue.summary}`, why: "A promised follow-up is past due", priority: 1, channel: "task" };
  if (has("Close date passed")) return { dealId: deal.id, action: "Confirm a new close date with the buyer", why: has("Close date passed")!.label, priority: 1, channel: "call" };
  if (has("No contact in")) return { dealId: deal.id, action: "Call your main contact to re-engage", why: has("No contact in")!.label, priority: 1, channel: "call" };
  if (has("Single-threaded")) return { dealId: deal.id, action: "Ask your champion to introduce the decision-maker", why: "Only one contact on a late-stage deal", priority: 2, channel: "email" };
  if (has("No contacts linked")) return { dealId: deal.id, action: "Add the people involved in this deal", why: "Spectre can't track engagement without contacts", priority: 2, channel: "update" };
  if (has("Stuck in")) return { dealId: deal.id, action: `Book a meeting to agree what moves it past ${deal.stage}`, why: has("Stuck in")!.label, priority: 2, channel: "meeting" };
  if (has("Customer hasn't replied")) return { dealId: deal.id, action: "Send a short follow-up with one clear question", why: has("Customer hasn't replied")!.label, priority: 2, channel: "email" };
  if (deal.stage === "proposal") return { dealId: deal.id, action: "Walk the buyer through the proposal live", why: "Proposals reviewed together close more often", priority: 3, channel: "meeting" };
  if (deal.stage === "negotiation") return { dealId: deal.id, action: "Confirm sign-off steps and who signs", why: "Removes late surprises in procurement", priority: 2, channel: "call" };
  return { dealId: deal.id, action: "Share something useful: a case study or relevant insight", why: "Keeps momentum while the deal is healthy", priority: 3, channel: "email" };
}

export interface ForecastPeriod {
  period: string; // YYYY-MM
  committed: number; // won in period
  weighted: number; // sum of amount × probability for open deals closing in period
  bestCase: number; // committed + all open deals closing in period
  deals: number;
}

/** Revenue forecast by month: what's won, what's weighted by probability, and the best case. */
export function forecast(deals: Deal[], scores: Map<string, Score>, months: number, now: Date): ForecastPeriod[] {
  const periods: ForecastPeriod[] = [];
  for (let i = 0; i < months; i++) {
    const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + i, 1));
    periods.push({ period: d.toISOString().slice(0, 7), committed: 0, weighted: 0, bestCase: 0, deals: 0 });
  }
  const byPeriod = new Map(periods.map((p) => [p.period, p]));
  for (const deal of deals) {
    const p = byPeriod.get(deal.closeDate.slice(0, 7));
    if (!p) continue;
    if (deal.stage === "won") { p.committed += deal.amount; p.bestCase += deal.amount; p.deals++; }
    else if (OPEN_STAGES.includes(deal.stage)) {
      const prob = scores.get(deal.id)?.probability ?? PRIOR_STAGE_WIN_RATE[deal.stage];
      p.weighted += deal.amount * prob;
      p.bestCase += deal.amount;
      p.deals++;
    }
  }
  for (const p of periods) p.weighted = Math.round(p.weighted);
  return periods;
}

export interface RiskAlert {
  dealId: string;
  severity: "high" | "medium";
  message: string;
}

/** Risk alerts before deals slip. */
export function riskAlerts(deals: Deal[], scores: Map<string, Score>): RiskAlert[] {
  const alerts: RiskAlert[] = [];
  for (const d of deals) {
    const s = scores.get(d.id);
    if (!s || s.risk === "low" || !OPEN_STAGES.includes(d.stage)) continue;
    const worst = [...s.factors].sort((a, b) => a.effect - b.effect)[0];
    alerts.push({ dealId: d.id, severity: s.risk, message: `${d.name}: ${worst?.label ?? "at risk"}` });
  }
  return alerts.sort((a, b) => (a.severity === b.severity ? 0 : a.severity === "high" ? -1 : 1));
}
