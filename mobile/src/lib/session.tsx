import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { storeDel, storeGet, storeSet } from "./storage";
import { authApi, tenantApi } from "../api/services";
import { ApiError, clearTokens, getAccessToken, getRefreshToken, hydrateTokens, setTokens } from "../api/client";
import type { Role, Tenant, User } from "./types";
import { LOCKED_STATUSES } from "./types";

const USER_KEY = "erp.user";
const CHILD_KEY = "erp.child";
const FEATURE_KEY = "erp.features";

interface Session {
  user: User | null;
  tenant: Tenant | null;
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

export function SessionProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [loading, setLoading] = useState(true);
  const [forcedLocked, setForcedLocked] = useState(false);
  const [childId, setChildIdState] = useState<string | null>(null);
  const [features, setFeatures] = useState<Record<string, boolean>>({});
  const leaving = useRef(false);

  const persist = async (next: User | null) => {
    setUser(next);
    if (next) await storeSet(USER_KEY, JSON.stringify(next));
    else await storeDel(USER_KEY);
  };

  useEffect(() => {
    void (async () => {
      await hydrateTokens();
      const [rawUser, rawChild, rawFeat] = await Promise.all([
        storeGet(USER_KEY),
        storeGet(CHILD_KEY),
        storeGet(FEATURE_KEY),
      ]);
      setChildIdState(rawChild);
      if (rawFeat) {
        try {
          const parsed = JSON.parse(rawFeat) as { flags?: Record<string, boolean> };
          if (parsed.flags) {
            memoryFeatures = parsed.flags;
            setFeatures(parsed.flags);
          }
        } catch {
          /* ignore */
        }
      }
      if (rawUser) {
        try {
          setUser(JSON.parse(rawUser) as User);
        } catch {
          /* ignore */
        }
      }
      if (!getAccessToken()) {
        setLoading(false);
        setReady(true);
        return;
      }
      try {
        const me = await authApi.me();
        await persist(me);
        if (me.role !== "ERP_OWNER") {
          try {
            const t = await tenantApi.me();
            setTenant(t);
            setForcedLocked(LOCKED_STATUSES.includes(t.status));
          } catch (e) {
            if (e instanceof ApiError && e.errorCode === "SUBSCRIPTION_INACTIVE") setForcedLocked(true);
          }
        }
      } catch {
        await clearTokens();
        await persist(null);
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
      await persist(null);
      setTenant(null);
      if (rt) void authApi.logout(rt).catch(() => undefined);
      throw new ApiError(403, {
        success: false,
        errorCode: "WRONG_PORTAL",
        message: `This portal is for a different role. Your account is ${payload.user.role.replaceAll("_", " ").toLowerCase()}.`,
      });
    }
    await persist(payload.user);
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
    } else if (payload.tenant) {
      setTenant(payload.tenant);
      setForcedLocked(LOCKED_STATUSES.includes(payload.tenant.status));
    }
    setLoading(false);
    return payload.user;
  }, []);

  const logout = useCallback(async () => {
    if (leaving.current) return;
    leaving.current = true;
    const rt = getRefreshToken();
    await clearTokens();
    await persist(null);
    setTenant(null);
    setForcedLocked(false);
    setChildIdState(null);
    setFeatures({});
    memoryFeatures = {};
    await Promise.all([storeDel(CHILD_KEY), storeDel(FEATURE_KEY)]);
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
    () => ({ user, tenant, loading, locked, childId, features, login, logout, setChildId, ready }),
    [user, tenant, loading, locked, childId, features, login, logout, setChildId, ready],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useSession outside provider");
  return ctx;
}
