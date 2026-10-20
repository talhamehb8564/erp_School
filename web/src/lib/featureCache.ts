const KEY = "atrium.features";
const FRESH_MS = 45_000;

export function writeFeatureCache(tenantId: string | undefined, flags: Record<string, boolean> | null | undefined) {
  if (!tenantId || !flags || !Object.keys(flags).length) return;
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ tenantId, flags, at: Date.now() }));
  } catch {
    /* ignore quota */
  }
}

export function readFeatureCache(tenantId?: string | null): Record<string, boolean> | null {
  if (!tenantId) return null;
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { tenantId?: string; flags?: Record<string, boolean> };
    if (parsed.tenantId !== tenantId || !parsed.flags) return null;
    return parsed.flags;
  } catch {
    return null;
  }
}

export function isFeatureCacheFresh(tenantId?: string | null, maxAge = FRESH_MS) {
  if (!tenantId) return false;
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return false;
    const parsed = JSON.parse(raw) as { tenantId?: string; flags?: Record<string, boolean>; at?: number };
    if (parsed.tenantId !== tenantId || !parsed.flags) return false;
    return Date.now() - (parsed.at || 0) < maxAge;
  } catch {
    return false;
  }
}

export function clearFeatureCache() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}
