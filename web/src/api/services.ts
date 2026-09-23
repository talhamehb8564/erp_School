import { api } from "./client";
import type {
  Announcement,
  AuthPayload,
  Campus,
  ChallanStatus,
  CreateTenantResponse,
  EnrollResponse,
  ExamResult,
  ExamSession,
  FeeChallan,
  FeeChargeType,
  FeeProof,
  FeeStructure,
  TimetableSettings,
  Homework,
  HomeworkSubmission,
  Notification,
  Page,
  SchoolClass,
  SchoolEvent,
  SchoolSettings,
  Section,
  StaffProfile,
  StaffSalary,
  StoredFile,
  StudentAttendance,
  StudentUser,
  Subject,
  Subscription,
  TeacherAssignment,
  TeacherAttendance,
  Tenant,
  TenantStatus,
  TimetableSlot,
  User,
  Role,
  UserStatus,
} from "../lib/types";

const v1 = "/api/v1";

export const authApi = {
  login: (username: string, password: string) =>
    api.post<AuthPayload>(`${v1}/auth/login`, { username, password }),
  refresh: (refreshToken: string) =>
    api.post<AuthPayload>(`${v1}/auth/refresh`, { refreshToken }),
  logout: (refreshToken: string) =>
    api.post<void>(`${v1}/auth/logout`, { refreshToken }),
  me: () => api.get<User>(`${v1}/auth/me`),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<void>(`${v1}/auth/change-password`, { currentPassword, newPassword }),
};

export const tenantApi = {
  list: (q?: string, status?: string, page = 0) => {
    const p = new URLSearchParams();
    if (q) p.set("q", q);
    if (status) p.set("status", status);
    p.set("size", "50");
    p.set("page", String(page));
    return api.get<Page<Tenant>>(`${v1}/tenants?${p}`);
  },
  get: (id: string) => api.get<Tenant>(`${v1}/tenants/${id}`),
  me: () => api.get<Tenant>(`${v1}/tenants/me`),
  create: (body: Record<string, unknown>) => api.post<CreateTenantResponse>(`${v1}/tenants`, body),
  update: (id: string, body: Record<string, unknown>) => api.put<Tenant>(`${v1}/tenants/${id}`, body),
  changeStatus: (id: string, status: TenantStatus, reason?: string) =>
    api.patch<Tenant>(`${v1}/tenants/${id}/status`, { status, reason }),
};

export const userApi = {
  list: (params: { role?: Role; q?: string; status?: UserStatus; tenantId?: string; page?: number } = {}) => {
    const p = new URLSearchParams({ size: "50", page: String(params.page ?? 0) });
    if (params.role) p.set("role", params.role);
    if (params.q) p.set("q", params.q);
    if (params.status) p.set("status", params.status);
    if (params.tenantId) p.set("tenantId", params.tenantId);
    return api.get<Page<User>>(`${v1}/users?${p}`);
  },
  get: (id: string) => api.get<User>(`${v1}/users/${id}`),
  create: (body: Record<string, unknown>) =>
    api.post<{ user: User; temporaryPassword?: string; message?: string }>(`${v1}/users`, body),
  update: (id: string, body: Record<string, unknown>) => api.put<User>(`${v1}/users/${id}`, body),
  activate: (id: string) => api.patch<User>(`${v1}/users/${id}/activate`),
  deactivate: (id: string) => api.patch<User>(`${v1}/users/${id}/deactivate`),
  resetPassword: (id: string) =>
    api.post<{ user: User; temporaryPassword?: string; message?: string }>(`${v1}/users/${id}/reset-password`),
};

export const subscriptionApi = {
  list: () => api.get<Page<Subscription>>(`${v1}/subscriptions?size=50`),
  current: (tenantId?: string) =>
    api.get<Subscription>(`${v1}/subscriptions/current${tenantId ? `?tenantId=${tenantId}` : ""}`),
  create: (body: { tenantId: string; amount?: number; currency?: string; notes?: string }) =>
    api.post<Subscription>(`${v1}/subscriptions`, body),
  submitPayment: (id: string, body: { slipUrl: string; transactionRef?: string }) =>
    api.post<Subscription>(`${v1}/subscriptions/${id}/payments`, body),
  review: (id: string, body: { approve: boolean; rejectionReason?: string; periodStart?: string; periodEnd?: string }) =>
    api.post<Subscription>(`${v1}/subscriptions/${id}/review`, body),
  expireOverdue: () => api.post<Subscription[]>(`${v1}/subscriptions/expire-overdue`),
};

