/**
 * HRVST Personal Silo.
 * Stores packets that the owner keeps rather than licenses.
 */
import type { HarvestPacket } from "./packet.ts";

export class PersonalSilo {
  private packets = new Map<string, HarvestPacket>();

  keep(packet: HarvestPacket) {
    this.packets.set(packet.id, packet);
  }

  list(owner: string): HarvestPacket[] {
    return [...this.packets.values()].filter((p) => p.owner === owner);
  }

  get(id: string): HarvestPacket | undefined {
    return this.packets.get(id);
  }
}
