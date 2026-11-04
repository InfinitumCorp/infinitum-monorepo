/**
 * ÆTHER distributed communications concept.
 * Stub for a mesh that carries secure voice, text and data.
 * Not a real mesh; documents the interface.
 */
export interface AetherNode {
  id: string;
  peers: string[];
}

export interface AetherMessage {
  from: string;
  to: string;
  payload: string;
  hop: number;
}

export class AetherMesh {
  private nodes = new Map<string, AetherNode>();

  join(id: string): AetherNode {
    const node = { id, peers: [] as string[] };
    this.nodes.set(id, node);
    return node;
  }

  connect(a: string, b: string) {
    const na = this.nodes.get(a);
    const nb = this.nodes.get(b);
    if (na && nb) {
      na.peers.push(b);
      nb.peers.push(a);
    }
  }

  list(): AetherNode[] {
    return [...this.nodes.values()];
  }
}
