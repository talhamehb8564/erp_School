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

export function applyTenantColors(base: Palette, primary?: string, accent?: string): Palette {
  if (!primary && !accent) return base;
  return {
    ...base,
    brand: primary || base.brand,
    brand2: accent || primary || base.brand2,
  };
}
