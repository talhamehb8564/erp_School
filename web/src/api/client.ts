import type { ApiErrorBody } from "../lib/types";

const ACCESS = "atrium.access";
const REFRESH = "atrium.refresh";

export const LOCKED_EVENT = "atrium:subscription-inactive";

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

const getInflight = new Map<string, Promise<unknown>>();
let authEpoch = 0;

export function getAccessToken() {
  return localStorage.getItem(ACCESS);
}
export function getRefreshToken() {
  return localStorage.getItem(REFRESH);
}
export function getAuthEpoch() {
  return authEpoch;
}
function bumpAuth() {
  authEpoch += 1;
  getInflight.clear();
}

export function setTokens(access: string, refresh?: string) {
  bumpAuth();
  localStorage.setItem(ACCESS, access);
  if (refresh) localStorage.setItem(REFRESH, refresh);
}
export function clearTokens() {
  bumpAuth();
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
      const res = await fetchWithTimeout("/api/v1/auth/refresh", {
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

const REQUEST_TIMEOUT_MS = 20_000;

async function fetchWithTimeout(url: string, init: RequestInit): Promise<Response> {
  const ctrl = new AbortController();
  const timer = window.setTimeout(() => ctrl.abort(), REQUEST_TIMEOUT_MS);
  try {
    return await fetch(url, { ...init, signal: ctrl.signal });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") {
      throw new ApiError(0, "The server took too long to respond. Check that Spring Boot is running on port 8080.");
    }
    throw new ApiError(0, "Cannot reach the ERP server. Start the Spring Boot API on port 8080.");
  } finally {
    window.clearTimeout(timer);
  }
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  retry = true,
): Promise<T> {
  const method = (init.method || "GET").toUpperCase();
  if (method === "GET" && retry) {
    const key = `${path}|${getAccessToken() || "anon"}`;
    const existing = getInflight.get(key);
    if (existing) return existing as Promise<T>;
    const pending = send<T>(path, init, retry).finally(() => {
      if (getInflight.get(key) === pending) getInflight.delete(key);
    });
    getInflight.set(key, pending);
    return pending;
  }
  return send<T>(path, init, retry);
}

async function send<T>(
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
  const startedEpoch = getAuthEpoch();
  const res = await fetchWithTimeout(path, { ...init, headers });
  if (
    res.status === 401 &&
    retry &&
    !path.includes("/auth/login") &&
    !path.includes("/auth/refresh") &&
    !path.includes("/auth/logout") &&
    !path.includes("/auth/change-password")
  ) {
    if (getAuthEpoch() !== startedEpoch) {
      return send<T>(path, init, false);
    }
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
    if (res.status === 403 && json && (json as ApiErrorBody).errorCode === "SUBSCRIPTION_INACTIVE") {
      window.dispatchEvent(new Event(LOCKED_EVENT));
    }
    throw new ApiError(res.status, (json as ApiErrorBody) || text || res.statusText);
  }
  if (json && Object.prototype.hasOwnProperty.call(json, "data")) {
    return json.data as T;
  }
  return json as T;
}

export const ALLOWED_UPLOADS = new Set([
  "application/pdf",
  "image/jpeg",
  "image/jpg",
  "image/png",
  "image/webp",
  "image/gif",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
]);

export function validateUpload(file: File) {
  if (file.size > 10 * 1024 * 1024) {
    throw new ApiError(400, {
      success: false,
      errorCode: "FILE_TOO_LARGE",
      message: "Maximum upload size is 10MB",
    });
  }
  const type = (file.type || "").split(";", 2)[0].trim().toLowerCase();
  const name = file.name.toLowerCase();
  const byExt = [".pdf", ".jpg", ".jpeg", ".png", ".webp", ".gif", ".doc", ".docx"].some((ext) => name.endsWith(ext));
  if (type && !ALLOWED_UPLOADS.has(type) && !byExt) {
    throw new ApiError(400, {
      success: false,
      errorCode: "FILE_TYPE",
      message: "File type is not allowed. Use PDF, image, or Word.",
    });
  }
  if (!type && !byExt) {
    throw new ApiError(400, {
      success: false,
      errorCode: "FILE_TYPE",
      message: "File type is not allowed. Use PDF, image, or Word.",
    });
  }
}

export async function openAuthedFile(href: string, retry = true) {
  const path = /^https?:\/\//i.test(href) ? new URL(href).pathname + new URL(href).search : href;
  const headers = new Headers();
  const token = getAccessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetchWithTimeout(path, { headers });
  if (res.status === 401 && retry) {
    const ok = await tryRefresh();
    if (ok) return openAuthedFile(href, false);
  }
  if (!res.ok) {
    throw new ApiError(res.status, "Could not open file");
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener,noreferrer");
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
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
  upload: <T>(path: string, file: File, onProgress?: (pct: number) => void) => {
    validateUpload(file);
    const fd = new FormData();
    fd.append("file", file);
    if (!onProgress) return request<T>(path, { method: "POST", body: fd });
    return uploadWithProgress<T>(path, fd, onProgress);
  },
};

function uploadWithProgress<T>(path: string, body: FormData, onProgress: (pct: number) => void, retry = true): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", path);
    const token = getAccessToken();
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.timeout = REQUEST_TIMEOUT_MS;
    xhr.upload.onprogress = (ev) => {
      if (ev.lengthComputable && ev.total > 0) onProgress(Math.round((ev.loaded / ev.total) * 100));
    };
    xhr.onload = () => {
      void (async () => {
        if (xhr.status === 401 && retry) {
          const ok = await tryRefresh();
          if (ok) {
            try {
              resolve(await uploadWithProgress<T>(path, body, onProgress, false));
            } catch (e) {
              reject(e);
            }
            return;
          }
        }
        const text = xhr.responseText || "";
        let json: { success?: boolean; data?: T; message?: string; errorCode?: string; errors?: { field: string; message: string }[] } | null = null;
        if (text) {
          try {
            json = JSON.parse(text);
          } catch {
            json = null;
          }
        }
        if (xhr.status < 200 || xhr.status >= 300) {
          reject(new ApiError(xhr.status, (json as ApiErrorBody) || text || xhr.statusText));
          return;
        }
        if (json && Object.prototype.hasOwnProperty.call(json, "data")) {
          resolve(json.data as T);
          return;
        }
        resolve(json as T);
      })();
    };
    xhr.onerror = () => reject(new ApiError(0, "Cannot reach the ERP server. Start the Spring Boot API on port 8080."));
    xhr.ontimeout = () =>
      reject(new ApiError(0, "The server took too long to respond. Check that Spring Boot is running on port 8080."));
    xhr.send(body);
  });
}
