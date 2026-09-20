import { useEffect, useState } from "react";
import { NavLink, Navigate, Outlet, useLocation } from "react-router-dom";
import { notificationApi } from "../api/services";
import { LookupsProvider, useLookups } from "../lib/lookups";
import { initials } from "../lib/format";
import { useSession } from "../lib/session";
import { applyTheme, readTheme, type Theme } from "../lib/theme";
import { SCHOOL_NAV } from "../lib/nav";
import Locked from "./Locked";
import ChangePassword from "./ChangePassword";
import { Button, ErrorBox } from "../ui/kit";

export default function SchoolApp() {
  const { user, tenant, locked, logout } = useSession();
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState<Theme>(readTheme());
  const [unread, setUnread] = useState(0);
  const loc = useLocation();

  useEffect(() => {
    if (!user || locked) return;
    void notificationApi.unread().then((r) => setUnread(r.unread)).catch(() => undefined);
  }, [user, locked]);

  if (!user) return <Navigate to="/" replace />;
  if (user.role === "ERP_OWNER") return <Navigate to="/admin/app" replace />;
  if (user.mustChangePassword && loc.pathname !== "/app/password") return <ChangePassword />;
  if (locked && loc.pathname !== "/app/billing") return <Locked />;

  const role = user.role;
  const items = SCHOOL_NAV.filter((i) => i.to !== "/app/password" && i.roles.includes(role));
  const toggleTheme = () => {
    const next = theme === "light" ? "dark" : "light";
    applyTheme(next);
    setTheme(next);
  };

  return (
    <LookupsProvider enabled={!locked}>
      <div className="shell" data-role={role}>
        {open ? <button type="button" className="scrim" aria-label="Close menu" onClick={() => setOpen(false)} /> : null}
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
          <Button kind="ghost" loadingText="Signing out…" onClick={() => logout()}>
            Sign out
          </Button>
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
            <LookupsAlert />
            <Outlet />
          </div>
        </div>
      </div>
    </LookupsProvider>
  );
}

function LookupsAlert() {
  const { error, reload, classes, subjects } = useLookups();
  if (!error || classes.length || subjects.length) return null;
  return (
    <div style={{ marginBottom: 16 }}>
      <ErrorBox error={error} onRetry={() => void reload()} title="Unable to load classes and subjects" />
    </div>
  );
}
