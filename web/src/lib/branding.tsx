import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { publicApi } from "../api/services";
import { applyBrand } from "./theme";

const CAMPUS_KEY = "atrium.publicCampus";

export interface PublicMedia {
  imageUrl: string;
  title?: string;
  caption?: string;
}

export interface PublicCampus {
  id: string;
  name: string;
  code: string;
  address?: string;
  city?: string;
  phone?: string;
  email?: string;
  facebookUrl?: string;
  instagramUrl?: string;
  youtubeUrl?: string;
  twitterUrl?: string;
  hero?: PublicMedia[];
}

export interface PublicSchool {
  id: string;
  code: string;
  name: string;
  logoUrl?: string;
  primaryColor?: string;
  accentColor?: string;
  secondaryColor?: string;
  tagline?: string;
  aboutText?: string;
  academicsText?: string;
  achievementsText?: string;
  email?: string;
  phone?: string;
  addressLine?: string;
  city?: string;
  website?: string;
  facebookUrl?: string;
  instagramUrl?: string;
  youtubeUrl?: string;
  twitterUrl?: string;
  linkedinUrl?: string;
  gallery?: PublicMedia[];
}

interface Branding {
  school: PublicSchool | null;
  campuses: PublicCampus[];
  campusId: string | null;
  campus: PublicCampus | null;
  loading: boolean;
  missing: boolean;
  error: string;
  setCampusId: (id: string) => void;
  reload: () => Promise<void>;
}

const Ctx = createContext<Branding | null>(null);

export function BrandingProvider({ children }: { children: ReactNode }) {
  const [school, setSchool] = useState<PublicSchool | null>(null);
  const [campuses, setCampuses] = useState<PublicCampus[]>([]);
  const [campusId, setCampusIdState] = useState<string | null>(() => sessionStorage.getItem(CAMPUS_KEY));
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState("");

  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await publicApi.school();
      const next = data.school;
      const list = data.campuses || [];
      setSchool(next);
      setCampuses(list);
      setMissing(!next);
      applyBrand(next?.primaryColor, next?.accentColor, next?.secondaryColor);
      if (next?.name) document.title = next.name;
      setCampusIdState((cur) => {
        const valid = cur && list.some((c) => c.id === cur) ? cur : list[0]?.id || null;
        if (valid) sessionStorage.setItem(CAMPUS_KEY, valid);
        else sessionStorage.removeItem(CAMPUS_KEY);
        return valid;
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Unable to load school";
      setError(msg);
      setMissing(/not found|no school/i.test(msg));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const setCampusId = useCallback((id: string) => {
    setCampusIdState(id);
    sessionStorage.setItem(CAMPUS_KEY, id);
  }, []);

  const campus = campuses.find((c) => c.id === campusId) || campuses[0] || null;

  const value = useMemo<Branding>(
    () => ({ school, campuses, campusId: campus?.id || campusId, campus, loading, missing, error, setCampusId, reload }),
    [school, campuses, campusId, campus, loading, missing, error, setCampusId, reload],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useBranding() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useBranding outside provider");
  return ctx;
}
