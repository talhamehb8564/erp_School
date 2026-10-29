import type { Role, SchoolBrand } from "./types";
import { ROLE_LABEL } from "./types";

import { bakedBrand, portalAppName } from "./config";

/** In-app identity is the school's full name — never the short code. */
export function portalTitle(brand?: SchoolBrand | null, fallback?: string) {
  const name = brand?.name?.trim() || bakedBrand()?.name?.trim();
  if (name) return name;
  return fallback || portalAppName();
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
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** ISO weekday 1=Monday … 7=Sunday, matching backend timetable dayOfWeek. */
export function isoWeekday(isoDate: string) {
  const d = new Date(`${isoDate}T12:00:00`);
  const js = d.getDay();
  return js === 0 ? 7 : js;
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

const REASONS: Record<string, string> = {
  UNPAID_FEES: "Fee not paid",
  LEFT_SCHOOL: "Student has left the school",
  TRANSFERRED: "Student has left the school",
  PARENT_REQUEST: "Deactivated at parent request",
  GRADUATED: "Graduated",
  ABSENT: "Student was absent",
  MEDICAL: "Medical reason",
  DISCIPLINARY: "Disciplinary action",
  INACTIVE: "Account is inactive",
  OTHER: "Other",
};

export function statusReasonLabel(code?: string | null, note?: string | null) {
  if (!code && !note) return "";
  const key = (code || "").toUpperCase();
  const label = REASONS[key] || (code ? code.replaceAll("_", " ") : "");
  if (key === "OTHER") return (note || label).trim();
  if (note && note.trim() && note.trim().toLowerCase() !== label.toLowerCase()) {
    return `${label} — ${note.trim()}`;
  }
  return label;
}

export const EXCLUDE_REASONS = [
  { id: "ABSENT", label: "Student was absent" },
  { id: "UNPAID_FEES", label: "Fee not paid" },
  { id: "INACTIVE", label: "Account is inactive" },
  { id: "MEDICAL", label: "Medical reason" },
  { id: "DISCIPLINARY", label: "Disciplinary action" },
  { id: "OTHER", label: "Other" },
];

export const REMARK_CATEGORIES = [
  { id: "APPRECIATION", label: "Appreciation" },
  { id: "COMPLAINT", label: "Complaint" },
  { id: "ACADEMIC", label: "Academic" },
  { id: "BEHAVIOUR", label: "Behaviour" },
  { id: "PARTICIPATION", label: "Participation" },
  { id: "HOMEWORK", label: "Homework" },
  { id: "GENERAL", label: "General" },
];

export function num(v: unknown) {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}
