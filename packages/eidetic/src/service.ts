/**
 * ei·det·ic identity-intelligence scaffold.
 * Authorized, lawful use only. No biometrics in this scaffold.
 */
export interface IdentityMatch {
  subject: string;
  confidence: number;
  source: string;
}

export class EideticService {
  async match(_query: string): Promise<IdentityMatch[]> {
    // Scaffold: returns empty. Production would query authorized sources.
    return [];
  }

  async recordConsent(subject: string, purpose: string): Promise<{ ok: true }> {
    return { ok: true };
  }
}
