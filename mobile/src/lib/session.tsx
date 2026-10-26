import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { storeDel, storeGet, storeSet } from "./storage";
import { authApi, publicApi, tenantApi } from "../api/services";
import { ApiError, clearTokens, getAccessToken, getRefreshToken, hydrateTokens, setTokens } from "../api/client";
import type { Role, SchoolBrand, Tenant, User } from "./types";
import { LOCKED_STATUSES } from "./types";
import { apiOrigin } from "./config";

const USER_KEY = "erp.user";
const CHILD_KEY = "erp.child";
const FEATURE_KEY = "erp.features";
const TENANT_KEY = "erp.tenant";
const BRAND_KEY = "erp.brand";
const BRAND_ORIGIN_KEY = "erp.brand.origin";

export function toBrand(t: { name: string; code: string; logoUrl?: string; primaryColor?: string; accentColor?: string; secondaryColor?: string; tagline?: string }): SchoolBrand {
  return {
    name: t.name,
    code: t.code,
    logoUrl: t.logoUrl,
    primaryColor: t.primaryColor,
    accentColor: t.accentColor,
    secondaryColor: t.secondaryColor,
    tagline: t.tagline,
  };
}

interface Session {
  user: User | null;
  tenant: Tenant | null;
  brand: SchoolBrand | null;
  loading: boolean;
  locked: boolean;
  childId: string | null;
  features: Record<string, boolean>;
  login: (username: string, password: string, expected?: Role) => Promise<User>;
  logout: () => Promise<void>;
  setChildId: (id: string | null) => void;
  refreshTenant: () => Promise<void>;
  ready: boolean;
}

const Ctx = createContext<Session | null>(null);
let memoryFeatures: Record<string, boolean> = {};

