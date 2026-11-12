/**
 * Spectre Researcher — uses real web_search tool backend.
 */
import { createTools } from "../core/tools.ts";

export async function researchCompany(name: string) {
  const tools = createTools();
  const result = await tools.web_search.execute({ prompt: name });
  return result;
}
