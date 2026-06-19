import { randomBytes, createHash } from "node:crypto";
import { q } from "../db.ts";

export const hashInvite = (t: string) => createHash("sha256").update(t, "utf8").digest("hex");
export const genInviteToken = () => randomBytes(32).toString("base64url");
const id = () => "inv_" + randomBytes(9).toString("hex");

export async function createInvite(familyId: string, createdBy: string, mode: "qr" | "link", role: string, maxUses: number) {
  const token = genInviteToken();
  const inviteId = id();
  const ttl = mode === "qr" ? "15 minutes" : "72 hours";
  await q(
    `INSERT INTO invites(id, family_id, role, token_hash, mode, max_uses, created_by, expires_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7, now() + $8::interval)`,
    [inviteId, familyId, role, hashInvite(token), mode, maxUses, createdBy, ttl],
  );
  return { inviteId, token };
}