export const campusApi = {
  list: () => api.get<Campus[]>(`${v1}/campuses`),
  create: (body: Record<string, unknown>) => api.post<Campus>(`${v1}/campuses`, body),
  update: (id: string, body: Record<string, unknown>) => api.put<Campus>(`${v1}/campuses/${id}`, body),
};

export const academicApi = {
  classes: () => api.get<SchoolClass[]>(`${v1}/classes`),
  createClass: (body: Record<string, unknown>) => api.post<SchoolClass>(`${v1}/classes`, body),
  sections: (classId: string) => api.get<Section[]>(`${v1}/classes/${classId}/sections`),
  allSections: () => api.get<Section[]>(`${v1}/sections`),
  createSection: (classId: string, name: string) =>
    api.post<Section>(`${v1}/classes/${classId}/sections`, { name }),
  subjects: () => api.get<Subject[]>(`${v1}/subjects`),
  createSubject: (body: { name: string; code: string }) => api.post<Subject>(`${v1}/subjects`, body),
  assignSubject: (classId: string, subjectId: string) =>
    api.post<unknown>(`${v1}/classes/${classId}/subjects/${subjectId}`),
  assignments: (teacherUserId?: string) =>
    api.get<TeacherAssignment[]>(`${v1}/teacher-assignments${teacherUserId ? `?teacherUserId=${teacherUserId}` : ""}`),
  assignTeacher: (body: Record<string, unknown>) =>
    api.post<TeacherAssignment>(`${v1}/teacher-assignments`, body),
  timetable: (classId: string, sectionId: string) =>
    api.get<TimetableSlot[]>(`${v1}/timetable?classId=${classId}&sectionId=${sectionId}`),
  createSlot: (body: Record<string, unknown>) => api.post<TimetableSlot>(`${v1}/timetable`, body),
  updateSlot: (id: string, body: Record<string, unknown>) => api.put<TimetableSlot>(`${v1}/timetable/${id}`, body),
  deleteSlot: (id: string) => api.del<void>(`${v1}/timetable/${id}`),
  applyTimetable: (body: Record<string, unknown>) => api.post<TimetableSlot[]>(`${v1}/timetable/apply`, body),
  copyTimetable: (body: Record<string, unknown>) => api.post<TimetableSlot[]>(`${v1}/timetable/copy`, body),
  timetableSettings: () => api.get<TimetableSettings>(`${v1}/timetable/settings`),
  saveTimetableSettings: (body: Record<string, unknown>) =>
    api.put<TimetableSettings>(`${v1}/timetable/settings`, body),
  teacherTimetable: () => api.get<TimetableSlot[]>(`${v1}/teacher/timetable`),
  teacherToday: () => api.get<TimetableSlot[]>(`${v1}/teacher/timetable/today`),
};

export const studentApi = {
  list: (classId?: string, sectionId?: string, page = 0) => {
    const p = new URLSearchParams({ size: "50", page: String(page) });
    if (classId) p.set("classId", classId);
    if (sectionId) p.set("sectionId", sectionId);
    return api.get<Page<StudentUser>>(`${v1}/students?${p}`);
  },
  get: (id: string) => api.get<StudentUser>(`${v1}/students/${id}`),
  me: () => api.get<StudentUser>(`${v1}/students/me`),
  enroll: (body: Record<string, unknown>) => api.post<EnrollResponse>(`${v1}/students`, body),
  update: (id: string, body: Record<string, unknown>) => api.put<StudentUser>(`${v1}/students/${id}`, body),
  linkParent: (id: string, parentUserId: string, relationship?: string) =>
    api.post<unknown>(`${v1}/students/${id}/parents`, { parentUserId, relationship }),
  children: () => api.get<StudentUser[]>(`${v1}/parents/me/children`),
};

