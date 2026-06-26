// ADR 0036 — unit tests for the hardened image-URL / icon / accent validator.
// No DB. Mirrors the Kotlin accept/reject vectors (keep the three in lock-step).
import { describe, it, expect } from "vitest";
import {
  imageUrlError, iconError, accentHexError,
  validateHubMedia, validateCardMedia, validateBlockPayloadMedia,
  CURATED_ICONS,
} from "../src/media-validation.ts";

const ok = (u: string) => expect(imageUrlError(u), u).toBeNull();
const bad = (u: string) => expect(imageUrlError(u), u).not.toBeNull();

describe("imageUrlError — accepts", () => {
  it("plain allowlisted https URL", () => ok("https://upload.wikimedia.org/wikipedia/commons/a/a4/logo.png"));
  it("case-insensitive host (WHATWG lowercases)", () => ok("https://UPLOAD.WIKIMEDIA.ORG/x.png"));
  it("explicit default port 443 (normalized away)", () => ok("https://upload.wikimedia.org:443/x.png"));
  it("trailing-dot FQDN", () => ok("https://upload.wikimedia.org./x.jpg"));
  it("non-svg extensions", () => { ok("https://upload.wikimedia.org/x.jpeg"); ok("https://upload.wikimedia.org/x.webp"); });
});

describe("imageUrlError — rejects (evasion vectors)", () => {
  it("http (not https)", () => bad("http://upload.wikimedia.org/x.png"));
  it("data: URI", () => bad("data:image/png;base64,iVBORw0KGgo="));
  it("javascript:", () => bad("javascript:alert(1)"));
  it("blob:", () => bad("blob:https://upload.wikimedia.org/abc"));
  it("userinfo @ smuggling → real host is evil.com", () => bad("https://upload.wikimedia.org@evil.com/x.png"));
  it("path-as-host", () => bad("https://evil.com/upload.wikimedia.org/x.png"));
  it("suffix-evasion host", () => bad("https://upload.wikimedia.org.evil.com/x.png"));
  it("prefix-evasion host", () => bad("https://notupload.wikimedia.org/x.png"));
  it("sibling subdomain (exact-host, not registrable-domain)", () => bad("https://commons.wikimedia.org/x.png"));
  it("alternate explicit port", () => bad("https://upload.wikimedia.org:8443/x.png"));
  it("punycode/IDN homograph (cyrillic i) → xn-- host ≠ allowlist", () => bad("https://upload.wikіmedia.org/x.png"));
  it("SVG (XSS surface)", () => { bad("https://upload.wikimedia.org/logo.svg"); bad("https://upload.wikimedia.org/LOGO.SVG"); });
  it("whitespace / control smuggling", () => { bad("https://upload.wikimedia.org/a b.png"); bad("https://upload.wikimedia.org/a\tb.png"); });
  it("over-long URL", () => bad("https://upload.wikimedia.org/" + "a".repeat(2100) + ".png"));
  it("non-string", () => { bad(undefined as any); bad(123 as any); });
});

describe("iconError", () => {
  it("accepts every curated name", () => { for (const n of CURATED_ICONS) expect(iconError(n)).toBeNull(); });
  it("rejects unknown / arbitrary glyph", () => { expect(iconError("nuke")).not.toBeNull(); expect(iconError("medical_services")).not.toBeNull(); });
});

describe("accentHexError", () => {
  it("accepts #RRGGBB (either case)", () => { expect(accentHexError("#1c6e8c")).toBeNull(); expect(accentHexError("#1C6E8C")).toBeNull(); });
  it("rejects bad hex", () => { for (const h of ["1c6e8c", "#1c6e8", "#1c6e8cc", "#zzzzzz", "red", "#fff"]) expect(accentHexError(h), h).not.toBeNull(); });
});

describe("object validators", () => {
  it("valid Hub.media → no issues", () =>
    expect(validateHubMedia({ heroUrl: "https://upload.wikimedia.org/h.png", heroFit: "contain", icon: "school", accentColor: "#2C3E73" })).toEqual([]));
  it("Hub.media bad host + bad icon → two issues at correct paths", () => {
    const issues = validateHubMedia({ heroUrl: "https://evil.com/h.png", icon: "spaceship" });
    expect(issues.map((i) => i.path.join("."))).toEqual(["media.heroUrl", "media.icon"]);
  });
  it("Card.media thumbnail host enforced", () =>
    expect(validateCardMedia({ thumbnailUrl: "https://evil.com/t.png" }).length).toBe(1));
  it("block contact avatarUrl + accentColor enforced", () => {
    expect(validateBlockPayloadMedia("contact", { name: "x", avatarUrl: "https://upload.wikimedia.org/a.png", accentColor: "#aabbcc" })).toEqual([]);
    expect(validateBlockPayloadMedia("contact", { name: "x", avatarUrl: "https://evil.com/a.png" }).length).toBe(1);
  });
  it("block link thumbnailUrl enforced; non-image type ignores", () => {
    expect(validateBlockPayloadMedia("link", { url: "https://x", thumbnailUrl: "https://evil.com/t.png" }).length).toBe(1);
    expect(validateBlockPayloadMedia("checklist", { items: [] })).toEqual([]);
  });
  it("absent media → no issues", () => { expect(validateHubMedia(undefined)).toEqual([]); expect(validateCardMedia(null)).toEqual([]); });
});
