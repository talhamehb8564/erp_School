import type { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useSession } from "./lib/session";
import { homeFor } from "./lib/routes";
import Welcome from "./pages/Welcome";
import LoginPage from "./pages/LoginPage";
import OwnerApp, { OwnerHome, SchoolDetail, Schools, Subscriptions } from "./pages/OwnerApp";
import SchoolApp from "./pages/SchoolApp";
import ChangePassword from "./pages/ChangePassword";
import Dashboard from "./pages/school/Dashboard";
import { StudentsPage, UsersPage } from "./pages/school/People";
import { AcademicsPage, CampusesPage, TimetablePage } from "./pages/school/Academics";
import { AttendancePage, ExamsPage, HomeworkPage } from "./pages/school/Teaching";
import { BillingPage, FeesPage, SalariesPage, SettingsPage } from "./pages/school/Finance";
import { AnnouncementsPage, CalendarPage, NotificationsPage, ReportsPage } from "./pages/school/Comms";
import { Loading } from "./ui/kit";
import type { Role } from "./lib/types";

function Guard({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading } = useSession();
  if (loading) return <Loading label="Restoring session…" />;
  if (!user) return <Navigate to="/" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to={homeFor(user)} replace />;
  return <>{children}</>;
}

function PublicOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useSession();
  if (loading) return <Loading />;
  if (user) return <Navigate to={homeFor(user)} replace />;
  return <>{children}</>;
}

function RoleGate({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useSession();
  if (!user || !roles.includes(user.role)) {
    return <Navigate to={user ? homeFor(user) : "/"} replace />;
  }
  return <>{children}</>;
}

function HomeRedirect() {
  const { user, loading } = useSession();
  if (loading) return <Loading />;
  if (!user) return <Navigate to="/" replace />;
  return <Navigate to={homeFor(user)} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<PublicOnly><Welcome /></PublicOnly>} />
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
        <Route path="students" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER"]}><StudentsPage /></RoleGate>} />
        <Route path="users" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL"]}><UsersPage /></RoleGate>} />
        <Route path="campuses" element={<RoleGate roles={["SCHOOL_ADMIN"]}><CampusesPage /></RoleGate>} />
        <Route path="academics" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL"]}><AcademicsPage /></RoleGate>} />
        <Route path="timetable" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT", "PARENT"]}><TimetablePage /></RoleGate>} />
        <Route path="attendance" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"]}><AttendancePage /></RoleGate>} />
        <Route path="homework" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"]}><HomeworkPage /></RoleGate>} />
        <Route path="exams" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"]}><ExamsPage /></RoleGate>} />
        <Route path="fees" element={<RoleGate roles={["ACCOUNT_OFFICER", "PRINCIPAL", "PARENT", "STUDENT"]}><FeesPage /></RoleGate>} />
        <Route path="salaries" element={<RoleGate roles={["SCHOOL_ADMIN", "ACCOUNT_OFFICER", "PRINCIPAL", "TEACHER"]}><SalariesPage /></RoleGate>} />
        <Route path="announcements" element={<AnnouncementsPage />} />
        <Route path="calendar" element={<CalendarPage />} />
        <Route path="reports" element={<RoleGate roles={["SCHOOL_ADMIN", "PRINCIPAL", "ACCOUNT_OFFICER"]}><ReportsPage /></RoleGate>} />
        <Route path="settings" element={<RoleGate roles={["SCHOOL_ADMIN", "ACCOUNT_OFFICER"]}><SettingsPage /></RoleGate>} />
        <Route path="billing" element={<RoleGate roles={["SCHOOL_ADMIN"]}><BillingPage /></RoleGate>} />
        <Route path="notifications" element={<NotificationsPage />} />
      </Route>
      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
