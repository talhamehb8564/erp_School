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
