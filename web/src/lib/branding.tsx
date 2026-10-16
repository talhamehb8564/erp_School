import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { publicApi } from "../api/services";
import { applyBrand } from "./theme";

const CAMPUS_KEY = "atrium.publicCampus";
const BRAND_KEY = "atrium.publicBrand";

function readBrandCache(): { school: PublicSchool; campuses: PublicCampus[] } | null {
  try {
    const raw = sessionStorage.getItem(BRAND_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { school?: PublicSchool; campuses?: PublicCampus[] };
    if (parsed?.school?.id) return { school: parsed.school, campuses: parsed.campuses || [] };
  } catch {
    /* ignore */
  }
  return null;
}

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
  return pathname === "/" || pathname.startsWith("/portals") || pathname.startsWith("/login") || pathname === "/admin";
}

export function BrandingProvider({ children }: { children: ReactNode }) {
  const loc = useLocation();
  const publicSite = isPublicPath(loc.pathname);
  const cached = useMemo(() => readBrandCache(), []);
  const [school, setSchool] = useState<PublicSchool | null>(cached?.school || null);
  const [campuses, setCampuses] = useState<PublicCampus[]>(cached?.campuses || []);
  const [campusId, setCampusIdState] = useState<string | null>(() => sessionStorage.getItem(CAMPUS_KEY));
  const [loading, setLoading] = useState(publicSite && !cached?.school);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState("");
  const hasSchool = useRef(Boolean(cached?.school));

  useEffect(() => {
    if (!cached?.school) return;
    applyBrand(cached.school.primaryColor, cached.school.accentColor, cached.school.secondaryColor);
    if (cached.school.name) document.title = cached.school.name;
  }, [cached]);

  const reload = useCallback(async () => {
    setLoading(!hasSchool.current);
    setError("");
    try {
      const data = await publicApi.school();
      const next = data.school;
      const list = data.campuses || [];
      setSchool(next);
      hasSchool.current = Boolean(next);
      setCampuses(list);
      setMissing(!next);
      try {
        if (next) sessionStorage.setItem(BRAND_KEY, JSON.stringify({ school: next, campuses: list }));
        else sessionStorage.removeItem(BRAND_KEY);
      } catch {
        /* quota */
      }
      applyBrand(next?.primaryColor, next?.accentColor, next?.secondaryColor);
      if (next?.name) document.title = next.name;
      setCampusIdState((cur) => {
        const valid = cur && list.some((c) => c.id === cur) ? cur : list[0]?.id || null;
        if (valid) sessionStorage.setItem(CAMPUS_KEY, valid);
        else sessionStorage.removeItem(CAMPUS_KEY);
        return valid;
      });
    } catch (e) {
      if (hasSchool.current) return;
      const msg = e instanceof Error ? e.message : "Unable to load school";
      setError(msg);
      setMissing(/not found|no school/i.test(msg));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!publicSite) {
      setLoading(false);
      return;
    }
    void reload();
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
