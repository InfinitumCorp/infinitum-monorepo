import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { SUPABASE_MIGRATION_FILE, supabaseMigrationSql } from "../src/supabase-migration.js";

mkdirSync(dirname(SUPABASE_MIGRATION_FILE), { recursive: true });
writeFileSync(SUPABASE_MIGRATION_FILE, supabaseMigrationSql());
console.log(`wrote ${SUPABASE_MIGRATION_FILE}`);