export const attendanceApi = {
  roster: (slotId: string) => api.get<StudentUser[]>(`${v1}/attendance/lectures/${slotId}/roster`),
  markLecture: (slotId: string, body: { date: string; marks: { studentId: string; status: string; remarks?: string }[] }) =>
    api.post<StudentAttendance[]>(`${v1}/attendance/lectures/${slotId}`, body),
  lectureSheet: (slotId: string, date: string) =>
    api.get<StudentAttendance[]>(`${v1}/attendance/lectures/${slotId}?date=${date}`),
  studentHistory: (studentId: string, from: string, to: string) =>
    api.get<StudentAttendance[]>(`${v1}/attendance/students/${studentId}?from=${from}&to=${to}`),
  markTeacher: (body: Record<string, unknown>) =>
    api.post<TeacherAttendance>(`${v1}/attendance/teachers`, body),
  teacherHistory: (from: string, to: string, teacherUserId?: string) => {
    const p = new URLSearchParams({ from, to });
    if (teacherUserId) p.set("teacherUserId", teacherUserId);
    return api.get<TeacherAttendance[]>(`${v1}/attendance/teachers?${p}`);
  },
};

export const homeworkApi = {
  list: (classId?: string, sectionId?: string) => {
    const p = new URLSearchParams();
    if (classId && sectionId) {
      p.set("classId", classId);
      p.set("sectionId", sectionId);
    }
    const q = p.toString();
    return api.get<Homework[]>(`${v1}/homework${q ? `?${q}` : ""}`);
  },
  get: (id: string) => api.get<Homework>(`${v1}/homework/${id}`),
  create: (body: Record<string, unknown>) => api.post<Homework>(`${v1}/homework`, body),
  submit: (id: string, body: { studentId?: string; fileUrl?: string; notes?: string }) =>
    api.post<HomeworkSubmission>(`${v1}/homework/${id}/submissions`, body),
  submissions: (id: string) => api.get<HomeworkSubmission[]>(`${v1}/homework/${id}/submissions`),
  review: (submissionId: string, remark?: string) =>
    api.post<HomeworkSubmission>(`${v1}/homework/submissions/${submissionId}/review`, { remark }),
};

export const examApi = {
  sessions: () => api.get<ExamSession[]>(`${v1}/exams/sessions`),
  createSession: (body: Record<string, unknown>) => api.post<ExamSession>(`${v1}/exams/sessions`, body),
  upsertResult: (sessionId: string, body: Record<string, unknown>) =>
    api.post<ExamResult>(`${v1}/exams/sessions/${sessionId}/results`, body),
  publish: (sessionId: string) => api.post<ExamSession>(`${v1}/exams/sessions/${sessionId}/publish`),
  sessionResults: (sessionId: string) => api.get<ExamResult[]>(`${v1}/exams/sessions/${sessionId}/results`),
  studentResult: (sessionId: string, studentId: string) =>
    api.get<{ session: ExamSession; subjects: ExamResult[]; totalMarks: number; obtainedMarks: number; percentage: number; grade: string; passStatus: string; rollNumber?: string; admissionNumber?: string; studentId?: string }>(
      `${v1}/exams/sessions/${sessionId}/students/${studentId}`,
    ),
  byRoll: (sessionId: string, rollNumber: string) =>
    api.get<{ session: ExamSession; subjects: ExamResult[]; totalMarks: number; obtainedMarks: number; percentage: number; grade: string; passStatus: string; rollNumber?: string; admissionNumber?: string; studentId?: string }>(
      `${v1}/exams/sessions/${sessionId}/results/by-roll?rollNumber=${encodeURIComponent(rollNumber)}`,
    ),
};

export const feeApi = {
  structures: () => api.get<FeeStructure[]>(`${v1}/fees/structures`),
  saveStructure: (body: Record<string, unknown>) => api.post<FeeStructure>(`${v1}/fees/structures`, body),
  generate: (body: Record<string, unknown>) => api.post<FeeChallan[]>(`${v1}/fees/challans/generate`, body),
  studentChallans: (studentId: string) => api.get<FeeChallan[]>(`${v1}/fees/students/${studentId}/challans`),
  submitProof: (id: string, body: { slipUrl: string; transactionRef?: string }) =>
    api.post<FeeChallan>(`${v1}/fees/challans/${id}/proofs`, body),
  reviewProof: (id: string, approve: boolean, remarks?: string) =>
    api.post<FeeChallan>(`${v1}/fees/proofs/${id}/review`, { approve, remarks }),
  pendingProofs: () => api.get<FeeProof[]>(`${v1}/fees/proofs/pending`),
  byStatus: (status: ChallanStatus) => api.get<FeeChallan[]>(`${v1}/fees/challans?status=${status}`),
  markOverdue: () => api.post<FeeChallan[]>(`${v1}/fees/challans/mark-overdue`),
  getChallan: (id: string) => api.get<FeeChallan & Record<string, unknown>>(`${v1}/fees/challans/${id}`),
  chargeTypes: () => api.get<FeeChargeType[]>(`${v1}/fees/charge-types`),
  saveChargeType: (body: { name: string; defaultAmount: number }) =>
    api.post<FeeChargeType>(`${v1}/fees/charge-types`, body),
  applyDiscount: (studentId: string, body: { percent?: number; amount?: number; reason?: string }) =>
    api.post<unknown>(`${v1}/fees/students/${studentId}/discounts`, body),
};

