export type Theme = "light" | "dark";
const KEY = "atrium.theme";

export function readTheme(): Theme {
  const v = localStorage.getItem(KEY);
  if (v === "dark" || v === "light") return v;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem(KEY, theme);
}

/** Push school primary/accent into CSS variables used by sidebar, buttons, and cards. */
export function applyBrand(primary?: string | null, accent?: string | null, secondary?: string | null) {
  const root = document.documentElement.style;
  if (primary) {
    root.setProperty("--brand", primary);
    root.setProperty("--navy", primary);
    root.setProperty("--forest", primary);
    root.setProperty("--accent", primary);
    root.setProperty("--btn", `color-mix(in srgb, ${primary} 58%, #2a0508)`);
    root.setProperty("--btn-hover", `color-mix(in srgb, ${primary} 38%, #1a0204)`);
    root.setProperty("--sidebar-bg", `color-mix(in srgb, ${primary} 88%, #05070c)`);
  }
  if (accent) {
    root.setProperty("--brand-2", accent);
    root.setProperty("--brass", accent);
    root.setProperty("--brass-2", accent);
    root.setProperty("--accent-2", `color-mix(in srgb, ${accent} 18%, transparent)`);
  }
  if (secondary) {
    root.setProperty("--brand-3", secondary);
  }
}
