import type { Role } from "./types";

/**
 * Mirrors backend @PreAuthorize. Screens hide actions that cannot succeed,
 * but the API still returns 403 — never grant extra roles to make a button work.
 */
export const can = {
  manageTimetable: (role?: Role) => role === "SCHOOL_ADMIN" || role === "ERP_OWNER",
  viewClassTimetable: (role?: Role) =>
    role === "SCHOOL_ADMIN" || role === "PRINCIPAL" || role === "ERP_OWNER",
  inactivateStudents: (role?: Role) => role === "SCHOOL_ADMIN" || role === "ERP_OWNER",
  manageExamSessions: (role?: Role) =>
    role === "SCHOOL_ADMIN" || role === "PRINCIPAL" || role === "ERP_OWNER",
  manageDateSheets: (role?: Role) =>
    role === "SCHOOL_ADMIN" || role === "PRINCIPAL" || role === "ERP_OWNER",
  enterMarks: (role?: Role) =>
    role === "SCHOOL_ADMIN" || role === "PRINCIPAL" || role === "TEACHER" || role === "ERP_OWNER",
  generateChallans: (role?: Role) => role === "ACCOUNT_OFFICER" || role === "ERP_OWNER",
  viewFees: (role?: Role) =>
    role === "ACCOUNT_OFFICER" || role === "PRINCIPAL" || role === "PARENT" || role === "STUDENT",
  markStudentAttendance: (role?: Role) =>
    role === "SCHOOL_ADMIN" || role === "PRINCIPAL" || role === "TEACHER",
  markStaffAttendance: (role?: Role) => role === "SCHOOL_ADMIN" || role === "PRINCIPAL",
};
