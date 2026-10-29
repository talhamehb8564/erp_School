import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { useColorScheme } from "react-native";
import { applyTenantColors, dark, light, type Palette } from "./theme";
import { useSession } from "./session";
import { bakedBrand } from "./config";
import { safeHex } from "./media";

interface Appearance {
  scheme: "light" | "dark";
  colors: Palette;
  toggle: () => void;
}

const Ctx = createContext<Appearance | null>(null);

export function AppearanceProvider({ children }: { children: ReactNode }) {
  const system = useColorScheme();
  const { tenant, brand } = useSession();
  const [override, setOverride] = useState<"light" | "dark" | null>(null);
  const scheme: "light" | "dark" = override || (system === "dark" ? "dark" : "light");
  const colors = useMemo(
    () =>
      applyTenantColors(
        scheme === "dark" ? dark : light,
        safeHex(tenant?.primaryColor || brand?.primaryColor || bakedBrand()?.primaryColor),
        safeHex(tenant?.accentColor || brand?.accentColor || bakedBrand()?.accentColor),
      ),
    [scheme, tenant?.primaryColor, tenant?.accentColor, brand?.primaryColor, brand?.accentColor],
  );
  const value = useMemo<Appearance>(
    () => ({
      scheme,
      colors,
      toggle: () => setOverride(scheme === "dark" ? "light" : "dark"),
    }),
    [scheme, colors],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAppearance() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAppearance");
  return ctx;
}
