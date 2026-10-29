import { api } from "./client";
import type {
  AuthPayload,
  Campus,
  ExamResult,
  ExamSession,
  FeeChallan,
  Homework,
  Notification,
  Page,
  Role,
  SchoolClass,
  SchoolSettings,
  Section,
  StudentAttendance,
  StudentUser,
  Subject,
  Subscription,
  Tenant,
  TenantStatus,
  TimetableSlot,
  User,
  UserStatus,
} from "../lib/types";

const v1 = "/api/v1";

export const publicApi = {
  school: (code?: string) => {
    const q = code ? `?code=${encodeURIComponent(code)}` : "";
    return api.get<{ school?: { name: string; code: string; logoUrl?: string; primaryColor?: string; accentColor?: string; secondaryColor?: string; tagline?: string; id?: string } }>(
      `${v1}/public/school${q}`,
    );
  },
};

export const authApi = {
  login: (username: string, password: string) =>
    api.post<AuthPayload>(`${v1}/auth/login`, { username, password }),
  refresh: (refreshToken: string) => api.post<AuthPayload>(`${v1}/auth/refresh`, { refreshToken }),
  logout: (refreshToken: string) => api.post<void>(`${v1}/auth/logout`, { refreshToken }),
  me: () => api.get<User>(`${v1}/auth/me`),
  changePassword: (currentPassword: string, newPassword?: string, newUsername?: string) =>
    api.post<User>(`${v1}/auth/change-password`, {
      currentPassword,
      ...(newPassword ? { newPassword } : {}),
      ...(newUsername ? { newUsername } : {}),
    }),
};

export const featureApi = {
  current: () => api.get<Record<string, boolean>>(`${v1}/features`),
  forTenant: (id: string) => api.get<Record<string, boolean>>(`${v1}/tenants/${id}/features`),
  save: (id: string, body: Record<string, boolean>) =>
    api.put<Record<string, boolean>>(`${v1}/tenants/${id}/features`, body),
};

export const tenantApi = {
  list: (q?: string, status?: string, page = 0) => {
    const p = new URLSearchParams({ size: "50", page: String(page) });
    if (q) p.set("q", q);
    if (status) p.set("status", status);
    return api.get<Page<Tenant>>(`${v1}/tenants?${p}`);
  },
  get: (id: string) => api.get<Tenant>(`${v1}/tenants/${id}`),
  me: () => api.get<Tenant>(`${v1}/tenants/me`),
  create: (body: Record<string, unknown>) => api.post<{ tenant: Tenant; administrator?: { user: User; temporaryPassword?: string } }>(`${v1}/tenants`, body),
  update: (id: string, body: Record<string, unknown>) => api.put<Tenant>(`${v1}/tenants/${id}`, body),
  changeStatus: (id: string, status: TenantStatus, reason?: string) =>
    api.patch<Tenant>(`${v1}/tenants/${id}/status`, { status, reason }),
};

export const userApi = {
  list: (params: { role?: Role; q?: string; status?: UserStatus; page?: number } = {}) => {
    const p = new URLSearchParams({ size: "50", page: String(params.page ?? 0) });
    if (params.role) p.set("role", params.role);
    if (params.q) p.set("q", params.q);
    if (params.status) p.set("status", params.status);
    return api.get<Page<User>>(`${v1}/users?${p}`);
  },
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
  submitPayment: (id: string, body: { slipUrl: string; transactionRef?: string }) =>
    api.post<Subscription>(`${v1}/subscriptions/${id}/payments`, body),
  review: (id: string, body: { approve: boolean; rejectionReason?: string; periodStart?: string; periodEnd?: string }) =>
    api.post<Subscription>(`${v1}/subscriptions/${id}/review`, body),
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
  createSection: (classId: string, name: string) => api.post<Section>(`${v1}/classes/${classId}/sections`, { name }),
  subjects: () => api.get<Subject[]>(`${v1}/subjects`),
  createSubject: (body: { name: string; code: string }) => api.post<Subject>(`${v1}/subjects`, body),
  timetable: (classId: string, sectionId: string) =>
    api.get<TimetableSlot[]>(`${v1}/timetable?classId=${classId}&sectionId=${sectionId}`),
  teacherTimetable: () => api.get<TimetableSlot[]>(`${v1}/teacher/timetable`),
  teacherToday: () => api.get<TimetableSlot[]>(`${v1}/teacher/timetable/today`),
  myTimetable: () => api.get<TimetableSlot[]>(`${v1}/timetable/me`),
  teacherAssignments: (teacherUserId?: string) =>
    api.get<{ id: string; teacherUserId?: string; classId: string; sectionId: string; subjectId: string }[]>(
      `${v1}/teacher-assignments${teacherUserId ? `?teacherUserId=${teacherUserId}` : ""}`,
    ),
  timetableSettings: () =>
    api.get<{
      workingDays?: string;
      suggestedSlots?: { lectureNo?: number; startTime?: string; endTime?: string }[];
    }>(`${v1}/timetable/settings`),
  createSlot: (body: Record<string, unknown>) => api.post<TimetableSlot>(`${v1}/timetable`, body),
  updateSlot: (id: string, body: Record<string, unknown>) => api.put<TimetableSlot>(`${v1}/timetable/${id}`, body),
  deleteSlot: (id: string) => api.del<void>(`${v1}/timetable/${id}`),
  applyPattern: (body: Record<string, unknown>) => api.post<TimetableSlot[]>(`${v1}/timetable/apply`, body),
};

