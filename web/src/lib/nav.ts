import type { Role } from "./types";

export interface NavItem {
  to: string;
  label: string;
  roles: Role[];
}

export const SCHOOL_NAV: NavItem[] = [
  { to: "/app", label: "Dashboard", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER", "PARENT", "STUDENT"] },
  { to: "/app/students", label: "Students", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER"] },
  { to: "/app/users", label: "Users", roles: ["SCHOOL_ADMIN", "PRINCIPAL"] },
  { to: "/app/campuses", label: "Campuses", roles: ["SCHOOL_ADMIN"] },
  { to: "/app/academics", label: "Academics", roles: ["SCHOOL_ADMIN", "PRINCIPAL"] },
  { to: "/app/timetable", label: "Timetable", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT", "PARENT"] },
  { to: "/app/attendance", label: "Attendance", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"] },
  { to: "/app/homework", label: "Homework", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"] },
  { to: "/app/exams", label: "Marks & results", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"] },
  { to: "/app/fees", label: "Fees", roles: ["ACCOUNT_OFFICER", "PRINCIPAL", "PARENT", "STUDENT"] },
  { to: "/app/salaries", label: "Salaries", roles: ["SCHOOL_ADMIN", "ACCOUNT_OFFICER", "PRINCIPAL", "TEACHER"] },
  { to: "/app/announcements", label: "Announcements", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT", "ACCOUNT_OFFICER"] },
  { to: "/app/calendar", label: "Calendar", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT", "ACCOUNT_OFFICER"] },
  { to: "/app/reports", label: "Reports", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "ACCOUNT_OFFICER"] },
  { to: "/app/settings", label: "Settings", roles: ["SCHOOL_ADMIN", "ACCOUNT_OFFICER"] },
  { to: "/app/billing", label: "Subscription", roles: ["SCHOOL_ADMIN"] },
  { to: "/app/notifications", label: "Notifications", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER", "PARENT", "STUDENT"] },
  { to: "/app/password", label: "Password", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER", "PARENT", "STUDENT"] },
];

export function rolesForPath(pathname: string): Role[] | undefined {
  const exact = SCHOOL_NAV.find((i) => i.to === pathname);
  return exact?.roles;
}

export const FEE_OPERATORS: Role[] = ["ACCOUNT_OFFICER", "ERP_OWNER"];
export const ACADEMIC_WRITERS: Role[] = ["SCHOOL_ADMIN", "ERP_OWNER"];
