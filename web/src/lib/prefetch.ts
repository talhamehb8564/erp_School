import { academicApi, attendanceApi, examApi, feeApi, homeworkApi, reportApi, studentApi } from "../api/services";
import type { Role } from "./types";

/** Start only the first-screen GET while the portal chunk is still loading. */
export function prefetchPortal(role: Role) {
  if (role === "ERP_OWNER") {
    void reportApi.platformDashboard().catch(() => undefined);
    return;
  }
  if (role === "TEACHER") {
    void academicApi.teacherToday().catch(() => undefined);
    void reportApi.teacherProgress().catch(() => undefined);
    return;
  }
  if (role === "STUDENT") {
    void reportApi.myProgress().catch(() => undefined);
    return;
  }
  if (role === "PARENT") {
    void studentApi.children().catch(() => undefined);
    void reportApi.childrenProgress().catch(() => undefined);
    return;
  }
  if (role === "ACCOUNT_OFFICER") {
    void reportApi.schoolDashboard().catch(() => undefined);
    return;
  }
  void reportApi.schoolDashboard().catch(() => undefined);
}

function lastDaysRange() {
  const from = new Date();
  from.setDate(from.getDate() - 30);
  return { from: from.toISOString().slice(0, 10), to: new Date().toISOString().slice(0, 10) };
}

/** Warm the clicked module after the dashboard is already on screen. */
export function prefetchRoute(path: string, role?: Role) {
  if (path === "/app/homework") {
    void homeworkApi.list(undefined, undefined, 40).catch(() => undefined);
    return;
  }
  if (path === "/app/exams") {
    void examApi.sessions().catch(() => undefined);
    return;
  }
  if (path === "/app/fees") {
    if (role === "STUDENT") void feeApi.myChallanSummaries(24).catch(() => undefined);
    else if (role === "PARENT") void feeApi.parentChallanSummaries(20).catch(() => undefined);
    return;
  }
  if (path === "/app/attendance" && role === "STUDENT") {
    const { from, to } = lastDaysRange();
    void attendanceApi.mine(from, to).catch(() => undefined);
  }
}
