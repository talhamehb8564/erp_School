import { lazy, Suspense, type ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useSession } from "./lib/session";
import { useFeatures, type FeatureKey } from "./lib/features";
import { homeFor, LOGIN_PORTALS } from "./lib/routes";
import { Loading } from "./ui/kit";
import ErrorBoundary from "./ui/ErrorBoundary";
import type { Role } from "./lib/types";

const Welcome = lazy(() => import("./pages/Welcome"));
const LoginPage = lazy(() => import("./pages/LoginPage"));
const PortalPage = lazy(() => import("./pages/PortalPage"));
const OwnerApp = lazy(() => import("./pages/OwnerApp"));
const OwnerHome = lazy(() => import("./pages/OwnerApp").then((m) => ({ default: m.OwnerHome })));
const Schools = lazy(() => import("./pages/OwnerApp").then((m) => ({ default: m.Schools })));
const SchoolDetail = lazy(() => import("./pages/OwnerApp").then((m) => ({ default: m.SchoolDetail })));
const Subscriptions = lazy(() => import("./pages/OwnerApp").then((m) => ({ default: m.Subscriptions })));
const SchoolApp = lazy(() => import("./pages/SchoolApp"));
const ChangePassword = lazy(() => import("./pages/ChangePassword"));

const Dashboard = lazy(() => import("./pages/school/Dashboard"));
const StudentsPage = lazy(() => import("./pages/school/People").then((m) => ({ default: m.StudentsPage })));
const UsersPage = lazy(() => import("./pages/school/People").then((m) => ({ default: m.UsersPage })));
const AcademicsPage = lazy(() => import("./pages/school/Academics").then((m) => ({ default: m.AcademicsPage })));
const CampusesPage = lazy(() => import("./pages/school/Academics").then((m) => ({ default: m.CampusesPage })));
const TimetablePage = lazy(() => import("./pages/school/Academics").then((m) => ({ default: m.TimetablePage })));
const AttendancePage = lazy(() => import("./pages/school/Teaching").then((m) => ({ default: m.AttendancePage })));
const TeacherAttendancePage = lazy(() => import("./pages/school/TeacherAttendance"));
const AdmissionsPage = lazy(() => import("./pages/school/Admissions"));
const ApplyAdmission = lazy(() => import("./pages/ApplyAdmission"));
const HomeworkPage = lazy(() => import("./pages/school/Teaching").then((m) => ({ default: m.HomeworkPage })));
const ExamsPage = lazy(() => import("./pages/school/Teaching").then((m) => ({ default: m.ExamsPage })));
const OnlineExamsPage = lazy(() => import("./pages/school/OnlineExams"));
const WebsitePage = lazy(() => import("./pages/school/Website"));
const FeesPage = lazy(() => import("./pages/school/Finance").then((m) => ({ default: m.FeesPage })));
const SalariesPage = lazy(() => import("./pages/school/Finance").then((m) => ({ default: m.SalariesPage })));
const SettingsPage = lazy(() => import("./pages/school/Finance").then((m) => ({ default: m.SettingsPage })));
const BillingPage = lazy(() => import("./pages/school/Finance").then((m) => ({ default: m.BillingPage })));
const AnnouncementsPage = lazy(() => import("./pages/school/Comms").then((m) => ({ default: m.AnnouncementsPage })));
const CalendarPage = lazy(() => import("./pages/school/Comms").then((m) => ({ default: m.CalendarPage })));
const NotificationsPage = lazy(() => import("./pages/school/Comms").then((m) => ({ default: m.NotificationsPage })));
const ReportsPage = lazy(() => import("./pages/school/Comms").then((m) => ({ default: m.ReportsPage })));

function Guard({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading } = useSession();
  if (!user && loading) return <Loading label="Restoring session…" />;
  if (!user) return <Navigate to={LOGIN_PORTALS} replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to={homeFor(user)} replace />;
  return <>{children}</>;
}

function PublicOnly({ children }: { children: ReactNode }) {
  const { user } = useSession();
  if (user) return <Navigate to={homeFor(user)} replace />;
  return <>{children}</>;
}

