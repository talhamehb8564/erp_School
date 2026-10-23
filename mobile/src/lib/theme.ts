export const light = {
  bg: "#F3EEE4",
  card: "#FFFcf7",
  ink: "#1B2430",
  muted: "#5C6B7A",
  line: "rgba(31,78,95,0.12)",
  brand: "#1F4E5F",
  brand2: "#2E7A8A",
  brass: "#C4A35A",
  ok: "#2F7D4A",
  warn: "#B7791F",
  bad: "#B42318",
  overlay: "rgba(15,20,25,0.45)",
};

export const dark = {
  bg: "#10161C",
  card: "#1A222C",
  ink: "#F4F1EA",
  muted: "#9AA8B5",
  line: "rgba(244,241,234,0.1)",
  brand: "#4CA6B8",
  brand2: "#2E7A8A",
  brass: "#D4B56A",
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
