import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
process.env.DATABASE_URL ||= "postgres:///fad_test";
const { pool, q } = await import("../src/db.ts");

beforeAll(async () => {
  await q(`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`);
  await q(readFileSync(resolve(here, "../migrations/0001_m0_init.sql"), "utf8"));
  await q(readFileSync(resolve(here, "../migrations/0002_auth.sql"), "utf8"));
});
afterAll(async () => { await pool.end(); });

describe("0002_auth schema", () => {
  it("user_identities unique (provider, provider_uid)", async () => {
    await q(`INSERT INTO users(id) VALUES ('u1')`);
    await q(`INSERT INTO user_identities(id,user_id,provider,provider_uid) VALUES ('i1','u1','dev','uid1')`);
    await expect(
      q(`INSERT INTO user_identities(id,user_id,provider,provider_uid) VALUES ('i2','u1','dev','uid1')`),
    ).rejects.toThrow();
  });

  it("membership role CHECK rejects junk; PK prevents dupes", async () => {
    await q(`INSERT INTO families(id,name) VALUES ('fam1','F')`);
    await expect(q(`INSERT INTO memberships(user_id,family_id,role) VALUES ('u1','fam1','king')`)).rejects.toThrow();
    await q(`INSERT INTO memberships(user_id,family_id,role) VALUES ('u1','fam1','owner')`);
    // PK test: same (user_id, family_id) — role is irrelevant, the PK is (user_id, family_id) only
    await expect(q(`INSERT INTO memberships(user_id,family_id,role) VALUES ('u1','fam1','owner')`)).rejects.toThrow();
  });

  it("membership status CHECK rejects invalid status values", async () => {
    // Prereqs: user u2, family fam2 (u1/fam1 already inserted above)
    await q(`INSERT INTO users(id) VALUES ('u2')`);
    await q(`INSERT INTO families(id,name) VALUES ('fam2','G')`);
    await expect(
      q(`INSERT INTO memberships(user_id,family_id,role,status) VALUES ('u2','fam2','adult','banned')`),
    ).rejects.toThrow();
  });

  it("refresh_tokens: valid insert persists; duplicate token_hash (PK) throws", async () => {
    // Prereq credential: kind='cli' requires family_scope NOT NULL
    await q(`INSERT INTO families(id,name) VALUES ('fam3','H')`);
    await q(
      `INSERT INTO credentials(id,family_scope,kind) VALUES ('cred1','fam3','cli')`,
    );

    // Valid refresh_token row
    await q(
      `INSERT INTO refresh_tokens(token_hash,credential_id,expires_at) VALUES ('hash_abc','cred1', now() + interval '1 hour')`,
    );
    const rows = await q(`SELECT token_hash FROM refresh_tokens WHERE token_hash='hash_abc'`);
    expect(rows.rows).toHaveLength(1);

    // Duplicate token_hash must throw (PK violation)
    await expect(
      q(`INSERT INTO refresh_tokens(token_hash,credential_id,expires_at) VALUES ('hash_abc','cred1', now() + interval '2 hours')`),
    ).rejects.toThrow();
  });
});
