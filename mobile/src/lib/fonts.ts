import { Platform } from "react-native";
import * as Font from "expo-font";
import { storeGet, storeSet } from "./storage";

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

const FONT_KEY = "erp.uiFont";
const loaded = new Set<string>();

const SYSTEM: Record<string, { ios: string; android: string }> = {
  "Times New Roman": { ios: "Times New Roman", android: "serif" },
  Georgia: { ios: "Georgia", android: "serif" },
  Arial: { ios: "Arial", android: "sans-serif" },
  Roboto: { ios: "System", android: "sans-serif" },
};

const GOOGLE_TTF: Record<string, string> = {
  Outfit: "https://github.com/google/fonts/raw/main/ofl/outfit/Outfit%5Bwght%5D.ttf",
  Inter: "https://github.com/google/fonts/raw/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf",
  "Open Sans": "https://github.com/google/fonts/raw/main/ofl/opensans/OpenSans%5Bwdth%2Cwght%5D.ttf",
  Lato: "https://github.com/google/fonts/raw/main/ofl/lato/Lato-Regular.ttf",
  Poppins: "https://github.com/google/fonts/raw/main/ofl/poppins/Poppins-Regular.ttf",
  Nunito: "https://github.com/google/fonts/raw/main/ofl/nunito/Nunito%5Bwght%5D.ttf",
  Montserrat: "https://github.com/google/fonts/raw/main/ofl/montserrat/Montserrat%5Bwght%5D.ttf",
  "Source Sans 3": "https://github.com/google/fonts/raw/main/ofl/sourcesans3/SourceSans3%5Bwght%5D.ttf",
  "Atkinson Hyperlegible": "https://github.com/google/fonts/raw/main/ofl/atkinsonhyperlegible/AtkinsonHyperlegible-Regular.ttf",
  Fraunces: "https://github.com/google/fonts/raw/main/ofl/fraunces/Fraunces%5BSOFT%2CWONK%2Copsz%2Cwght%5D.ttf",
  Literata: "https://github.com/google/fonts/raw/main/ofl/literata/Literata%5Bopsz%2Cwght%5D.ttf",
  Merriweather: "https://github.com/google/fonts/raw/main/ofl/merriweather/Merriweather%5Bopsz%2Cwdth%2Cwght%5D.ttf",
};

export function isPortalFont(value?: string | null): value is PortalFont {
  return Boolean(value && (PORTAL_FONTS as readonly string[]).includes(value));
}

export function systemFontFamily(name?: string | null) {
  if (!name) return undefined;
  if (Platform.OS === "web") return name;
  const mapped = SYSTEM[name];
  if (mapped) return Platform.OS === "ios" ? mapped.ios : mapped.android;
  if (loaded.has(name)) return name;
  return undefined;
}

export async function readStoredFont(): Promise<PortalFont | null> {
  const v = await storeGet(FONT_KEY);
  return isPortalFont(v) ? v : null;
}

export async function persistFont(name: PortalFont) {
  await storeSet(FONT_KEY, name);
}

const GOOGLE_CSS: Record<string, string> = {
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

function loadWebCss(name: string) {
  if (Platform.OS !== "web" || typeof document === "undefined") return;
  const spec = GOOGLE_CSS[name];
  if (!spec) return;
  const id = `portal-font-${name.replace(/\s+/g, "-")}`;
  if (document.getElementById(id)) return;
  const link = document.createElement("link");
  link.id = id;
  link.rel = "stylesheet";
  link.href = `https://fonts.googleapis.com/css2?family=${spec}&display=swap`;
  document.head.appendChild(link);
}

export async function ensureFontLoaded(name: string) {
  if (!name) return systemFontFamily(name);
  if (SYSTEM[name]) return systemFontFamily(name);
  if (Platform.OS === "web") {
    loadWebCss(name);
    loaded.add(name);
    return name;
  }
  if (loaded.has(name)) return name;
  const uri = GOOGLE_TTF[name];
  if (!uri) return systemFontFamily(name);
  try {
    await Font.loadAsync({ [name]: { uri } });
    loaded.add(name);
    return name;
  } catch {
    return systemFontFamily(name);
  }
}
