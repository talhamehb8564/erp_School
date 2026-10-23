export type Role =
  | "ERP_OWNER"
  | "SCHOOL_ADMIN"
  | "PRINCIPAL"
  | "TEACHER"
  | "ACCOUNT_OFFICER"
  | "PARENT"
  | "STUDENT";

export type UserStatus = "ACTIVE" | "INACTIVE" | "LOCKED";
export type TenantStatus = "PENDING" | "ACTIVE" | "SUSPENDED" | "DISABLED" | "EXPIRED";
export type SubscriptionStatus =
  | "PENDING"
  | "PAYMENT_SUBMITTED"
  | "PAID"
  | "REJECTED"
  | "EXPIRED"
  | "SUSPENDED";
export type ChallanStatus =
  | "UNPAID"
  | "PAYMENT_UNDER_VERIFICATION"
  | "PAID"
  | "PAYMENT_REJECTED"
  | "OVERDUE";
export type AttendanceStatus = "PRESENT" | "ABSENT" | "LATE" | "LEAVE";
export type StudentStatus = "ACTIVE" | "INACTIVE" | "GRADUATED" | "LEFT_SCHOOL";

export interface ApiErrorBody {
  success: false;
  message?: string;
  errorCode?: string;
  errors?: { field: string; message: string }[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface User {
  id: string;
  tenantId?: string;
  campusId?: string;
  username: string;
  email?: string;
  firstName: string;
  lastName: string;
  fullName?: string;
  phone?: string;
  role: Role;
  status: UserStatus;
  mustChangePassword: boolean;
  lastLoginAt?: string;
}

export interface AuthPayload {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
  tenant?: Tenant | null;
  features?: Record<string, boolean> | null;
}

export interface Tenant {
  id: string;
  code: string;
  name: string;
  legalName?: string;
  email?: string;
  phone?: string;
  addressLine?: string;
  city?: string;
  logoUrl?: string;
  primaryColor?: string;
  accentColor?: string;
  secondaryColor?: string;
  tagline?: string;
  status: TenantStatus;
  academicYear?: string;
  academicSession?: string;
}

export interface SubscriptionPayment {
  id: string;
  slipUrl?: string;
  transactionRef?: string;
  status: SubscriptionStatus;
  submittedAt?: string;
  reviewedAt?: string;
  rejectionReason?: string;
}

export interface Subscription {
  id: string;
  tenantId: string;
  tenantName?: string;
  planName?: string;
  status: SubscriptionStatus;
  amount?: number;
  currency?: string;
  periodStart?: string;
  periodEnd?: string;
  notes?: string;
  payments?: SubscriptionPayment[];
}

export interface Campus {
  id: string;
  name: string;
  code: string;
  address?: string;
  city?: string;
  phone?: string;
  email?: string;
}

export interface SchoolClass {
  id: string;
  campusId?: string;
  name: string;
  grade?: string;
}

export interface Section {
  id: string;
  classId: string;
  name: string;
}

export interface Subject {
  id: string;
  name: string;
  code?: string;
}

export interface StudentUser {
  id: string;
  userId?: string;
  firstName?: string;
  lastName?: string;
  fullName?: string;
  rollNumber?: string;
  admissionNumber?: string;
  classId?: string;
  sectionId?: string;
  className?: string;
  sectionName?: string;
  campusId?: string;
  campusName?: string;
  status?: StudentStatus;
  phone?: string;
  cnic?: string;
}

export interface ExamSession {
  id: string;
  name: string;
  published?: boolean;
  announceAt?: string;
  campusId?: string;
}

export interface ExamResult {
  id?: string;
  subjectId?: string;
  subjectName?: string;
  totalMarks?: number;
  obtainedMarks?: number;
  grade?: string;
  absent?: boolean;
}

export interface FeeChallan {
  id: string;
  challanNumber: string;
  studentId?: string;
  studentName?: string;
  rollNumber?: string;
  className?: string;
  campusName?: string;
  month?: string;
  dueDate?: string;
  totalPayable?: number;
  status: ChallanStatus;
  proofs?: { id?: string; slipUrl?: string; status?: string }[];
}

export interface Homework {
  id: string;
  title: string;
  description?: string;
  dueDate?: string;
  classId?: string;
  sectionId?: string;
  subjectId?: string;
  className?: string;
  sectionName?: string;
  subjectName?: string;
  attachments?: { id?: string; fileName?: string; fileUrl: string }[];
}

export interface Notification {
  id: string;
  type?: string;
  title?: string;
  body?: string;
  entityType?: string;
  entityId?: string;
  readAt?: string;
  createdAt?: string;
}

export interface TimetableSlot {
  id: string;
  dayOfWeek: number;
  startTime?: string;
  endTime?: string;
  classId?: string;
  sectionId?: string;
  subjectId?: string;
  className?: string;
  sectionName?: string;
  subjectName?: string;
  teacherName?: string;
}

export interface StudentAttendance {
  id: string;
  attendanceDate: string;
  status: AttendanceStatus;
  remarks?: string;
  studentId?: string;
}

export interface SchoolSettings {
  bankName?: string;
  accountTitle?: string;
  accountNumber?: string;
  iban?: string;
  jazzcash?: string;
  easypaisa?: string;
  paymentInstructions?: string;
}

export const ROLE_LABEL: Record<Role, string> = {
  ERP_OWNER: "ERP Owner",
  SCHOOL_ADMIN: "School Admin",
  PRINCIPAL: "Principal",
  TEACHER: "Teacher",
  ACCOUNT_OFFICER: "Account Officer",
  PARENT: "Parent",
  STUDENT: "Student",
};

export const LOCKED_STATUSES: TenantStatus[] = ["SUSPENDED", "DISABLED", "EXPIRED"];

export const FEATURE_KEYS = [
  "ATTENDANCE",
  "TEACHER_ATTENDANCE",
  "RESULTS",
  "ONLINE_EXAMS",
  "FEES",
  "HOMEWORK",
  "TIMETABLE",
  "NOTIFICATIONS",
  "ANNOUNCEMENTS",
  "ADMISSIONS",
  "STUDENTS",
  "PARENT_PORTAL",
  "CALENDAR",
  "SALARIES",
  "WEBSITE",
] as const;

export type FeatureKey = (typeof FEATURE_KEYS)[number];