export const studentApi = {
  list: (classId?: string, sectionId?: string, page = 0, q?: string, status?: string) => {
    const p = new URLSearchParams({ size: "20", page: String(page) });
    if (classId) p.set("classId", classId);
    if (sectionId) p.set("sectionId", sectionId);
    if (q) p.set("q", q);
    if (status) p.set("status", status);
    return api.get<Page<StudentUser>>(`${v1}/students?${p}`);
  },
  roster: (classId: string, sectionId?: string) => {
    const p = new URLSearchParams({ classId, status: "ACTIVE" });
    if (sectionId) p.set("sectionId", sectionId);
    return api.get<StudentUser[]>(`${v1}/students/roster?${p}`);
  },
  get: (id: string) => api.get<StudentUser>(`${v1}/students/${id}`),
  me: () => api.get<StudentUser>(`${v1}/students/me`),
  children: () => api.get<StudentUser[]>(`${v1}/parents/me/children`),
  inactivate: (id: string, reason: string, note?: string) =>
    api.post<StudentUser>(`${v1}/students/${id}/inactivate`, { reason, note }),
  reactivate: (id: string, note?: string) => api.post<StudentUser>(`${v1}/students/${id}/reactivate`, { note }),
  inactivateBulk: (body: { classId: string; sectionId?: string; reason: string; note?: string }) =>
    api.post<{ inactivated?: number; skipped?: number }>(`${v1}/students/inactivate-bulk`, body),
};

export const attendanceApi = {
  roster: (slotId: string) => api.get<StudentUser[]>(`${v1}/attendance/lectures/${slotId}/roster`),
  markLecture: (slotId: string, body: { date: string; marks: { studentId: string; status: string; remarks?: string }[] }) =>
    api.post<StudentAttendance[]>(`${v1}/attendance/lectures/${slotId}`, body),
  lectureSheet: (slotId: string, date: string) =>
    api.get<StudentAttendance[]>(`${v1}/attendance/lectures/${slotId}?date=${date}`),
  mine: (from: string, to: string) => api.get<StudentAttendance[]>(`${v1}/attendance/me?from=${from}&to=${to}`),
  studentHistory: (studentId: string, from: string, to: string) =>
    api.get<StudentAttendance[]>(`${v1}/attendance/students/${studentId}?from=${from}&to=${to}`),
  teacherDay: (date: string) => api.get<Record<string, unknown>[]>(`${v1}/attendance/teachers/day?date=${date}`),
  markTeacher: (body: Record<string, unknown>) => api.post<Record<string, unknown>>(`${v1}/attendance/teachers`, body),
};

export const homeworkApi = {
  list: (classId?: string, sectionId?: string, limit?: number) => {
    const p = new URLSearchParams();
    if (classId && sectionId) {
      p.set("classId", classId);
      p.set("sectionId", sectionId);
    }
    if (limit) p.set("limit", String(limit));
    const q = p.toString();
    return api.get<Homework[]>(`${v1}/homework${q ? `?${q}` : ""}`);
  },
  get: (id: string) => api.get<Homework>(`${v1}/homework/${id}`),
  create: (body: Record<string, unknown>) => api.post<Homework>(`${v1}/homework`, body),
  submit: (id: string, body: { studentId?: string; fileUrl?: string; notes?: string }) =>
    api.post<unknown>(`${v1}/homework/${id}/submissions`, body),
  submissions: (id: string) =>
    api.get<{ id: string; studentId?: string; fileUrl?: string; notes?: string; status?: string; submittedAt?: string }[]>(
      `${v1}/homework/${id}/submissions`,
    ),
};

