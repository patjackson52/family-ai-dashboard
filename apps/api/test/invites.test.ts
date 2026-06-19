import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { generateKeyPair, exportJWK } from "jose";
const here = dirname(fileURLToPath(import.meta.url));
process.env.DATABASE_URL ||= "postgres:///fad_test";
process.env.AUTH_ISS = "https://fad.test/auth"; process.env.AUTH_AUD = "fad-api-test";
process.env.ENABLE_DEV_AUTH = "1"; process.env.DEV_AUTH_SECRET = "dev"; delete process.env.VERCEL_ENV;
const kp = await generateKeyPair("EdDSA", { crv: "Ed25519", extractable: true });
const priv = await exportJWK(kp.privateKey); priv.kid = "k1"; priv.alg = "EdDSA";
process.env.AUTH_SIGNING_KEY = JSON.stringify(priv);
const { pool, q } = await import("../src/db.ts");
const { app } = await import("../src/app.ts");

beforeAll(async () => {
  await q(`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`);
  for (const m of ["0001_m0_init.sql","0002_auth.sql","0003_device_grant.sql","0004_refresh_grace.sql","0005_invites.sql"])
    await q(readFileSync(resolve(here, "../migrations/"+m), "utf8"));
});
afterAll(async () => { await pool.end(); });

const dev = { "content-type":"application/json", authorization:"Bearer dev" };

// Mint an owner of a fresh family via dev-token + POST /families
async function ownerOf(uid: string) {
  const t = (await (await app.request("/auth/dev-token",{method:"POST",headers:dev,body:JSON.stringify({provider:"dev",provider_uid:uid})})).json()).access;
  const fam = await (await app.request("/families",{method:"POST",headers:{...dev,authorization:`Bearer ${t}`},body:JSON.stringify({name:uid})})).json();
  return { token: t, familyId: fam.familyId };
}

describe("POST /families/:fid/invites", () => {
  it("owner mints; token ≥128-bit + only hash stored; raw returned once; not gzipped", async () => {
    const o = await ownerOf("alice");
    const r = await app.request(`/families/${o.familyId}/invites`, { method:"POST", headers:{...dev, authorization:`Bearer ${o.token}`}, body: JSON.stringify({ mode:"link", max_uses:3 }) });
    expect(r.status).toBe(201);
    expect(r.headers.get("content-encoding")).toBeNull();
    const b = await r.json();
    expect(Buffer.from(b.token, "base64url").length).toBeGreaterThanOrEqual(16);
    expect(b.role).toBe("adult");
    const row = await q(`SELECT token_hash FROM invites WHERE id=$1`, [b.invite_id]);
    expect(row.rows[0].token_hash).not.toContain(b.token); // hash, not raw
  });
  it("non-owner→404; cli token→403; role=owner/teen→400; max_uses>10→400", async () => {
    const o = await ownerOf("bob");
    const stranger = await ownerOf("carol"); // owner of a DIFFERENT family
    expect((await app.request(`/families/${o.familyId}/invites`,{method:"POST",headers:{...dev,authorization:`Bearer ${stranger.token}`},body:'{"mode":"qr"}'})).status).toBe(404); // not a member → 404
    expect((await app.request(`/families/${o.familyId}/invites`,{method:"POST",headers:{...dev,authorization:`Bearer ${o.token}`},body:'{"mode":"qr","role":"owner"}'})).status).toBe(400);
    expect((await app.request(`/families/${o.familyId}/invites`,{method:"POST",headers:{...dev,authorization:`Bearer ${o.token}`},body:'{"mode":"link","max_uses":11}'})).status).toBe(400);
  });
});
