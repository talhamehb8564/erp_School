import type { ApiErrorBody } from "../lib/types";

const ACCESS = "atrium.access";
const REFRESH = "atrium.refresh";

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

export function getAccessToken() {
  return localStorage.getItem(ACCESS);
}
export function getRefreshToken() {
  return localStorage.getItem(REFRESH);
}
export function setTokens(access: string, refresh?: string) {
  localStorage.setItem(ACCESS, access);
  if (refresh) localStorage.setItem(REFRESH, refresh);
}
export function clearTokens() {
  localStorage.removeItem(ACCESS);
  localStorage.removeItem(REFRESH);
}

let refreshInFlight: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    try {
      const res = await fetch("/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return false;
      const json = await res.json();
      const data = json.data;
      if (!data?.accessToken) return false;
      setTokens(data.accessToken, data.refreshToken);
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  retry = true,
): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getAccessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  let res: Response;
  try {
    res = await fetch(path, { ...init, headers });
  } catch {
    throw new ApiError(0, "Cannot reach the ERP server. Start the Spring Boot API on port 8080.");
  }
  if (res.status === 401 && retry && !path.includes("/auth/login") && !path.includes("/auth/refresh")) {
    const ok = await tryRefresh();
    if (ok) return request<T>(path, init, false);
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
  if (json && Object.prototype.hasOwnProperty.call(json, "data")) {
    return json.data as T;
  }
  return json as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PUT", body: JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  upload: <T>(path: string, file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return request<T>(path, { method: "POST", body: fd });
  },
};