export const examApi = {
  sessions: () => api.get<ExamSession[]>(`${v1}/exams/sessions`),
  createSession: (body: Record<string, unknown>) => api.post<ExamSession>(`${v1}/exams/sessions`, body),
  upsertResult: (sessionId: string, body: Record<string, unknown>) =>
    api.post<ExamResult>(`${v1}/exams/sessions/${sessionId}/results`, body),
  publish: (sessionId: string) => api.post<ExamSession>(`${v1}/exams/sessions/${sessionId}/publish`),
  setAnnounceAt: (sessionId: string, announceAt: string) =>
    api.post<ExamSession>(`${v1}/exams/sessions/${sessionId}/announce-at`, { announceAt }),
  myResult: (sessionId: string) => api.get<Record<string, unknown>>(`${v1}/exams/sessions/${sessionId}/mine`),
  studentResult: (sessionId: string, studentId: string) =>
    api.get<Record<string, unknown>>(`${v1}/exams/sessions/${sessionId}/students/${studentId}`),
  byRoll: (sessionId: string, rollNumber: string) =>
    api.get<Record<string, unknown>>(`${v1}/exams/sessions/${sessionId}/results/by-roll?rollNumber=${encodeURIComponent(rollNumber)}`),
  sessionResults: (sessionId: string) => api.get<ExamResult[]>(`${v1}/exams/sessions/${sessionId}/results`),
  deleteSession: (sessionId: string) => api.del<void>(`${v1}/exams/sessions/${sessionId}`),
};

export const onlineExamApi = {
  list: () => api.get<Record<string, unknown>[]>(`${v1}/online-exams`),
  mine: () => api.get<Record<string, unknown>[]>(`${v1}/online-exams/mine`),
  get: (id: string) => api.get<Record<string, unknown>>(`${v1}/online-exams/${id}`),
  create: (body: Record<string, unknown>) => api.post<Record<string, unknown>>(`${v1}/online-exams`, body),
  schedule: (id: string) => api.post<Record<string, unknown>>(`${v1}/online-exams/${id}/schedule`),
  publishResults: (id: string) => api.post<Record<string, unknown>>(`${v1}/online-exams/${id}/publish-results`),
  start: (id: string) => api.post<Record<string, unknown>>(`${v1}/online-exams/${id}/start`),
  saveAnswers: (id: string, body: Record<string, unknown>[]) =>
    api.put<Record<string, unknown>>(`${v1}/online-exams/${id}/answers`, body),
  submit: (id: string) => api.post<Record<string, unknown>>(`${v1}/online-exams/${id}/submit`),
  autoSubmit: (id: string) => api.post<Record<string, unknown>>(`${v1}/online-exams/${id}/auto-submit`),
  result: (id: string) => api.get<Record<string, unknown>>(`${v1}/online-exams/${id}/result`),
  attempts: (id: string) => api.get<Record<string, unknown>[]>(`${v1}/online-exams/${id}/attempts`),
  recipients: (id: string) => api.get<Record<string, unknown>>(`${v1}/online-exams/${id}/recipients`),
  saveRecipients: (id: string, body: Record<string, unknown>) =>
    api.put<Record<string, unknown>>(`${v1}/online-exams/${id}/recipients`, body),
  results: (id: string) => api.get<Record<string, unknown>[]>(`${v1}/online-exams/${id}/results`),
  remove: (id: string) => api.del<unknown>(`${v1}/online-exams/${id}`),
};

export const remarkApi = {
  list: (q: { studentId?: string; classId?: string; sectionId?: string; page?: number } = {}) => {
    const p = new URLSearchParams({ size: "40", page: String(q.page || 0) });
    if (q.studentId) p.set("studentId", q.studentId);
    if (q.classId) p.set("classId", q.classId);
    if (q.sectionId) p.set("sectionId", q.sectionId);
    return api.get<Page<Record<string, unknown>>>(`${v1}/remarks?${p}`);
  },
  create: (body: Record<string, unknown>) => api.post<Record<string, unknown>[]>(`${v1}/remarks`, body),
  remove: (id: string) => api.del<unknown>(`${v1}/remarks/${id}`),
};

