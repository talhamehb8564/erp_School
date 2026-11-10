export const light = {
  bg: "#F4F7FB",
  card: "#FFFFFF",
  ink: "#0B1C2C",
  muted: "#5A6B7D",
  line: "rgba(27,54,93,0.12)",
  brand: "#1B365D",
  brand2: "#2C5282",
  brass: "#16325A",
  ok: "#2F7D4A",
  warn: "#B7791F",
  bad: "#B42318",
  overlay: "rgba(11,28,44,0.45)",
};

export const dark = {
  bg: "#0B1220",
  card: "#152033",
  ink: "#F4F7FB",
  muted: "#9AA8B5",
  line: "rgba(244,247,251,0.1)",
  brand: "#8FB3D9",
  brand2: "#5B87B5",
  brass: "#8FB3D9",
  ok: "#5BD08A",
  warn: "#E2B657",
  bad: "#F97066",
  overlay: "rgba(0,0,0,0.55)",
};

export type Palette = typeof light;

export function inkOn(hex: string) {
  const h = hex.replace("#", "");
  if (h.length !== 6) return "#ffffff";
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  const y = (r * 299 + g * 587 + b * 114) / 1000;
  return y > 160 ? "#1a1012" : "#ffffff";
}

function hexLum(hex: string) {
  const h = hex.replace("#", "");
  if (h.length !== 6) return 1;
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return (r * 299 + g * 587 + b * 114) / 1000 / 255;
}

export function applyTenantColors(base: Palette, primary?: string, accent?: string, secondary?: string): Palette {
  if (!primary && !accent && !secondary) return base;
  const darkUi = hexLum(base.bg) < 0.35;
  let bg = base.bg;
  let card = base.card;
  if (secondary) {
    bg = darkUi ? mixHex(secondary, base.bg, 0.16) : hexLum(secondary) > 0.72 ? secondary : mixHex(secondary, base.bg, 0.2);
    card = darkUi ? mixHex(secondary, base.card, 0.1) : base.card;
  }
  const brand = primary || base.brand;
  const accentColor = accent || primary || base.brand2;
  const readable = (hex: string) => (darkUi && hexLum(hex) < 0.42 ? mixHex(hex, "#F4F7FB", 0.42) : hex);
  return {
    ...base,
    bg,
    card,
    ink: base.ink,
    muted: base.muted,
    brand: readable(brand),
    brand2: readable(accentColor),
    brass: readable(accentColor),
    bad: readable(accentColor),
  };
}

function mixHex(hex: string, fallback: string, amount: number) {
  const h = hex.replace("#", "");
  if (h.length !== 6) return fallback;
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  const fr = parseInt(fallback.replace("#", "").slice(0, 2), 16) || 255;
  const fg = parseInt(fallback.replace("#", "").slice(2, 4), 16) || 255;
  const fb = parseInt(fallback.replace("#", "").slice(4, 6), 16) || 255;
  const n = (c: number, f: number) => Math.round(c * amount + f * (1 - amount));
  const to = (c: number) => c.toString(16).padStart(2, "0");
  return `#${to(n(r, fr))}${to(n(g, fg))}${to(n(b, fb))}`;
}
