import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi, tenantApi } from "../api/services";
import { ApiError, AUTH_LOST_EVENT, LOCKED_EVENT, clearTokens, getAccessToken, getAuthEpoch, getRefreshToken, setTokens } from "../api/client";
import { clearFeatureCache, writeFeatureCache } from "./featureCache";
import { LOGIN_PORTALS } from "./routes";
import type { Role, Tenant, User } from "./types";
import { LOCKED_STATUSES } from "./types";

const USER_KEY = "atrium.user";
const CHILD_KEY = "atrium.child";

interface Session {
  user: User | null;
  tenant: Tenant | null;
  loading: boolean;
  locked: boolean;
  childId: string | null;
  login: (username: string, password: string, expected?: Role) => Promise<User>;
  logout: (opts?: { remote?: boolean }) => Promise<void>;
  reload: () => Promise<void>;
  setChildId: (id: string | null) => void;
}

const Ctx = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();
  const [user, setUser] = useState<User | null>(() => {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  });
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [loading, setLoading] = useState(() => Boolean(getAccessToken()) && !user);
  const [forcedLocked, setForcedLocked] = useState(false);
  const [childId, setChildIdState] = useState<string | null>(() => localStorage.getItem(CHILD_KEY));
  const leaving = useRef(false);
  const skipIdleMe = useRef(false);

  const persist = (next: User | null) => {
    setUser(next);
    if (next) localStorage.setItem(USER_KEY, JSON.stringify(next));
    else localStorage.removeItem(USER_KEY);
  };

  const loadTenant = async (u: User) => {
    if (u.role === "ERP_OWNER") {
      setTenant(null);
      setForcedLocked(false);
      return;
    }
    try {
      const t = await tenantApi.me();
      setTenant(t);
      setForcedLocked(LOCKED_STATUSES.includes(t.status));
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === "SUBSCRIPTION_INACTIVE") {
        setForcedLocked(true);
      } else {
        setTenant(null);
      }
    }
  };

  const reload = useCallback(async () => {
    if (!getAccessToken()) {
      persist(null);
      setTenant(null);
      setLoading(false);
      return;
    }
    const epoch = getAuthEpoch();
    try {
      let cachedRole: Role | undefined;
      try {
        const raw = localStorage.getItem(USER_KEY);
        cachedRole = raw ? (JSON.parse(raw) as User).role : undefined;
      } catch {
        cachedRole = undefined;
      }
      if (cachedRole && cachedRole !== "ERP_OWNER") {
        const [meResult, tenantResult] = await Promise.allSettled([authApi.me(), tenantApi.me()]);
        if (meResult.status !== "fulfilled") throw meResult.reason;
        if (getAuthEpoch() !== epoch) return;
        persist(meResult.value);
        if (tenantResult.status === "fulfilled") {
          setTenant(tenantResult.value);
          setForcedLocked(LOCKED_STATUSES.includes(tenantResult.value.status));
        } else {
          const e = tenantResult.reason;
          if (e instanceof ApiError && e.errorCode === "SUBSCRIPTION_INACTIVE") setForcedLocked(true);
          else setTenant(null);
        }
      } else {
        const me = await authApi.me();
        if (getAuthEpoch() !== epoch) return;
        persist(me);
        await loadTenant(me);
      }
    } catch (e) {
      if (getAuthEpoch() !== epoch) return;
      if (e instanceof ApiError && e.errorCode === "SUBSCRIPTION_INACTIVE") {
        setForcedLocked(true);
      } else {
        clearTokens();
        persist(null);
        setTenant(null);
        setForcedLocked(false);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!getAccessToken()) {
      setLoading(false);
      return;
    }
    if (user) {
      if (skipIdleMe.current) {
        skipIdleMe.current = false;
        setLoading(false);
        return;
      }
      const idle = (window as Window & { requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number }).requestIdleCallback;
      if (typeof idle === "function") {
        const id = idle(() => { void reload(); }, { timeout: 800 });
        const cancel = (window as Window & { cancelIdleCallback?: (id: number) => void }).cancelIdleCallback;
        return () => cancel?.(id);
      }
      const t = window.setTimeout(() => { void reload(); }, 200);
      return () => window.clearTimeout(t);
    }
    void reload();
  }, [reload]);

  useEffect(() => {
    const onLocked = () => setForcedLocked(true);
    window.addEventListener(LOCKED_EVENT, onLocked);
    return () => window.removeEventListener(LOCKED_EVENT, onLocked);
  }, []);

  useEffect(() => {
    const onLost = () => {
      persist(null);
      setTenant(null);
      setForcedLocked(false);
      localStorage.removeItem(CHILD_KEY);
      setChildIdState(null);
      navigate(LOGIN_PORTALS, { replace: true });
    };
    window.addEventListener(AUTH_LOST_EVENT, onLost);
    return () => window.removeEventListener(AUTH_LOST_EVENT, onLost);
  }, [navigate]);

  const applyTenant = (u: User, tenant?: Tenant | null) => {
    if (u.role === "ERP_OWNER") {
      setTenant(null);
      setForcedLocked(false);
      return true;
    }
    if (tenant) {
      setTenant(tenant);
      setForcedLocked(LOCKED_STATUSES.includes(tenant.status));
      return true;
    }
    return false;
  };

  const login = useCallback(async (username: string, password: string, expected?: Role) => {
    const payload = await authApi.login(username.trim(), password);
    setTokens(payload.accessToken, payload.refreshToken);
    if (expected && payload.user.role !== expected) {
      const rt = payload.refreshToken;
      clearTokens();
      persist(null);
      setTenant(null);
      if (rt) void authApi.logout(rt).catch(() => undefined);
      throw new ApiError(403, {
        success: false,
        errorCode: "WRONG_PORTAL",
        message: `This portal is for a different role. Your account is ${payload.user.role.replaceAll("_", " ").toLowerCase()}.`,
      });
    }
    persist(payload.user);
    writeFeatureCache(payload.user.tenantId, payload.features);
    skipIdleMe.current = true;
    if (!applyTenant(payload.user, payload.tenant)) {
      await loadTenant(payload.user);
    }
    setLoading(false);
    return payload.user;
  }, []);

  const logout = useCallback(async (opts?: { remote?: boolean }) => {
    if (leaving.current) return;
    leaving.current = true;
    const rt = opts?.remote !== false ? getRefreshToken() : null;
    clearTokens();
    persist(null);
    setTenant(null);
    setForcedLocked(false);
    localStorage.removeItem(CHILD_KEY);
    setChildIdState(null);
    clearFeatureCache();
    leaving.current = false;
    if (rt) void authApi.logout(rt).catch(() => undefined);
    navigate(LOGIN_PORTALS, { replace: true });
  }, [navigate]);

  const setChildId = useCallback((id: string | null) => {
    setChildIdState(id);
    if (id) localStorage.setItem(CHILD_KEY, id);
    else localStorage.removeItem(CHILD_KEY);
  }, []);

  const locked = Boolean(
    user &&
      user.role !== "ERP_OWNER" &&
      (forcedLocked || (tenant && LOCKED_STATUSES.includes(tenant.status))),
  );

  const value = useMemo<Session>(
    () => ({ user, tenant, loading, locked, childId, login, logout, reload, setChildId }),
    [user, tenant, loading, locked, childId, login, logout, reload, setChildId],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useSession outside provider");
  return ctx;
}