function RoleGate({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useSession();
  if (!user || !roles.includes(user.role)) {
    return <Navigate to={user ? homeFor(user) : LOGIN_PORTALS} replace />;
  }
  return <>{children}</>;
}

function FeatureGate({ feature, children }: { feature: FeatureKey; children: ReactNode }) {
  const { user } = useSession();
  const { on, loaded } = useFeatures();
  if (!loaded) return <>{children}</>;
  if (!on(feature)) return <Navigate to={user ? homeFor(user) : LOGIN_PORTALS} replace />;
  return <>{children}</>;
}

function HomeRedirect() {
  const { user, loading } = useSession();
  if (user) return <Navigate to={homeFor(user)} replace />;
  if (loading) return <Loading />;
  return <Navigate to={LOGIN_PORTALS} replace />;
}

export default function App() {
  return (
    <ErrorBoundary>
      <Suspense fallback={<Loading label="Loading…" />}>
        <Routes>
          <Route path="/" element={<PublicOnly><Welcome /></PublicOnly>} />
          <Route path="/apply" element={<PublicOnly><ApplyAdmission /></PublicOnly>} />
          <Route path="/portals" element={<PublicOnly><PortalPage /></PublicOnly>} />
          <Route path="/login/:role" element={<PublicOnly><LoginPage /></PublicOnly>} />
          <Route path="/admin" element={<PublicOnly><LoginPage owner /></PublicOnly>} />
          <Route path="/dashboard" element={<HomeRedirect />} />
          <Route path="/teacher" element={<HomeRedirect />} />
          <Route path="/principal" element={<HomeRedirect />} />
          <Route path="/student" element={<HomeRedirect />} />
          <Route path="/parent" element={<HomeRedirect />} />
          <Route path="/account" element={<HomeRedirect />} />
          <Route
            path="/admin/app"
            element={
              <Guard roles={["ERP_OWNER"]}>
                <OwnerApp />
              </Guard>
            }
          >
            <Route index element={<OwnerHome />} />
            <Route path="schools" element={<Schools />} />
            <Route path="schools/:id" element={<SchoolDetail />} />
            <Route path="subscriptions" element={<Subscriptions />} />
            <Route path="password" element={<ChangePassword />} />
          </Route>
          <Route
            path="/app"
            element={
              <Guard>
                <SchoolApp />
              </Guard>
            }
          >
            <Route index element={<Dashboard />} />
            <Route path="password" element={<ChangePassword />} />
            <Route path="students" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER"]}><FeatureGate feature="STUDENTS"><StudentsPage /></FeatureGate></RoleGate>} />
            <Route path="users" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL"]}><UsersPage /></RoleGate>} />
            <Route path="campuses" element={<RoleGate roles={["SCHOOL_ADMIN"]}><CampusesPage /></RoleGate>} />
            <Route path="academics" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL"]}><AcademicsPage /></RoleGate>} />
            <Route path="timetable" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT", "PARENT"]}><FeatureGate feature="TIMETABLE"><TimetablePage /></FeatureGate></RoleGate>} />
            <Route path="attendance" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"]}><FeatureGate feature="ATTENDANCE"><AttendancePage /></FeatureGate></RoleGate>} />
            <Route path="teacher-attendance" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL"]}><FeatureGate feature="TEACHER_ATTENDANCE"><TeacherAttendancePage /></FeatureGate></RoleGate>} />
            <Route path="homework" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"]}><FeatureGate feature="HOMEWORK"><HomeworkPage /></FeatureGate></RoleGate>} />
            <Route path="exams" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"]}><FeatureGate feature="RESULTS"><ExamsPage /></FeatureGate></RoleGate>} />
            <Route path="online-exams" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT"]}><FeatureGate feature="ONLINE_EXAMS"><OnlineExamsPage /></FeatureGate></RoleGate>} />
            <Route path="online-exams/:examId" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT"]}><FeatureGate feature="ONLINE_EXAMS"><OnlineExamsPage /></FeatureGate></RoleGate>} />
            <Route path="website" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL"]}><FeatureGate feature="WEBSITE"><WebsitePage /></FeatureGate></RoleGate>} />
            <Route path="admissions" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL"]}><FeatureGate feature="ADMISSIONS"><AdmissionsPage /></FeatureGate></RoleGate>} />
            <Route path="fees" element={<RoleGate roles={["ACCOUNT_OFFICER", "PRINCIPAL", "PARENT", "STUDENT"]}><FeatureGate feature="FEES"><FeesPage /></FeatureGate></RoleGate>} />
            <Route path="salaries" element={<RoleGate roles={["SCHOOL_ADMIN", "ACCOUNT_OFFICER", "PRINCIPAL", "TEACHER"]}><FeatureGate feature="SALARIES"><SalariesPage /></FeatureGate></RoleGate>} />
            <Route path="announcements" element={<FeatureGate feature="ANNOUNCEMENTS"><AnnouncementsPage /></FeatureGate>} />
            <Route path="calendar" element={<FeatureGate feature="CALENDAR"><CalendarPage /></FeatureGate>} />
            <Route path="reports" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "ACCOUNT_OFFICER"]}><ReportsPage /></RoleGate>} />
            <Route path="settings" element={<RoleGate roles={["SCHOOL_ADMIN", "ACCOUNT_OFFICER"]}><SettingsPage /></RoleGate>} />
            <Route path="billing" element={<RoleGate roles={["SCHOOL_ADMIN"]}><BillingPage /></RoleGate>} />
            <Route path="notifications" element={<FeatureGate feature="NOTIFICATIONS"><NotificationsPage /></FeatureGate>} />
          </Route>
          <Route path="*" element={<HomeRedirect />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}
