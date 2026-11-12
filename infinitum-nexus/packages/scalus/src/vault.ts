/**
 * SCALVS Context Vault.
 * Append-only memory for agents. Production would back this with Firmament
 * or a private store with retention policies.
 */
export interface MemoryEntry {
  id: string;
  agentId: string;
  content: string;
  tags: string[];
  createdAt: string;
}

export class ContextVault {
  private entries: MemoryEntry[] = [];

  remember(agentId: string, content: string, tags: string[] = []): MemoryEntry {
    const entry: MemoryEntry = {
      id: `mem_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      agentId,
      content,
      tags,
      createdAt: new Date().toISOString(),
    };
    this.entries.push(entry);
    return entry;
  }

  recall(agentId: string, tag?: string): MemoryEntry[] {
    return this.entries.filter((e) => e.agentId === agentId && (!tag || e.tags.includes(tag)));
  }

  clear(agentId: string) {
    this.entries = this.entries.filter((e) => e.agentId !== agentId);
  }
}
