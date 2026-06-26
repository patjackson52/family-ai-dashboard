import { describe, it, expect } from "vitest";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { requiredEnvVars, missingEnv } from "../scripts/env-check.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const srcDir = resolve(here, "../src");

describe("env-check preflight", () => {
  it("derives the required set from the source guards + structural reads", () => {
    const req = requiredEnvVars(srcDir);
    // the three AUTH_* throw-guards in tokens.ts (the firebase-500 cause)
    expect(req).toContain("AUTH_SIGNING_KEY");
    expect(req).toContain("AUTH_ISS");
    expect(req).toContain("AUTH_AUD");
    // structural reads that don't use the throw idiom
    expect(req).toContain("DATABASE_URL");
    expect(req).toContain("FIREBASE_PROJECT_ID");
  });

  it("reports exactly the unset/empty required vars (the prod-outage case)", () => {
    const required = ["AUTH_SIGNING_KEY", "AUTH_ISS", "AUTH_AUD", "DATABASE_URL", "FIREBASE_PROJECT_ID"];
    // prod's state on 2026-06-25: DATABASE_URL + FIREBASE_PROJECT_ID set, AUTH_* unset
    const env = { DATABASE_URL: "postgres://x", FIREBASE_PROJECT_ID: "dayfold-app", AUTH_ISS: "" };
    expect(missingEnv(required, env)).toEqual(["AUTH_SIGNING_KEY", "AUTH_ISS", "AUTH_AUD"]);
  });

  it("reports nothing missing when all are present", () => {
    const required = ["A", "B"];
    expect(missingEnv(required, { A: "1", B: "2" })).toEqual([]);
  });
});
