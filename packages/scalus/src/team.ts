/**
 * SCALVS multi-agent team.
 * Runs agents in sequence and collects results.
 */
import { runAgent, type Agent, type RunResult } from "./agent.ts";

export class Team {
  constructor(public agents: Agent[]) {}

  async run(prompt: string): Promise<RunResult[]> {
    const results: RunResult[] = [];
    for (const agent of this.agents) {
      results.push(await runAgent(agent, prompt));
    }
    return results;
  }
}
