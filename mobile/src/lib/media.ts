import { apiUrl } from "./config";

/** Resolve a stored file path or absolute URL for Image / Linking. */
export function mediaUrl(path?: string | null) {
  if (!path) return "";
  const v = path.trim();
  if (!v) return "";
  if (/^(https?:|file:|data:|content:)/i.test(v)) return v;
  return apiUrl(v.startsWith("/") ? v : `/${v}`);
}

export function isHexColor(value?: string | null) {
  if (!value) return false;
  return /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(value.trim());
}

export function safeHex(value?: string | null) {
  return isHexColor(value) ? value!.trim() : undefined;
}
