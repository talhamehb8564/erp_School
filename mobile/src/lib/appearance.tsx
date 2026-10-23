import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { useColorScheme } from "react-native";
import { applyTenantColors, dark, light, type Palette } from "./theme";
import { useSession } from "./session";

interface Appearance {
  scheme: "light" | "dark";
  colors: Palette;
  toggle: () => void;
}

const Ctx = createContext<Appearance | null>(null);

export function AppearanceProvider({ children }: { children: ReactNode }) {
  const system = useColorScheme();
  const { tenant } = useSession();
  const [override, setOverride] = useState<"light" | "dark" | null>(null);
  const scheme: "light" | "dark" = override || (system === "dark" ? "dark" : "light");
  const colors = useMemo(
    () => applyTenantColors(scheme === "dark" ? dark : light, tenant?.primaryColor, tenant?.accentColor),
    [scheme, tenant?.primaryColor, tenant?.accentColor],
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
