import type { Role, SchoolBrand } from "./types";
import { ROLE_LABEL } from "./types";

/** In-app identity is the school's full name — never the short code (e.g. GVS). */
export function portalTitle(brand?: SchoolBrand | null, fallback = "School ERP") {
  const name = brand?.name?.trim();
  if (name) return name;
  return fallback;
}

export function brandSubtitle(brand?: SchoolBrand | null, fallback = "Portal") {
  const tag = brand?.tagline?.trim();
  if (tag) return tag;
  return fallback;
}

export function toHms(t?: string | { hour?: number; minute?: number } | number[] | null) {
  if (t == null) return "08:00:00";
  if (typeof t === "string") {
    const hhmm = t.slice(0, 5);
    return hhmm.length === 5 ? `${hhmm}:00` : t;
  }
  if (Array.isArray(t) && typeof t[0] === "number") {
    return `${String(t[0]).padStart(2, "0")}:${String(t[1] || 0).padStart(2, "0")}:00`;
  }
  if (typeof t === "object" && typeof t.hour === "number") {
    return `${String(t.hour).padStart(2, "0")}:${String(t.minute ?? 0).padStart(2, "0")}:00`;
  }
  return "08:00:00";
}

export function money(n?: number | string | null, currency = "PKR") {
  if (n === undefined || n === null || n === "") return "—";
  const v = typeof n === "string" ? Number(n) : n;
  if (Number.isNaN(v)) return String(n);
  return `${currency} ${v.toLocaleString("en-PK", { maximumFractionDigits: 0 })}`;
}

export function fmtDate(iso?: string) {
  if (!iso) return "—";
  return iso.slice(0, 10);
}

export function fmtTime(t?: string | { hour?: number; minute?: number }) {
  if (t == null) return "—";
  if (typeof t === "string") return t.slice(0, 5);
  if (typeof t.hour === "number") {
    return `${String(t.hour).padStart(2, "0")}:${String(t.minute ?? 0).padStart(2, "0")}`;
  }
  return "—";
}

export function dayName(n: number) {
  return ["", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"][n] || String(n);
}

export function roleLabel(role?: Role) {
  return role ? ROLE_LABEL[role] : "—";
}

export function today() {
  return new Date().toISOString().slice(0, 10);
}

export function monthStart(d = today()) {
  return `${d.slice(0, 7)}-01`;
}

export function initials(name?: string) {
  if (!name) return "•";
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] || "") + (parts[1]?.[0] || "")).toUpperCase();
}

export function statusTone(s?: string): "ok" | "warn" | "bad" | "mute" {
  const v = (s || "").toUpperCase();
  if (["ACTIVE", "PAID", "PRESENT", "SUBMITTED", "REVIEWED", "PUBLISHED"].includes(v)) return "ok";
  if (["PENDING", "PAYMENT_SUBMITTED", "PAYMENT_UNDER_VERIFICATION", "LATE", "UNPAID"].includes(v)) return "warn";
  if (["SUSPENDED", "DISABLED", "EXPIRED", "REJECTED", "PAYMENT_REJECTED", "OVERDUE", "ABSENT", "LOCKED", "INACTIVE"].includes(v))
    return "bad";
  return "mute";
}

export function pretty(s?: string) {
  if (!s) return "—";
  return s.replaceAll("_", " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

export function num(v: unknown) {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}
