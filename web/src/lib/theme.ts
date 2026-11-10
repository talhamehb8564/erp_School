export type Theme = "light" | "dark";
const THEME_KEY = "atrium.theme";
const FONT_KEY = "atrium.uiFont";

export const PORTAL_FONTS = [
  "Outfit",
  "Inter",
  "Roboto",
  "Open Sans",
  "Lato",
  "Poppins",
  "Nunito",
  "Montserrat",
  "Source Sans 3",
  "Atkinson Hyperlegible",
  "Times New Roman",
  "Georgia",
  "Arial",
  "Fraunces",
  "Literata",
  "Merriweather",
] as const;

export type PortalFont = (typeof PORTAL_FONTS)[number];

const SYSTEM_FONTS = new Set<string>(["Times New Roman", "Georgia", "Arial"]);

const SERIF_FONTS = new Set<string>(["Times New Roman", "Georgia", "Fraunces", "Literata", "Merriweather"]);

const GOOGLE_SPEC: Record<string, string> = {
  Outfit: "Outfit:wght@380;460;560;650",
  Inter: "Inter:wght@400;500;600;700",
  Roboto: "Roboto:wght@400;500;700",
  "Open Sans": "Open+Sans:wght@400;600;700",
  Lato: "Lato:wght@400;700",
  Poppins: "Poppins:wght@400;500;600;700",
  Nunito: "Nunito:wght@400;600;700",
  Montserrat: "Montserrat:wght@400;500;600;700",
  "Source Sans 3": "Source+Sans+3:wght@400;600;700",
  "Atkinson Hyperlegible": "Atkinson+Hyperlegible:wght@400;700",
  Fraunces: "Fraunces:opsz,wght@9..144,500;9..144,650",
  Literata: "Literata:opsz,wght@7..72,500;7..72,650",
  Merriweather: "Merriweather:wght@400;700",
};

export function readTheme(): Theme {
  const v = localStorage.getItem(THEME_KEY);
  if (v === "dark" || v === "light") return v;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function applyTheme(theme: Theme) {
  const root = document.documentElement;
  root.dataset.theme = theme;
  root.style.colorScheme = theme;
  root.style.removeProperty("--bg");
  root.style.removeProperty("--bg-2");
  root.style.removeProperty("--ink");
  root.style.removeProperty("--muted");
  root.style.removeProperty("--card");
  localStorage.setItem(THEME_KEY, theme);
}

function luminance(hex: string) {
  const h = hex.replace("#", "");
  if (h.length !== 6) return 1;
  const r = parseInt(h.slice(0, 2), 16) / 255;
  const g = parseInt(h.slice(2, 4), 16) / 255;
  const b = parseInt(h.slice(4, 6), 16) / 255;
  const lin = (c: number) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

function inkOn(hex: string) {
  return luminance(hex) > 0.55 ? "#1a1012" : "#ffffff";
}

function isPortalFont(value: string): value is PortalFont {
  return (PORTAL_FONTS as readonly string[]).includes(value);
}

export function readStoredFont(): PortalFont | null {
  try {
    const v = localStorage.getItem(FONT_KEY);
    if (v && isPortalFont(v)) return v;
  } catch {
    /* private mode */
  }
  return null;
}

function persistFont(name: PortalFont) {
  try {
    localStorage.setItem(FONT_KEY, name);
  } catch {
    /* private mode */
  }
}

function loadWebFont(name: string) {
  if (SYSTEM_FONTS.has(name) || typeof document === "undefined") return;
  const spec = GOOGLE_SPEC[name];
  if (!spec) return;
  const id = `portal-font-${name.replace(/\s+/g, "-")}`;
  if (document.getElementById(id)) return;
  const link = document.createElement("link");
  link.id = id;
  link.rel = "stylesheet";
  link.href = `https://fonts.googleapis.com/css2?family=${spec}&display=swap`;
  document.head.appendChild(link);
}

function fallbackStack(name: string) {
  if (SERIF_FONTS.has(name)) return `"${name}", Georgia, "Times New Roman", serif`;
  return `"${name}", system-ui, "Segoe UI", sans-serif`;
}

/** Apply a school font. Missing/unknown values keep the last saved font instead of snapping back to Outfit. */
export function applyFont(family?: string | null) {
  if (typeof document === "undefined") return;
  const incoming = family?.trim() || "";
  const resolved: PortalFont = incoming && isPortalFont(incoming)
    ? incoming
    : (readStoredFont() || "Outfit");
  if (incoming && isPortalFont(incoming)) persistFont(incoming);
  loadWebFont(resolved);
  const stack = fallbackStack(resolved);
  const root = document.documentElement.style;
  root.setProperty("--font-ui", stack);
  root.setProperty("--font-serif", stack);
  document.documentElement.dataset.font = resolved;
}

/** Push school chrome colors. Surfaces stay in CSS so dark/light can switch without inline --bg/--ink winning. */
export function applyBrand(primary?: string | null, accent?: string | null, secondary?: string | null) {
  const root = document.documentElement.style;
  root.removeProperty("--bg");
  root.removeProperty("--bg-2");
  root.removeProperty("--ink");
  root.removeProperty("--muted");
  root.removeProperty("--card");
  if (primary) {
    root.setProperty("--brand", primary);
    root.setProperty("--navy", primary);
    root.setProperty("--forest", primary);
    root.setProperty("--btn", primary);
    root.setProperty("--btn-hover", `color-mix(in srgb, ${primary} 72%, #0c0c0c)`);
    root.setProperty("--btn-ink", inkOn(primary));
    root.setProperty("--sidebar-bg", `color-mix(in srgb, ${primary} 88%, #05070c)`);
  }
  if (accent) {
    root.setProperty("--brand-2", accent);
    root.setProperty("--brass", accent);
    root.setProperty("--brass-2", `color-mix(in srgb, ${accent} 78%, #0c0c0c)`);
    root.setProperty("--accent", accent);
    root.setProperty("--accent-2", `color-mix(in srgb, ${accent} 18%, transparent)`);
    root.setProperty("--bad", accent);
    root.setProperty("--danger", accent);
    root.setProperty("--danger-hover", `color-mix(in srgb, ${accent} 72%, #0c0c0c)`);
    root.setProperty("--danger-ink", inkOn(accent));
  } else if (primary) {
    root.setProperty("--bad", primary);
    root.setProperty("--danger", primary);
    root.setProperty("--danger-hover", `color-mix(in srgb, ${primary} 72%, #0c0c0c)`);
    root.setProperty("--danger-ink", inkOn(primary));
  }
  if (secondary) {
    root.setProperty("--brand-3", secondary);
  }
}

if (typeof document !== "undefined") {
  applyFont(readStoredFont());
}
