import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { supabaseMigrations } from "../src/supabase-migration.js";

for (const { file, sql } of supabaseMigrations()) {
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, sql);
  console.log(`wrote ${file}`);
}
