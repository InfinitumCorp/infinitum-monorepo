/**
 * SCALVS default tools.
 * Agents can import these or register their own.
 */
import type { Tool } from "./agent.ts";

export const echoTool: Tool = {
  name: "echo",
  description: "Echo the prompt",
  async execute(input) {
    return { echoed: input.prompt };
  },
};

export const searchTool: Tool = {
  name: "search",
  description: "Search the web via DuckDuckGo HTML",
  async execute(input) {
    const q = String(input.prompt ?? "").trim();
    if (!q) return { error: "empty query" };
    try {
      const url = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(q)}`;
      const res = await fetch(url, { headers: { "user-agent": "SCALVS/0.1" } });
      const html = await res.text();
      const titles = [...html.matchAll(/<a[^>]*class="result__a"[^>]*>([^<]+)<\/a>/gi)].map((m) => m[1].trim()).slice(0, 5);
      return { titles, query: q };
    } catch (err) {
      return { error: String(err), query: q };
    }
  },
};

export const noteTool: Tool = {
  name: "note",
  description: "Store a note",
  async execute(input) {
    return { stored: true, note: String(input.prompt ?? "") };
  },
};

export const defaultTools: Tool[] = [echoTool, searchTool, noteTool];