export const salaryApi = {
  profiles: () => api.get<StaffProfile[]>(`${v1}/salaries/profiles`),
  upsertProfile: (userId: string, baseSalary: number) =>
    api.post<StaffProfile>(`${v1}/salaries/profiles`, { userId, baseSalary }),
  generate: (month: string) => api.post<StaffSalary[]>(`${v1}/salaries/generate`, { month }),
  month: (month: string) => api.get<StaffSalary[]>(`${v1}/salaries?month=${month}`),
  mine: () => api.get<StaffSalary[]>(`${v1}/salaries/me`),
  pay: (id: string, paymentDate?: string) =>
    api.post<StaffSalary>(`${v1}/salaries/${id}/pay`, paymentDate ? { paymentDate } : {}),
  adjust: (id: string, body: { otherDeductions?: number; bonuses?: number; notes?: string }) =>
    api.post<StaffSalary>(`${v1}/salaries/${id}/adjust`, body),
};

export const announcementApi = {
  list: () => api.get<Announcement[]>(`${v1}/announcements`),
  create: (body: Record<string, unknown>) => api.post<Announcement>(`${v1}/announcements`, body),
};

export const calendarApi = {
  list: (from?: string, to?: string) => {
    const p = new URLSearchParams();
    if (from) p.set("from", from);
    if (to) p.set("to", to);
    const q = p.toString();
    return api.get<SchoolEvent[]>(`${v1}/calendar/events${q ? `?${q}` : ""}`);
  },
  create: (body: Record<string, unknown>) => api.post<SchoolEvent>(`${v1}/calendar/events`, body),
  update: (id: string, body: Record<string, unknown>) => api.put<SchoolEvent>(`${v1}/calendar/events/${id}`, body),
};

export const settingsApi = {
  get: () => api.get<SchoolSettings>(`${v1}/school-settings`),
  save: (body: SchoolSettings) => api.put<SchoolSettings>(`${v1}/school-settings`, body),
};

export const fileApi = {
  upload: (file: File, onProgress?: (pct: number) => void) =>
    api.upload<StoredFile>(`${v1}/files`, file, onProgress),
};

export const notificationApi = {
  list: () => api.get<Page<Notification>>(`${v1}/notifications?size=30`),
  unread: () => api.get<{ unread: number }>(`${v1}/notifications/unread-count`),
  read: (id: string) => api.post<Notification>(`${v1}/notifications/${id}/read`),
};

export const reportApi = {
  schoolDashboard: () => api.get<Record<string, number>>(`${v1}/dashboard/school`),
  platformDashboard: () => api.get<Record<string, number>>(`${v1}/dashboard/platform`),
  attendance: (studentId: string, from: string, to: string) =>
    api.get<Record<string, unknown>>(`${v1}/reports/attendance/students/${studentId}?from=${from}&to=${to}`),
  feeCollection: (month: string) =>
    api.get<Record<string, unknown>>(`${v1}/reports/fees/collection?month=${month}`),
  examClass: (sessionId: string, classId: string) =>
    api.get<Record<string, unknown>[]>(`${v1}/reports/exams/${sessionId}/classes/${classId}`),
};

export const portalApi = {
  teacher: () => api.get<Record<string, unknown>>(`${v1}/portals/teacher`),
  principal: () => api.get<Record<string, unknown>>(`${v1}/portals/principal`),
  account: () => api.get<Record<string, unknown>>(`${v1}/portals/account`),
  parent: () => api.get<Record<string, unknown>>(`${v1}/portals/parent`),
  student: () => api.get<Record<string, unknown>>(`${v1}/portals/student`),
};

export const healthApi = {
  ping: () => api.get<{ status: string }>(`${v1}/health`),
};
