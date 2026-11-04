/** +SPCTR data model. Every record belongs to a workspace (a person's or an organization's CID). */

export type Market = "commercial" | "government" | "partnership" | "investor";

export interface Company {
  id: string;
  workspace: string;
  name: string;
  domain?: string;
  kind: "company" | "agency" | "partner" | "investor";
  industry?: string;
  headcount?: number;
  /** Facts gathered about the account (by people or the Researcher agent), each with its source. */
  facts: Fact[];
  createdAt: string;
  updatedAt: string;
}

export interface Fact {
  id: string;
  field: string; // e.g. "leadership", "funding", "contract_award", "news"
  value: string;
  /** Where the fact came from: a URL, "user", an agent id, a document. Recorded on Firmament. */
  source: string;
  observedAt: string;
}

export interface Contact {
  id: string;
  workspace: string;
  companyId?: string;
  name: string;
  title?: string;
  email?: string;
  phone?: string;
  /** Role in the buying decision. */
  influence?: "decision-maker" | "champion" | "influencer" | "blocker" | "user";
  /** Contacts who opted out are kept only as a suppression record. */
  optedOut?: boolean;
  createdAt: string;
  updatedAt: string;
}

export const STAGES = ["lead", "qualified", "discovery", "proposal", "negotiation", "won", "lost"] as const;
export type Stage = (typeof STAGES)[number];
export const OPEN_STAGES: Stage[] = ["lead", "qualified", "discovery", "proposal", "negotiation"];

export interface Deal {
  id: string;
  workspace: string;
  companyId: string;
  name: string;
  market: Market;
  stage: Stage;
  amount: number;
  currency: string;
  /** Expected close date (ISO date). */
  closeDate: string;
  ownerCid: string;
  contactIds: string[];
  stageEnteredAt: string;
  createdAt: string;
  updatedAt: string;
  lostReason?: string;
}

export type ActivityKind = "email" | "call" | "meeting" | "note" | "task";

export interface Activity {
  id: string;
  workspace: string;
  kind: ActivityKind;
  at: string;
  summary: string;
  companyId?: string;
  dealId?: string;
  contactIds: string[];
  /** Direction for emails/calls: did the customer reach out or reply? */
  inbound?: boolean;
  /** For tasks: due date and whether done. */
  dueAt?: string;
  done?: boolean;
  actorCid: string;
}

/** "Who knows whom": an edge in the relationship map. Strength 1 (met once) to 5 (close). */
export interface Relationship {
  id: string;
  workspace: string;
  from: string; // contact id, or "cid:..." for a team member
  to: string;
  strength: 1 | 2 | 3 | 4 | 5;
  note?: string;
}
