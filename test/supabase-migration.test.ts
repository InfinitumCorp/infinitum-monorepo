import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { supabaseMigrations } from "../src/supabase-migration.js";

describe("supabase migrations", () => {
  for (const { file, sql } of supabaseMigrations()) {
    it(`${file} matches src/schema.ts (run npm run db:gen if this fails)`, () => {
      expect(readFileSync(file, "utf8").replace(/\r\n/g, "\n")).toBe(sql);
    });

    it(`${file} enables RLS and keeps the Data API roles out`, () => {
      expect(sql).toContain("enable row level security");
      expect(sql).toContain("revoke all on schema infinitum_revenue from anon, authenticated");
      expect(sql).not.toMatch(/create policy/i);
    });
  }
});
