import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { storeDel, storeGet, storeSet } from "./storage";
import { authApi, tenantApi } from "../api/services";
import { ApiError, clearTokens, getAccessToken, getRefreshToken, hydrateTokens, setTokens } from "../api/client";
import type { Role, SchoolBrand, Tenant, User } from "./types";
import { LOCKED_STATUSES } from "./types";

const USER_KEY = "erp.user";
const CHILD_KEY = "erp.child";
const FEATURE_KEY = "erp.features";
const TENANT_KEY = "erp.tenant";
const BRAND_KEY = "erp.brand";

function toBrand(t: Tenant): SchoolBrand {
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

  const rememberBrand = async (t: Tenant | null) => {
    if (!t) return;
    const next = toBrand(t);
    setBrand(next);
    await storeSet(BRAND_KEY, JSON.stringify(next));
    await storeSet(TENANT_KEY, JSON.stringify(t));
  };

  useEffect(() => {
    void (async () => {
      await hydrateTokens();
      const [rawUser, rawChild, rawFeat, rawTenant, rawBrand] = await Promise.all([
        storeGet(USER_KEY),
        storeGet(CHILD_KEY),
        storeGet(FEATURE_KEY),
        storeGet(TENANT_KEY),
        storeGet(BRAND_KEY),
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
      if (cachedBrand) setBrand(cachedBrand);
      else if (cachedTenant) setBrand(toBrand(cachedTenant));
      if (cachedTenant) {
        setTenant(cachedTenant);
        setForcedLocked(LOCKED_STATUSES.includes(cachedTenant.status));
      }

      const token = getAccessToken();
      if (!token) {
        await persistUser(null);
        setLoading(false);
        setReady(true);
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
          if (cachedTenant && cachedTenant.id === me.tenantId) {
            /* keep cached tenant; refresh in background below */
          }
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
    } else if (payload.tenant) {
      setTenant(payload.tenant);
      setForcedLocked(LOCKED_STATUSES.includes(payload.tenant.status));
      await rememberBrand(payload.tenant);
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
  }, []);

  const setChildId = useCallback((id: string | null) => {
    setChildIdState(id);
    if (id) void storeSet(CHILD_KEY, id);
    else void storeDel(CHILD_KEY);
  }, []);

  const locked = Boolean(
    user && user.role !== "ERP_OWNER" && (forcedLocked || (tenant && LOCKED_STATUSES.includes(tenant.status))),
  );

  const value = useMemo<Session>(
    () => ({ user, tenant, brand, loading, locked, childId, features, login, logout, setChildId, ready }),
    [user, tenant, brand, loading, locked, childId, features, login, logout, setChildId, ready],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useSession outside provider");
  return ctx;
}


