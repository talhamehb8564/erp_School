const KEY = "atrium.features";

export function writeFeatureCache(tenantId: string | undefined, flags: Record<string, boolean> | null | undefined) {
  if (!tenantId || !flags) return;
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

export function clearFeatureCache() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}
