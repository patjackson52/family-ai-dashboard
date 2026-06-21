// Retention sweep — run on a schedule (cron) to purge expired auth ephemera.
// Safe + idempotent. Usage: DATABASE_URL=... node scripts/sweep.mjs
import { sweep } from "../src/auth/sweep.ts";
import { pool } from "../src/db.ts";
const out = await sweep();
console.log("sweep:", JSON.stringify(out));
await pool.end();
process.exit(0);
