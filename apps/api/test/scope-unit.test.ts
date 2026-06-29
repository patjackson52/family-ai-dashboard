// ADR 0029 — direct unit tests for the content scope-gate primitives. scope-gate.test
// drives these through routes; this pins the security-critical PARSING contract directly:
// grants are matched by EXACT string, never split(':'), because hub ids are free text and
// may contain ':' — a split would mis-attribute authority (privilege confusion). Also pins
// the empty-id guard and action/resource mismatch denials.
import { describe, it, expect } from "vitest";
import { scopeAllows, grantedHubIds } from "../src/auth/scope.ts";

describe("scopeAllows", () => {
  it("a global content:<action> grant covers any content resource for that action", () => {
    expect(scopeAllows(["content:read"], "content", "read")).toBe(true);
    expect(scopeAllows(["content:read"], "hub:anything", "read")).toBe(true);
  });

  it("denies a different action (content:read does not grant write)", () => {
    expect(scopeAllows(["content:read"], "content", "write")).toBe(false);
  });

  it("a resource-qualified grant covers only its exact resource + action", () => {
    expect(scopeAllows(["hub:A:read"], "hub:A", "read")).toBe(true);
    expect(scopeAllows(["hub:A:read"], "hub:B", "read")).toBe(false); // resource mismatch
    expect(scopeAllows(["hub:A:read"], "hub:A", "write")).toBe(false); // action mismatch
  });

  it("empty grants permit nothing", () => {
    expect(scopeAllows([], "hub:A", "read")).toBe(false);
  });

  it("SECURITY: a hub id containing ':' is matched by exact string, not split", () => {
    // resource "hub:a:b" (hub id "a:b") needs the exact grant "hub:a:b:read"
    expect(scopeAllows(["hub:a:b:read"], "hub:a:b", "read")).toBe(true);
    // and a grant for a DIFFERENT id that shares a prefix must not leak across
    expect(scopeAllows(["hub:a:read"], "hub:a:b", "read")).toBe(false);
  });
});

describe("grantedHubIds", () => {
  it("a global content:<action> grant → null (all hubs)", () => {
    expect(grantedHubIds(["content:read"], "read")).toBeNull();
    expect(grantedHubIds(["content:read", "hub:X:read"], "read")).toBeNull(); // global wins
  });

  it("explicit hub grants → exactly those ids", () => {
    expect(grantedHubIds(["hub:X:read", "hub:Y:read"], "read")).toEqual(["X", "Y"]);
  });

  it("SECURITY: a hub id containing ':' is extracted intact, not split", () => {
    expect(grantedHubIds(["hub:a:b:read"], "read")).toEqual(["a:b"]);
  });

  it("an empty-id grant (hub::read) is rejected (length guard)", () => {
    expect(grantedHubIds(["hub::read"], "read")).toEqual([]);
  });

  it("grants for the other action are excluded", () => {
    expect(grantedHubIds(["hub:X:write"], "read")).toEqual([]);
  });

  it("no grants → no authority (empty list, not null)", () => {
    expect(grantedHubIds([], "read")).toEqual([]);
  });
});
