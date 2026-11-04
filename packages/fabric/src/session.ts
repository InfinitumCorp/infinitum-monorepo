/**
 * FABRIC session scaffold.
 * Tracks an encrypted conversation between two parties.
 */
import { createMessage, type FabricMessage } from "./message.ts";

export class FabricSession {
  private messages: FabricMessage[] = [];
  constructor(public a: string, public b: string, public key: string) {}

  send(from: string, to: string, plaintext: string): FabricMessage {
    const m = createMessage(from, to, plaintext, this.key);
    this.messages.push(m);
    return m;
  }

  history(): FabricMessage[] {
    return [...this.messages];
  }
}
