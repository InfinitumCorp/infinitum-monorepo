/**
 * Cumulus Cloud licensing.
 */
export interface LicenseGrant {
  objectKey: string;
  licensee: string;
  terms: string;
  issuedAt: string;
}

export function grant(objectKey: string, licensee: string, terms: string): LicenseGrant {
  return { objectKey, licensee, terms, issuedAt: new Date().toISOString() };
}
