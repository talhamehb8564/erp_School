import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { authApi, tenantApi } from "../api/services";
import { ApiError, clearTokens, getAccessToken, getRefreshToken, setTokens } from "../api/client";
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
  logout: () => Promise<void>;
  reload: () => Promise<void>;
  setChildId: (id: string | null) => void;
}

const Ctx = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  });
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [loading, setLoading] = useState(true);
  const [childId, setChildIdState] = useState<string | null>(() => localStorage.getItem(CHILD_KEY));

  const persist = (next: User | null) => {
    setUser(next);
    if (next) localStorage.setItem(USER_KEY, JSON.stringify(next));
    else localStorage.removeItem(USER_KEY);
  };

  const loadTenant = async (u: User) => {
    if (u.role === "ERP_OWNER") {
      setTenant(null);
      return;
    }
    try {
      const t = await tenantApi.me();
      setTenant(t);
    } catch {
      setTenant(null);
    }
  };

  const reload = useCallback(async () => {
    if (!getAccessToken()) {
      persist(null);
      setTenant(null);
      setLoading(false);
      return;
    }
    try {
      const me = await authApi.me();
      persist(me);
      await loadTenant(me);
    } catch {
      clearTokens();
      persist(null);
      setTenant(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const login = useCallback(async (username: string, password: string, expected?: Role) => {
    const payload = await authApi.login(username.trim(), password);
    if (expected && payload.user.role !== expected) {
      try {
        await authApi.logout(payload.refreshToken);
      } catch {
        /* ignore */
      }
      throw new ApiError(403, {
        success: false,
        errorCode: "WRONG_PORTAL",
        message: `This portal is for a different role. Your account is ${payload.user.role.replaceAll("_", " ").toLowerCase()}.`,
      });
    }
    setTokens(payload.accessToken, payload.refreshToken);
    persist(payload.user);
    await loadTenant(payload.user);
    return payload.user;
  }, []);

  const logout = useCallback(async () => {
    const rt = getRefreshToken();
    try {
      if (rt) await authApi.logout(rt);
    } catch {
      /* still clear locally */
    }
    clearTokens();
    persist(null);
    setTenant(null);
    localStorage.removeItem(CHILD_KEY);
    setChildIdState(null);
  }, []);

  const setChildId = (id: string | null) => {
    setChildIdState(id);
    if (id) localStorage.setItem(CHILD_KEY, id);
    else localStorage.removeItem(CHILD_KEY);
  };

  const locked = Boolean(
    user &&
      user.role !== "ERP_OWNER" &&
      tenant &&
      LOCKED_STATUSES.includes(tenant.status),
  );

  const value = useMemo<Session>(
    () => ({ user, tenant, loading, locked, childId, login, logout, reload, setChildId }),
    [user, tenant, loading, locked, childId, login, logout, reload],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useSession outside provider");
  return ctx;
}
