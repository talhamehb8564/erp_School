import { useEffect, useState } from "react";
import { NavLink, Navigate, Outlet, useLocation } from "react-router-dom";
import { notificationApi } from "../api/services";
import { LookupsProvider } from "../lib/lookups";
import { initials } from "../lib/format";
import { useSession } from "../lib/session";
import { applyTheme, readTheme, type Theme } from "../lib/theme";
import type { Role } from "../lib/types";
import Locked from "./Locked";
import ChangePassword from "./ChangePassword";

interface Item {
  to: string;
  label: string;
  roles: Role[];
}

const NAV: Item[] = [
  { to: "/app", label: "Dashboard", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER", "PARENT", "STUDENT"] },
  { to: "/app/students", label: "Students", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER"] },
  { to: "/app/users", label: "Users", roles: ["SCHOOL_ADMIN", "PRINCIPAL"] },
  { to: "/app/campuses", label: "Campuses", roles: ["SCHOOL_ADMIN"] },
  { to: "/app/academics", label: "Academics", roles: ["SCHOOL_ADMIN", "PRINCIPAL"] },
  { to: "/app/timetable", label: "Timetable", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT", "PARENT"] },
  { to: "/app/attendance", label: "Attendance", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"] },
  { to: "/app/homework", label: "Homework", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"] },
  { to: "/app/exams", label: "Marks & results", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT"] },
  { to: "/app/fees", label: "Fees", roles: ["SCHOOL_ADMIN", "ACCOUNT_OFFICER", "PRINCIPAL", "PARENT", "STUDENT"] },
  { to: "/app/salaries", label: "Salaries", roles: ["SCHOOL_ADMIN", "ACCOUNT_OFFICER", "PRINCIPAL", "TEACHER"] },
  { to: "/app/announcements", label: "Announcements", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT", "ACCOUNT_OFFICER"] },
  { to: "/app/calendar", label: "Calendar", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "PARENT", "STUDENT", "ACCOUNT_OFFICER"] },
  { to: "/app/reports", label: "Reports", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "ACCOUNT_OFFICER"] },
  { to: "/app/settings", label: "Settings", roles: ["SCHOOL_ADMIN", "ACCOUNT_OFFICER"] },
  { to: "/app/billing", label: "Subscription", roles: ["SCHOOL_ADMIN"] },
  { to: "/app/notifications", label: "Notifications", roles: ["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER", "PARENT", "STUDENT"] },
];

export default function SchoolApp() {
  const { user, tenant, locked, logout } = useSession();
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState<Theme>(readTheme());
  const [unread, setUnread] = useState(0);
  const loc = useLocation();

  useEffect(() => {
    if (!user || locked) return;
    void notificationApi.unread().then((r) => setUnread(r.unread)).catch(() => undefined);
  }, [user, locked, loc.pathname]);

  if (!user) return <Navigate to="/" replace />;
  if (user.role === "ERP_OWNER") return <Navigate to="/admin/app" replace />;
  if (user.mustChangePassword && loc.pathname !== "/app/password") return <ChangePassword />;
  if (locked && loc.pathname !== "/app/billing") return <Locked />;

  const role = user.role;
  const items = NAV.filter((i) => i.roles.includes(role));
  const toggleTheme = () => {
    const next = theme === "light" ? "dark" : "light";
    applyTheme(next);
    setTheme(next);
  };

  return (
    <LookupsProvider enabled={!locked}>
      <div className="shell" data-role={role}>
        <aside className={`sidebar ${open ? "open" : ""}`}>
          <div className="brand-mark" style={{ marginBottom: 16 }}>
            <div className="mark">A</div>
            <div>
              {tenant?.name || "Atrium"}
              <div style={{ fontSize: 12, opacity: 0.65 }}>{tenant?.code}</div>
            </div>
          </div>
          {items.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.to === "/app"} className="nav-link" onClick={() => setOpen(false)}>
              {i.label}
              {i.to === "/app/notifications" && unread ? ` (${unread})` : ""}
            </NavLink>
          ))}
          <div style={{ flex: 1 }} />
          <button className="nav-link" style={{ background: "none", border: 0, textAlign: "left" }} onClick={() => void logout()}>
            Sign out
          </button>
        </aside>
        <div className="main">
          <header className="topbar">
            <button className="icon-btn burger" onClick={() => setOpen((v) => !v)}>
              ☰
            </button>
            <div>
              <div className="crumbs">{role.replaceAll("_", " ")}</div>
              <strong>{user.fullName}</strong>
            </div>
            <div className="row">
              <button className="icon-btn" onClick={toggleTheme}>{theme === "dark" ? "☀" : "☾"}</button>
              <div className="avatar">{initials(user.fullName)}</div>
            </div>
          </header>
          <div className="content">
            <Outlet />
          </div>
        </div>
      </div>
    </LookupsProvider>
  );
}
