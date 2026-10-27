import { Platform } from "react-native";
import Constants from "expo-constants";
import * as Device from "expo-device";
import type { SchoolBrand } from "./types";

export interface BakedSchool {
  name: string;
  appName?: string;
  code?: string;
  primaryColor?: string;
  accentColor?: string;
  tagline?: string;
}

export function bakedSchool(): BakedSchool | null {
  const extra = Constants.expoConfig?.extra as { school?: BakedSchool } | undefined;
  const school = extra?.school;
  if (school?.name?.trim()) return school;
  return null;
}

/** Build-time identity for splash / first paint. Live tenant overwrites after login. */
export function bakedBrand(): SchoolBrand | null {
  const school = bakedSchool();
  if (!school) return null;
  return {
    name: school.name.trim(),
    code: school.code || "",
    primaryColor: school.primaryColor,
    accentColor: school.accentColor,
    tagline: school.tagline,
  };
}

export function portalAppName() {
  const school = bakedSchool();
  if (school?.appName?.trim()) return school.appName.trim();
  if (school?.name?.trim()) return `${school.name.trim()} Portal`;
  return "School Portal";
}

function trimSlash(url: string) {
  return url.replace(/\/+$/, "");
}

function metroLanHost() {
  const hostUri = Constants.expoConfig?.hostUri || Constants.linkingUri;
  const host = typeof hostUri === "string" ? hostUri.replace(/^https?:\/\//, "").split(":")[0] : "";
  if (!host || host === "localhost" || host === "127.0.0.1" || host === "0.0.0.0") return "";
  return host;
}

/**
 * Spring Boot origin. Never hardcode localhost or a LAN IP as a production URL.
 *
 * Development (Metro on your PC): EXPO_PUBLIC_API_URL unset → Metro LAN host :8080
 * Production Play Store / EAS: set EXPO_PUBLIC_API_URL=https://your-api.example.com
 *   (or expo.extra.apiUrl in EAS). Store builds must use HTTPS.
 *
 * A physical phone's 127.0.0.1 is the phone itself, not your PC.
 */
export function apiOrigin() {
  const env = process.env.EXPO_PUBLIC_API_URL?.trim();
  if (env) return trimSlash(env);
  const extra = (Constants.expoConfig?.extra as { apiUrl?: string } | undefined)?.apiUrl?.trim();
  if (extra) return trimSlash(extra);
  const lan = metroLanHost();
  if (lan) return `http://${lan}:8080`;
  if (__DEV__ && Platform.OS === "android" && !Device.isDevice) return "http://10.0.2.2:8080";
  if (__DEV__ && Platform.OS === "ios" && !Device.isDevice) return "http://127.0.0.1:8080";
  return "";
}

export function apiUrl(path: string) {
  const origin = apiOrigin();
  if (!origin) return path;
  if (path.startsWith("http")) return path;
  return `${origin}${path.startsWith("/") ? path : `/${path}`}`;
}
