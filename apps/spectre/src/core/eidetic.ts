/**
 * Spectre integration point for ei·det·ic.
 * Authorized, lawful use only. Calls the stub service.
 */
import { EideticService } from "@infinitum/eidetic";

const eidetic = new EideticService();

export async function matchIdentity(query: string) {
  return eidetic.match(query);
}

export async function recordConsent(subject: string, purpose: string) {
  return eidetic.recordConsent(subject, purpose);
}
