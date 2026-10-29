import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
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
  whatsappUrl?: string;
  hero?: PublicMedia[];
  principal?: PublicPrincipal;
  map?: PublicMap;
}

export interface PublicPrincipal {
  name?: string;
  title?: string;
  photoUrl?: string;
  photos?: PublicMedia[];
  message?: string;
  published?: boolean;
  isDefault?: boolean;
}

export interface PublicNotice {
  title?: string;
  body?: string;
  videoUrl?: string;
  posterUrl?: string;
  published?: boolean;
  isDefault?: boolean;
}

export interface PublicMap {
  lat?: number | string | null;
  lng?: number | string | null;
  place?: string;
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
  whatsappUrl?: string;
  admissions?: {
    enabled?: boolean;
    ctaLabel?: string;
    appointmentEnabled?: boolean;
    appointmentRequired?: boolean;
  };
  gallery?: PublicMedia[];
  principal?: PublicPrincipal;
  notice?: PublicNotice;
  map?: PublicMap;
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

function isPublicPath(pathname: string) {
  return pathname === "/" || pathname === "/apply" || pathname.startsWith("/portals") || pathname.startsWith("/login") || pathname === "/admin";
}

export function BrandingProvider({ children }: { children: ReactNode }) {
  const loc = useLocation();
  const publicSite = isPublicPath(loc.pathname);
  const [school, setSchool] = useState<PublicSchool | null>(null);
  const [campuses, setCampuses] = useState<PublicCampus[]>([]);
  const [campusId, setCampusIdState] = useState<string | null>(() => sessionStorage.getItem(CAMPUS_KEY));
  const [loading, setLoading] = useState(publicSite);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState("");
  const hasSchool = useRef(false);

  const reload = useCallback(async (opts?: { silent?: boolean }) => {
    if (!opts?.silent) setLoading(!hasSchool.current);
    setError("");
    try {
      const data = await publicApi.school();
      const next = data.school;
      const list = data.campuses || [];
      setSchool(next);
      hasSchool.current = Boolean(next);
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
    if (hasSchool.current) {
      setLoading(false);
      return;
    }
    void reload({ silent: !publicSite });
  }, [publicSite, reload]);

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
