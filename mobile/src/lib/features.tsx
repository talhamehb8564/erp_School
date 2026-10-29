import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { featureApi } from "../api/services";
import { useSession } from "./session";

interface Features {
  flags: Record<string, boolean>;
  loaded: boolean;
  on: (key?: string) => boolean;
}

const Ctx = createContext<Features | null>(null);

export function FeatureProvider({ children }: { children: ReactNode }) {
  const { user, features } = useSession();
  const [flags, setFlags] = useState<Record<string, boolean>>(features);
  const [loaded, setLoaded] = useState(Object.keys(features).length > 0 || !user || user.role === "ERP_OWNER");

  useEffect(() => {
    setFlags(features);
    if (Object.keys(features).length) setLoaded(true);
  }, [features]);

  useEffect(() => {
    if (!user || user.role === "ERP_OWNER") {
      setLoaded(true);
      return;
    }
    if (Object.keys(features).length) {
      setLoaded(true);
      return;
    }
    let cancelled = false;
    void featureApi
      .current()
      .then((next) => {
        if (cancelled) return;
        setFlags(next);
        setLoaded(true);
      })
      .catch(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [user?.id, user?.tenantId, features]);

  const value = useMemo<Features>(
    () => ({
      flags,
      loaded,
      on: (key) => {
        if (!key) return true;
        const k = key.toUpperCase().replace(/-/g, "_");
        if (!(k in flags)) return true;
        return flags[k] !== false;
      },
    }),
    [flags, loaded],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useFeatures() {
  const ctx = useContext(Ctx);
  if (!ctx) return { flags: {} as Record<string, boolean>, loaded: true, on: () => true };
  return ctx;
}
