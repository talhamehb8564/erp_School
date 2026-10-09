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
export function applyBrand(primary?: string | null, accent?: string | null) {
  const root = document.documentElement.style;
  if (primary) {
    root.setProperty("--brand", primary);
    root.setProperty("--navy", primary);
    root.setProperty("--forest", primary);
    root.setProperty("--accent", primary);
    root.setProperty("--sidebar-bg", `color-mix(in srgb, ${primary} 88%, #05070c)`);
  } else {
    root.removeProperty("--brand");
    root.removeProperty("--navy");
    root.removeProperty("--forest");
    root.removeProperty("--accent");
    root.removeProperty("--sidebar-bg");
  }
  if (accent) {
    root.setProperty("--brand-2", accent);
    root.setProperty("--brass", accent);
    root.setProperty("--brass-2", accent);
    root.setProperty("--accent-2", `color-mix(in srgb, ${accent} 22%, transparent)`);
  } else {
    root.removeProperty("--brand-2");
    root.removeProperty("--brass");
    root.removeProperty("--brass-2");
    root.removeProperty("--accent-2");
  }
}