function parseJson<T>(raw: string | null): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [brand, setBrand] = useState<SchoolBrand | null>(null);
  const [loading, setLoading] = useState(true);
  const [forcedLocked, setForcedLocked] = useState(false);
  const [childId, setChildIdState] = useState<string | null>(null);
  const [features, setFeatures] = useState<Record<string, boolean>>({});
  const leaving = useRef(false);

  const persistUser = async (next: User | null) => {
    setUser(next);
    if (next) await storeSet(USER_KEY, JSON.stringify(next));
    else await storeDel(USER_KEY);
  };

  const rememberBrand = async (t: Tenant | SchoolBrand | null) => {
    if (!t?.name) return;
    const next = toBrand(t);
    setBrand(next);
    await storeSet(BRAND_KEY, JSON.stringify(next));
    await storeSet(BRAND_ORIGIN_KEY, apiOrigin() || "");
    if ("id" in t && t.id) await storeSet(TENANT_KEY, JSON.stringify(t));
  };

  const loadPublicBrand = async () => {
    const origin = apiOrigin();
    if (!origin) return;
    try {
      const data = await publicApi.school();
      const school = data?.school;
      if (school?.name && school?.code) {
        await rememberBrand(school);
      }
    } catch {
      /* dedicated-school branding is best-effort; login must not wait */
    }
  };

  useEffect(() => {
    void (async () => {
      await hydrateTokens();
      const origin = apiOrigin() || "";
      const [rawUser, rawChild, rawFeat, rawTenant, rawBrand, rawOrigin] = await Promise.all([
        storeGet(USER_KEY),
        storeGet(CHILD_KEY),
        storeGet(FEATURE_KEY),
        storeGet(TENANT_KEY),
        storeGet(BRAND_KEY),
        storeGet(BRAND_ORIGIN_KEY),
      ]);
      setChildIdState(rawChild);
      const cachedUser = parseJson<User>(rawUser);
      const cachedTenant = parseJson<Tenant>(rawTenant);
      const cachedBrand = parseJson<SchoolBrand>(rawBrand);
      const cachedFeat = parseJson<{ flags?: Record<string, boolean> }>(rawFeat);
      if (cachedFeat?.flags) {
        memoryFeatures = cachedFeat.flags;
        setFeatures(cachedFeat.flags);
      }
      const originOk = rawOrigin === origin;
      if (originOk && cachedBrand?.name) setBrand(cachedBrand);
      else if (originOk && cachedTenant?.name) setBrand(toBrand(cachedTenant));
      else {
        setBrand(null);
        if (!originOk) {
          await Promise.all([storeDel(BRAND_KEY), storeDel(TENANT_KEY), storeDel(BRAND_ORIGIN_KEY)]);
        }
      }
      if (originOk && cachedTenant) {
        setTenant(cachedTenant);
        setForcedLocked(LOCKED_STATUSES.includes(cachedTenant.status));
      }

      const token = getAccessToken();
      if (!token) {
        await persistUser(null);
        setTenant(null);
        setLoading(false);
        setReady(true);
        void loadPublicBrand();
        return;
      }

      if (cachedUser) {
        setUser(cachedUser);
        setLoading(false);
        setReady(true);
      }

      try {
        const me = await authApi.me();
        await persistUser(me);
        if (me.role !== "ERP_OWNER") {
          try {
            const t = await tenantApi.me();
            setTenant(t);
            setForcedLocked(LOCKED_STATUSES.includes(t.status));
            await rememberBrand(t);
          } catch (e) {
            if (e instanceof ApiError && e.errorCode === "SUBSCRIPTION_INACTIVE") setForcedLocked(true);
          }
        } else {
          setTenant(null);
          setForcedLocked(false);
          setBrand(null);
          await Promise.all([storeDel(BRAND_KEY), storeDel(TENANT_KEY)]);
        }
      } catch {
        await clearTokens();
        await persistUser(null);
        setTenant(null);
        setForcedLocked(false);
        void loadPublicBrand();
      } finally {
        setLoading(false);
        setReady(true);
      }
    })();
  }, []);

  const login = useCallback(async (username: string, password: string, expected?: Role) => {
    const payload = await authApi.login(username.trim(), password);
    await setTokens(payload.accessToken, payload.refreshToken);
    if (expected && payload.user.role !== expected) {
      const rt = payload.refreshToken;
      await clearTokens();
      await persistUser(null);
      setTenant(null);
      if (rt) void authApi.logout(rt).catch(() => undefined);
      throw new ApiError(403, {
        success: false,
        errorCode: "WRONG_PORTAL",
        message: `This portal is for a different role. Your account is ${payload.user.role.replaceAll("_", " ").toLowerCase()}.`,
      });
    }
    await persistUser(payload.user);
    if (payload.features && payload.user.tenantId) {
      memoryFeatures = payload.features;
      setFeatures(payload.features);
      await storeSet(
        FEATURE_KEY,
        JSON.stringify({ tenantId: payload.user.tenantId, flags: payload.features, at: Date.now() }),
      );
    }
    if (payload.user.role === "ERP_OWNER") {
      setTenant(null);
      setForcedLocked(false);
      setBrand(null);
      await Promise.all([storeDel(BRAND_KEY), storeDel(TENANT_KEY)]);
    } else {
      let t = payload.tenant || null;
      if (!t) {
        try {
          t = await tenantApi.me();
        } catch (e) {
          if (e instanceof ApiError && e.errorCode === "SUBSCRIPTION_INACTIVE") setForcedLocked(true);
        }
      }
      if (t) {
        setTenant(t);
        setForcedLocked(LOCKED_STATUSES.includes(t.status));
        await rememberBrand(t);
      }
    }
    setLoading(false);
    return payload.user;
  }, []);

  const logout = useCallback(async () => {
    if (leaving.current) return;
    leaving.current = true;
    const rt = getRefreshToken();
    await clearTokens();
    await persistUser(null);
    setTenant(null);
    setForcedLocked(false);
    setChildIdState(null);
    setFeatures({});
    memoryFeatures = {};
    await Promise.all([storeDel(CHILD_KEY), storeDel(FEATURE_KEY), storeDel(TENANT_KEY)]);
    leaving.current = false;
    if (rt) void authApi.logout(rt).catch(() => undefined);
    void loadPublicBrand();
  }, []);

  const refreshTenant = useCallback(async () => {
    if (!user || user.role === "ERP_OWNER") return;
    try {
      const t = await tenantApi.me();
      setTenant(t);
      setForcedLocked(LOCKED_STATUSES.includes(t.status));
      await rememberBrand(t);
    } catch {
      /* keep current */
    }
  }, [user]);

  const setChildId = useCallback((id: string | null) => {
    setChildIdState(id);
    if (id) void storeSet(CHILD_KEY, id);
    else void storeDel(CHILD_KEY);
  }, []);

  const locked = Boolean(
    user && user.role !== "ERP_OWNER" && (forcedLocked || (tenant && LOCKED_STATUSES.includes(tenant.status))),
  );

  const displayBrand = useMemo<SchoolBrand | null>(() => {
    if (user && user.role !== "ERP_OWNER" && tenant?.name) return toBrand(tenant);
    return brand;
  }, [user, tenant, brand]);

  const value = useMemo<Session>(
    () => ({
      user,
      tenant,
      brand: displayBrand,
      loading,
      locked,
      childId,
      features,
      login,
      logout,
      setChildId,
      refreshTenant,
      ready,
    }),
    [user, tenant, displayBrand, loading, locked, childId, features, login, logout, setChildId, refreshTenant, ready],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useSession outside provider");
  return ctx;
}
