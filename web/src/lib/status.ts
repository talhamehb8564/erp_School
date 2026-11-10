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
