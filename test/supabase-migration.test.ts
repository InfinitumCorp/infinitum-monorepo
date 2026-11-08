import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { SUPABASE_MIGRATION_FILE, supabaseMigrationSql } from "../src/supabase-migration.js";

describe("supabase migration", () => {
  it("matches src/schema.ts (run npm run db:gen if this fails)", () => {
    expect(readFileSync(SUPABASE_MIGRATION_FILE, "utf8").replace(/\r\n/g, "\n")).toBe(supabaseMigrationSql());
  });

  it("enables RLS and keeps the Data API roles out", () => {
    const sql = supabaseMigrationSql();
    expect(sql).toContain("enable row level security");
    expect(sql).toContain("revoke all on schema infinitum_revenue from anon, authenticated");
    expect(sql).not.toMatch(/create policy/i);
  });
});
