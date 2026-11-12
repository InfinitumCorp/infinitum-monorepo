/**
 * HRVST licensing flow.
 * A packet can be kept in Personal Silo or licensed.
 */
import type { HarvestPacket } from "./packet.ts";

export interface License {
  packetId: string;
  licensee: string;
  terms: string;
  issuedAt: string;
}

export function licensePacket(packet: HarvestPacket, licensee: string, terms: string): License {
  return {
    packetId: packet.id,
    licensee,
    terms,
    issuedAt: new Date().toISOString(),
  };
}

export function keepInSilo(packet: HarvestPacket): { packetId: string; silo: true } {
  return { packetId: packet.id, silo: true };
}
