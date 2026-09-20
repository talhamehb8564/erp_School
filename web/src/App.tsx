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
import type { Role } from "./lib/types";

function Guard({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading } = useSession();
  if (loading) return <div className="loading">Restoring session…</div>;
  if (!user) return <Navigate to="/" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to={homeFor(user)} replace />;
  return <>{children}</>;
}

function PublicOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useSession();
  if (loading) return <div className="loading">Loading…</div>;
  if (user) return <Navigate to={homeFor(user)} replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<PublicOnly><Welcome /></PublicOnly>} />
      <Route path="/login/:role" element={<PublicOnly><LoginPage /></PublicOnly>} />
      <Route path="/admin" element={<PublicOnly><LoginPage owner /></PublicOnly>} />
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
        <Route path="students" element={<StudentsPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="campuses" element={<CampusesPage />} />
        <Route path="academics" element={<AcademicsPage />} />
        <Route path="timetable" element={<TimetablePage />} />
        <Route path="attendance" element={<AttendancePage />} />
        <Route path="homework" element={<HomeworkPage />} />
        <Route path="exams" element={<ExamsPage />} />
        <Route path="fees" element={<FeesPage />} />
        <Route path="salaries" element={<SalariesPage />} />
        <Route path="announcements" element={<AnnouncementsPage />} />
        <Route path="calendar" element={<CalendarPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="billing" element={<BillingPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
