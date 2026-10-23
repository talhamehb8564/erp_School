import Constants from "expo-constants";

function trimSlash(url: string) {
  return url.replace(/\/+$/, "");
}

/** Resolve the Spring Boot origin. Never hardcode production localhost. */
export function apiOrigin() {
  const env = process.env.EXPO_PUBLIC_API_URL?.trim();
  if (env) return trimSlash(env);
  const extra = (Constants.expoConfig?.extra as { apiUrl?: string } | undefined)?.apiUrl?.trim();
  if (extra) return trimSlash(extra);
  const hostUri = Constants.expoConfig?.hostUri || Constants.linkingUri;
  const host = typeof hostUri === "string" ? hostUri.replace(/^https?:\/\//, "").split(":")[0] : "";
  if (host && host !== "localhost" && host !== "127.0.0.1") {
    return `http://${host}:8080`;
  }
  if (__DEV__) return "http://127.0.0.1:8080";
  return "";
}

export function apiUrl(path: string) {
  const origin = apiOrigin();
  if (!origin) return path;
  if (path.startsWith("http")) return path;
  return `${origin}${path.startsWith("/") ? path : `/${path}`}`;
}
