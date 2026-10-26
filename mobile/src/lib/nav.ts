import type { FeatureKey, Role } from "./types";

export interface NavItem {
  href: string;
  label: string;
  icon: string;
  roles: Role[];
  feature?: FeatureKey;
}

export const MODULES: NavItem[] = [
  { href: "/students", label: "Students", icon: "people", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER"], feature: "STUDENTS" },
  { href: "/users", label: "Users", icon: "person-circle", roles: ["SCHOOL_ADMIN", "PRINCIPAL"] },
  { href: "/campuses", label: "Campuses", icon: "business", roles: ["SCHOOL_ADMIN"] },
  { href: "/academics", label: "Academics", icon: "library", roles: ["SCHOOL_ADMIN", "PRINCIPAL"] },
  { href: "/timetable", label: "Timetable", icon: "calendar", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT", "PARENT"], feature: "TIMETABLE" },
  { href: "/attendance", label: "Attendance", icon: "checkmark-done", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"], feature: "ATTENDANCE" },
  { href: "/teacher-attendance", label: "Staff attendance", icon: "walk", roles: ["SCHOOL_ADMIN", "PRINCIPAL"], feature: "TEACHER_ATTENDANCE" },
  { href: "/homework", label: "Homework", icon: "document-text", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"], feature: "HOMEWORK" },
  { href: "/exams", label: "Marks & results", icon: "ribbon", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"], feature: "RESULTS" },
  { href: "/online-exams", label: "Online exams", icon: "desktop-outline", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT"], feature: "ONLINE_EXAMS" },
  { href: "/fees", label: "Fees", icon: "card", roles: ["ACCOUNT_OFFICER", "PRINCIPAL", "PARENT", "STUDENT"], feature: "FEES" },
  { href: "/salaries", label: "Salaries", icon: "cash", roles: ["SCHOOL_ADMIN", "ACCOUNT_OFFICER", "PRINCIPAL", "TEACHER"], feature: "SALARIES" },
  { href: "/announcements", label: "Announcements", icon: "megaphone", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT", "ACCOUNT_OFFICER"], feature: "ANNOUNCEMENTS" },
  { href: "/calendar", label: "Calendar", icon: "calendar-outline", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT", "ACCOUNT_OFFICER"], feature: "CALENDAR" },
  { href: "/reports", label: "Reports", icon: "stats-chart", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "ACCOUNT_OFFICER"] },
  { href: "/admissions", label: "Admissions", icon: "enter", roles: ["SCHOOL_ADMIN", "PRINCIPAL"], feature: "ADMISSIONS" },
  { href: "/settings", label: "Settings", icon: "settings", roles: ["SCHOOL_ADMIN", "ACCOUNT_OFFICER"] },
  { href: "/billing", label: "Subscription", icon: "diamond", roles: ["SCHOOL_ADMIN"] },
  { href: "/schools", label: "Schools", icon: "business", roles: ["ERP_OWNER"] },
  { href: "/subscriptions", label: "Subscriptions", icon: "diamond", roles: ["ERP_OWNER"] },
];
