import { describe, it, expect } from "vitest";
import { blockPayloadIssues } from "../src/content-validation.ts";

// ADR 0035 Option C: the generated BlockSchema.payload is z.any() (codegen stubbed
// the per-type oneOf $refs), so the server validates block payloads as anything.
// blockPayloadIssues closes that — tolerant of both schema + client field names.
const ok = (b: any) => expect(blockPayloadIssues(b)).toEqual([]);
const bad = (b: any) => expect(blockPayloadIssues(b).length).toBeGreaterThan(0);

describe("block payload validation (ADR 0035)", () => {
  it("a payload present must carry its type's core field", () => {
    ok({ type: "checklist", payload: { items: [{ text: "Submit FAFSA" }] } });
    bad({ type: "checklist", payload: { items: [] } });
    ok({ type: "contact", payload: { name: "Admissions" } });
    bad({ type: "contact", payload: { phone: "888" } });   // no name
    ok({ type: "link", payload: { url: "https://x" } });
    bad({ type: "link", payload: { label: "portal" } });   // no url
  });

  it("is tolerant of BOTH schema and client field names (no side picked yet)", () => {
    ok({ type: "document", payload: { ref: "url://x" } });      // schema name
    ok({ type: "document", payload: { docRef: "url://x" } });   // client name
    ok({ type: "budget", payload: { items: [{ label: "Tuition", amount: 100 }] } }); // schema
    ok({ type: "budget", payload: { total: 1000, spent: 250 } });                    // client
  });

  it("a block with no payload is fine (body_md / placeholder)", () => {
    ok({ type: "contact", body_md: "**Admissions** 888" });
    ok({ type: "checklist" });
    ok({ type: "text", body_md: "notes" });
  });

  it("a non-object payload is rejected, and text/markdown payloads are ignored", () => {
    bad({ type: "contact", payload: "oops" });
    bad({ type: "checklist", payload: [1, 2, 3] });
    ok({ type: "markdown", payload: { anything: true } });  // text/markdown render body_md
  });
});