export const feeApi = {
  myChallanSummaries: (limit = 20) => api.get<FeeChallan[]>(`${v1}/fees/me/challans?limit=${limit}`),
  parentChallanSummaries: (limit = 20) => api.get<FeeChallan[]>(`${v1}/fees/children/challans?limit=${limit}`),
  childChallanSummaries: (studentId: string, limit = 24) =>
    api.get<FeeChallan[]>(`${v1}/fees/students/${studentId}/challans?summary=true&limit=${limit}`),
  pendingProofs: (n = 12) => api.get<Record<string, unknown>[]>(`${v1}/fees/proofs/pending?limit=${n}`),
  getChallan: (id: string) => api.get<Record<string, unknown>>(`${v1}/fees/challans/${id}`),
  generate: (body: Record<string, unknown>) => api.post<unknown>(`${v1}/fees/challans/generate`, body),
  submitProof: (id: string, body: { slipUrl: string; transactionRef?: string }) =>
    api.post<unknown>(`${v1}/fees/challans/${id}/proofs`, body),
  reviewProof: (id: string, approve: boolean, remarks?: string) =>
    api.post<unknown>(`${v1}/fees/proofs/${id}/review`, { approve, remarks }),
  byStatus: (status: string, page = 0) =>
    api.get<FeeChallan[]>(`${v1}/fees/challans?status=${encodeURIComponent(status)}&page=${page}&size=40`),
};

export const salaryApi = {
  mine: () => api.get<Record<string, unknown>[]>(`${v1}/salaries/me`),
  month: (month: string) => api.get<Record<string, unknown>[]>(`${v1}/salaries?month=${month}`),
  generate: (month: string) => api.post<Record<string, unknown>[]>(`${v1}/salaries/generate`, { month }),
  pay: (id: string, paymentDate?: string, paymentProofUrl?: string) =>
    api.post<Record<string, unknown>>(`${v1}/salaries/${id}/pay`, {
      ...(paymentDate ? { paymentDate } : {}),
      ...(paymentProofUrl ? { paymentProofUrl } : {}),
    }),
};

export const announcementApi = {
  list: () => api.get<{ id: string; title: string; body?: string; audience?: string; createdAt?: string }[]>(`${v1}/announcements`),
  create: (body: Record<string, unknown>) => api.post<unknown>(`${v1}/announcements`, body),
};

export const calendarApi = {
  list: (from?: string, to?: string) => {
    const p = new URLSearchParams();
    if (from) p.set("from", from);
    if (to) p.set("to", to);
    const q = p.toString();
    return api.get<Record<string, unknown>[]>(`${v1}/calendar/events${q ? `?${q}` : ""}`);
  },
};

export const settingsApi = {
  get: () => api.get<SchoolSettings>(`${v1}/school-settings`),
  save: (body: SchoolSettings) => api.put<SchoolSettings>(`${v1}/school-settings`, body),
};

export const fileApi = {
  upload: (file: { uri: string; name: string; type: string }) =>
    api.upload<{ url: string; id?: string }>(`${v1}/files`, file),
};

export const notificationApi = {
  list: () => api.get<Page<Notification>>(`${v1}/notifications?size=30`),
  unread: () => api.get<{ unread: number }>(`${v1}/notifications/unread-count`),
  read: (id: string) => api.post<Notification>(`${v1}/notifications/${id}/read`),
};

export const reportApi = {
  schoolDashboard: () => api.get<Record<string, number>>(`${v1}/dashboard/school`),
  platformDashboard: () => api.get<Record<string, number>>(`${v1}/dashboard/platform`),
  myProgress: () => api.get<Record<string, unknown>>(`${v1}/reports/progress/me`),
  teacherProgress: () => api.get<Record<string, unknown>>(`${v1}/reports/progress/teacher`),
  childrenProgress: () => api.get<Record<string, Record<string, unknown>>>(`${v1}/reports/progress/children`),
  feeCollection: (month: string) => api.get<Record<string, unknown>>(`${v1}/reports/fees/collection?month=${month}`),
};

export const admissionApi = {
  applications: (page = 0) => api.get<Page<Record<string, unknown>>>(`${v1}/admissions/applications?page=${page}&size=20`),
  application: (id: string) => api.get<Record<string, unknown>>(`${v1}/admissions/applications/${id}`),
  setStatus: (id: string, status: string) =>
    api.put<Record<string, unknown>>(`${v1}/admissions/applications/${id}/status`, { status }),
};

export const healthApi = {
  ping: () => api.get<{ status: string }>(`${v1}/health`),
};
