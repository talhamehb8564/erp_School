import type { ApiErrorBody } from "../lib/types";
import { apiUrl } from "../lib/config";
import { storeDel, storeGet, storeSet } from "../lib/storage";

const ACCESS = "erp.access";
const REFRESH = "erp.refresh";

export class ApiError extends Error {
  status: number;
  errorCode?: string;
  fields?: { field: string; message: string }[];

  constructor(status: number, body?: ApiErrorBody | string) {
    const parsed = typeof body === "string" ? undefined : body;
    super(typeof body === "string" ? body : parsed?.message || "Request failed");
    this.status = status;
    this.errorCode = parsed?.errorCode;
    this.fields = parsed?.errors;
  }
}

let memoryAccess = "";
let memoryRefresh = "";
let authEpoch = 0;
const getInflight = new Map<string, Promise<unknown>>();
const getTtlCache = new Map<string, { exp: number; data: unknown }>();
const GET_TTL_MS = 45_000;
const GET_TIMEOUT_MS = 12_000;
const REQUEST_TIMEOUT_MS = 45_000;

const CACHEABLE_GET =
  /^\/api\/v1\/(auth\/me|classes|subjects|sections|campuses|tenants\/me|features|settings|school-settings|public\/school|notifications(\/unread-count)?|dashboard\/(school|platform)|portals\/(teacher|parent|student|account|principal)|announcements|homework|exams\/sessions|online-exams(\/mine)?|fees\/(me\/challans|children\/challans|proofs\/pending)|reports\/progress\/(me|teacher|children)|parents\/me\/children|teacher\/timetable(\/today)?|timetable\/me|attendance\/me)(\?|$)/;

export async function hydrateTokens() {
  const [a, r] = await Promise.all([storeGet(ACCESS), storeGet(REFRESH)]);
  memoryAccess = a || "";
  memoryRefresh = r || "";
}

export function getAccessToken() {
  return memoryAccess;
}
export function getRefreshToken() {
  return memoryRefresh;
}
export function getAuthEpoch() {
  return authEpoch;
}

function bumpAuth() {
  authEpoch += 1;
  getInflight.clear();
  getTtlCache.clear();
}

export async function setTokens(access: string, refresh?: string) {
  bumpAuth();
  memoryAccess = access;
  if (refresh) memoryRefresh = refresh;
  await storeSet(ACCESS, access);
  if (refresh) await storeSet(REFRESH, refresh);
}

export async function clearTokens() {
  bumpAuth();
  memoryAccess = "";
  memoryRefresh = "";
  await Promise.all([storeDel(ACCESS), storeDel(REFRESH)]);
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: ctrl.signal });
  } catch (e) {
    if (e instanceof Error && e.name === "AbortError") {
      throw new ApiError(0, "The request timed out. Check the API URL and your network.");
    }
    throw new ApiError(0, "Cannot reach the ERP server. Set EXPO_PUBLIC_API_URL and confirm Spring Boot is running.");
  } finally {
    clearTimeout(timer);
  }
}

let refreshInFlight: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    try {
      const res = await fetchWithTimeout(apiUrl("/api/v1/auth/refresh"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      }, GET_TIMEOUT_MS);
      if (!res.ok) return false;
      const json = await res.json();
      const data = json.data;
      if (!data?.accessToken) return false;
      await setTokens(data.accessToken, data.refreshToken);
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const method = (init.method || "GET").toUpperCase();
  if (method !== "GET") getTtlCache.clear();
  if (method === "GET" && retry) {
    const key = `${path}|${getAccessToken() || "anon"}`;
    if (CACHEABLE_GET.test(path)) {
      const hit = getTtlCache.get(key);
      if (hit && hit.exp > Date.now()) return hit.data as T;
    }
    const existing = getInflight.get(key);
    if (existing) return existing as Promise<T>;
    const pending = send<T>(path, init, retry)
      .then((data) => {
        if (CACHEABLE_GET.test(path)) getTtlCache.set(key, { exp: Date.now() + GET_TTL_MS, data });
        return data;
      })
      .finally(() => {
        if (getInflight.get(key) === pending) getInflight.delete(key);
      });
    getInflight.set(key, pending);
    return pending;
  }
  return send<T>(path, init, retry);
}

async function send<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getAccessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const startedEpoch = getAuthEpoch();
  const method = (init.method || "GET").toUpperCase();
  const timeoutMs =
    path.includes("/auth/login") || path.includes("/auth/refresh") || method === "GET" || method === "HEAD"
      ? GET_TIMEOUT_MS
      : REQUEST_TIMEOUT_MS;
  const res = await fetchWithTimeout(apiUrl(path), { ...init, headers }, timeoutMs);
  if (
    res.status === 401 &&
    retry &&
    !path.includes("/auth/login") &&
    !path.includes("/auth/refresh") &&
    !path.includes("/auth/logout") &&
    !path.includes("/auth/change-password")
  ) {
    if (getAuthEpoch() !== startedEpoch) return send<T>(path, init, false);
    const ok = await tryRefresh();
    if (ok) return send<T>(path, init, false);
  }
  const text = await res.text();
  let json: { success?: boolean; data?: T; message?: string; errorCode?: string; errors?: { field: string; message: string }[] } | null = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = null;
    }
  }
  if (!res.ok) {
    throw new ApiError(res.status, (json as ApiErrorBody) || text || res.statusText);
  }
  if (json && Object.prototype.hasOwnProperty.call(json, "data")) return json.data as T;
  return json as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body: JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  upload: <T>(path: string, file: { uri: string; name: string; type: string }) => {
    const fd = new FormData();
    fd.append("file", { uri: file.uri, name: file.name, type: file.type } as unknown as Blob);
    return request<T>(path, { method: "POST", body: fd });
  },
};
