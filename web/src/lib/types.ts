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
export type StudentStatus = "ACTIVE" | "INACTIVE" | "GRADUATED" | "TRANSFERRED";
export type HomeworkSubmissionStatus = "SUBMITTED" | "LATE" | "REVIEWED";
export type SalaryStatus = "UNPAID" | "PAID";

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
  createdAt?: string;
}

export interface AuthPayload {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
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
  state?: string;
  country?: string;
  postalCode?: string;
  logoUrl?: string;
  website?: string;
  status: TenantStatus;
  academicYear?: string;
  academicSession?: string;
  timezone?: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateTenantResponse {
  tenant: Tenant;
  administrator?: { user: User; temporaryPassword?: string; message?: string };
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
  status: SubscriptionStatus;
  amount?: number;
  currency?: string;
  periodStart?: string;
  periodEnd?: string;
  notes?: string;
  createdAt?: string;
  payments?: SubscriptionPayment[];
}

export interface Campus {
  id: string;
  tenantId?: string;
  name: string;
  code: string;
  address?: string;
  city?: string;
  phone?: string;
}

export interface SchoolClass {
  id: string;
  campusId?: string;
  name: string;
  grade?: string;
  academicSession?: string;
}

export interface Section {
  id: string;
  classId: string;
  name: string;
}

export interface Subject {
  id: string;
  name: string;
  code: string;
}

export interface TeacherAssignment {
  id: string;
  teacherUserId: string;
  classId: string;
  sectionId: string;
  subjectId: string;
}

export interface TimetableSlot {
  id: string;
  classId: string;
  sectionId: string;
  subjectId: string;
  teacherUserId: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  className?: string;
  sectionName?: string;
  subjectName?: string;
  teacherName?: string;
}

export interface StudentUser {
  id: string;
  userId?: string;
  campusId?: string;
  classId?: string;
  sectionId?: string;
  admissionNumber?: string;
  registrationNumber?: string;
  rollNumber?: string;
  photoUrl?: string;
  gender?: string;
  dateOfBirth?: string;
  admissionDate?: string;
  status?: StudentStatus;
  address?: string;
  guardianName?: string;
  guardianPhone?: string;
  user?: User;
}

export interface EnrollResponse {
  student: StudentUser;
  studentAccount?: { user: User; temporaryPassword?: string; message?: string };
  parentAccount?: { user: User; temporaryPassword?: string; message?: string };
  message?: string;
}

export interface StudentAttendance {
  id: string;
  timetableSlotId?: string;
  studentId: string;
  teacherUserId?: string;
  attendanceDate: string;
  status: AttendanceStatus;
  remarks?: string;
}

export interface TeacherAttendance {
  id: string;
  teacherUserId: string;
  attendanceDate: string;
  status: AttendanceStatus;
  remarks?: string;
}

export interface Homework {
  id: string;
  teacherUserId: string;
  classId: string;
  sectionId: string;
  subjectId: string;
  title: string;
  description?: string;
  dueDate: string;
  createdAt?: string;
  attachments?: { id?: string; fileName?: string; fileUrl?: string; contentType?: string }[];
}

export interface HomeworkSubmission {
  id: string;
  homeworkId: string;
  studentId: string;
  fileUrl?: string;
  notes?: string;
  status: HomeworkSubmissionStatus;
  submittedAt?: string;
  reviewedAt?: string;
  teacherRemark?: string;
}

export interface ExamSession {
  id: string;
  name: string;
  academicSession?: string;
  startDate?: string;
  endDate?: string;
  published?: boolean;
  publishedAt?: string;
}

export interface ExamResult {
  id: string;
  examSessionId: string;
  studentId: string;
  subjectId: string;
  totalMarks: number;
  obtainedMarks: number;
  percentage?: number;
  grade?: string;
  passStatus?: string;
  remarks?: string;
}

export interface FeeStructure {
  id?: string;
  classId?: string;
  name: string;
  academicYear?: string;
  tuitionAmount: number;
}

export interface FeeChallan {
  id: string;
  studentId: string;
  challanNumber: string;
  month: string;
  issueDate: string;
  dueDate: string;
  tuitionFee: number;
  previousOutstanding: number;
  discountAmount: number;
  additionalCharges: number;
  totalPayable: number;
  status: ChallanStatus;
  charges?: unknown[];
  proofs?: FeeProof[];
  studentName?: string;
  rollNumber?: string;
  admissionNumber?: string;
  classId?: string;
  sectionId?: string;
  campusId?: string;
  campusName?: string;
  guardianName?: string;
}

export interface FeeProof {
  id: string;
  challanId?: string;
  slipUrl?: string;
  transactionRef?: string;
  status?: ChallanStatus;
  remarks?: string;
  reviewedAt?: string;
  challanNumber?: string;
  studentId?: string;
  studentName?: string;
  rollNumber?: string;
  month?: string;
  dueDate?: string;
  totalPayable?: number;
  campusName?: string;
}

export interface StaffProfile {
  id: string;
  userId: string;
  baseSalary: number;
}

export interface StaffSalary {
  id: string;
  staffUserId: string;
  month: string;
  baseSalary: number;
  attendanceDeductions?: number;
  otherDeductions?: number;
  bonuses?: number;
  netPay?: number;
  status: SalaryStatus;
  paymentDate?: string;
}

export interface Announcement {
  id: string;
  title: string;
  body: string;
  audience: string;
  classId?: string;
  sectionId?: string;
  attachmentUrl?: string;
  publishDate: string;
  expiryDate?: string;
}

export interface SchoolEvent {
  id: string;
  title: string;
  description?: string;
  eventType: string;
  startDate: string;
  endDate?: string;
  audience: string;
}

export interface SchoolSettings {
  tenantId?: string;
  paymentInstructions?: string;
  bankName?: string;
  accountTitle?: string;
  accountNumber?: string;
  iban?: string;
  jazzcash?: string;
  easypaisa?: string;
  otherPaymentMethods?: string;
}

export interface FeeChargeType {
  id: string;
  name: string;
  defaultAmount: number;
  active?: boolean;
}

export interface TimetableSettings {
  startTime?: string;
  endTime?: string;
  lectureMinutes?: number;
  breakMinutes?: number;
  lecturesPerDay?: number;
  workingDays?: string;
  suggestedSlots?: { lectureNo: number; startTime: string; endTime: string }[];
}

export interface Notification {
  id: string;
  type?: string;
  title: string;
  body?: string;
  entityType?: string;
  entityId?: string;
  readAt?: string;
  createdAt?: string;
}

export interface StoredFile {
  id: string;
  url: string;
  fileName: string;
  contentType?: string;
  sizeBytes?: number;
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
