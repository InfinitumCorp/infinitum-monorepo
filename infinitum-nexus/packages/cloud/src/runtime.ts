/**
 * Cumulus Cloud agent runtime stub.
 * Documents the interface for running agents on Cloud.
 */
export interface RuntimeTask {
  agentId: string;
  prompt: string;
  status: "queued" | "running" | "done";
}

export class AgentRuntime {
  private tasks: RuntimeTask[] = [];

  enqueue(agentId: string, prompt: string): RuntimeTask {
    const t: RuntimeTask = { agentId, prompt, status: "queued" };
    this.tasks.push(t);
    return t;
  }

  list(): RuntimeTask[] {
    return [...this.tasks];
  }
}
