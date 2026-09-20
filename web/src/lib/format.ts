import type { Role } from "./types";
import { ROLE_LABEL } from "./types";

export function money(n?: number | string | null, currency = "PKR") {
  if (n === undefined || n === null || n === "") return "—";
  const v = typeof n === "string" ? Number(n) : n;
  if (Number.isNaN(v)) return String(n);
  return `${currency} ${v.toLocaleString("en-PK", { maximumFractionDigits: 0 })}`;
}

export function fmtDate(iso?: string) {
  if (!iso) return "—";
  const d = iso.slice(0, 10);
  return d;
}

export function fmtTime(t?: string) {
  if (!t) return "—";
  return t.slice(0, 5);
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

export function statusTone(s?: string) {
  const v = (s || "").toUpperCase();
  if (["ACTIVE", "PAID", "PRESENT", "SUBMITTED", "REVIEWED"].includes(v)) return "ok";
  if (["PENDING", "PAYMENT_SUBMITTED", "PAYMENT_UNDER_VERIFICATION", "LATE", "UNPAID"].includes(v)) return "warn";
  if (["SUSPENDED", "DISABLED", "EXPIRED", "REJECTED", "PAYMENT_REJECTED", "OVERDUE", "ABSENT", "LOCKED", "INACTIVE"].includes(v))
    return "bad";
  return "mute";
}

export function pretty(s?: string) {
  if (!s) return "—";
  return s.replaceAll("_", " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}
