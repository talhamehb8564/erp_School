import { academicApi, examApi, feeApi, homeworkApi, reportApi, studentApi } from "../api/services";
import type { Role } from "./types";

/** Start the first dashboard GET while the portal chunk is still loading. */
export function prefetchPortal(role: Role) {
  if (role === "ERP_OWNER") {
    void reportApi.platformDashboard().catch(() => undefined);
    return;
  }
  if (role === "TEACHER") {
    void academicApi.teacherToday().catch(() => undefined);
    void homeworkApi.list(undefined, undefined, 8).catch(() => undefined);
    void reportApi.teacherProgress().catch(() => undefined);
    void examApi.sessions().catch(() => undefined);
    return;
  }
  if (role === "STUDENT") {
    void reportApi.myProgress().catch(() => undefined);
    void homeworkApi.list(undefined, undefined, 8).catch(() => undefined);
    void feeApi.myChallanSummaries(20).catch(() => undefined);
    void examApi.sessions().catch(() => undefined);
    return;
  }
  if (role === "PARENT") {
    void studentApi.children().catch(() => undefined);
    void reportApi.childrenProgress().catch(() => undefined);
    void feeApi.parentChallanSummaries(20).catch(() => undefined);
    void examApi.sessions().catch(() => undefined);
    return;
  }
  if (role === "ACCOUNT_OFFICER") {
    void reportApi.schoolDashboard().catch(() => undefined);
    void feeApi.pendingProofs(12).catch(() => undefined);
    return;
  }
  void reportApi.schoolDashboard().catch(() => undefined);
}
