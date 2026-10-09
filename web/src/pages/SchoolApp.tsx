import { useEffect, useState } from "react";
import { NavLink, Navigate, Outlet, useLocation } from "react-router-dom";
import { auditApi, notificationApi } from "../api/services";
import { LookupsProvider, useLookups } from "../lib/lookups";
import { initials } from "../lib/format";
import { useSession } from "../lib/session";
import { applyBrand, applyTheme, readTheme, type Theme } from "../lib/theme";
import { SCHOOL_NAV } from "../lib/nav";
import Locked from "./Locked";
import ChangePassword from "./ChangePassword";
import { Button, ErrorBox } from "../ui/kit";
import ErrorBoundary from "../ui/ErrorBoundary";

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

  useEffect(() => {
    applyBrand(tenant?.primaryColor, tenant?.accentColor);
    const school = tenant?.name?.trim();
    document.title = school || "School ERP";
    return () => {
      applyBrand(undefined, undefined);
      document.title = "School ERP";
    };
  }, [tenant?.primaryColor, tenant?.accentColor, tenant?.name]);

  if (!user) return <Navigate to="/" replace />;
  if (user.role === "ERP_OWNER") return <Navigate to="/admin/app" replace />;
  if (user.mustChangePassword && loc.pathname !== "/app/password") return <ChangePassword />;
  if (locked && loc.pathname !== "/app/billing") return <Locked />;

  const role = user.role;
  const items = SCHOOL_NAV.filter((i) => i.roles.includes(role));
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
          <BrandMark name={tenant?.name || "School"} code={tenant?.code} logoUrl={tenant?.logoUrl} />
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
            <ActivityBar />
            <ErrorBoundary>
              <Outlet />
            </ErrorBoundary>
          </div>
        </div>
      </div>
    </LookupsProvider>
  );
}

function BrandMark({ name, code, logoUrl }: { name: string; code?: string; logoUrl?: string }) {
  const [broken, setBroken] = useState(false);
  const letter = (name || "S").trim().charAt(0).toUpperCase() || "S";
  return (
    <div className="brand-mark" style={{ marginBottom: 16 }}>
      {logoUrl && !broken ? (
        <img className="brand-logo" src={logoUrl} alt="" onError={() => setBroken(true)} />
      ) : (
        <div className="mark">{letter}</div>
      )}
      <div>
        {name}
        {code ? <div style={{ fontSize: 12, opacity: 0.65 }}>{code}</div> : null}
      </div>
    </div>
  );
}

function ActivityBar() {
  const { locked } = useSession();
  const [items, setItems] = useState<{ id: string; action: string; createdAt?: string }[]>([]);
  useEffect(() => {
    if (locked) return;
    void auditApi.mine().then((page) => setItems(page.content || [])).catch(() => undefined);
  }, [locked]);
  if (!items.length) return null;
  return (
    <div className="activity-bar" aria-label="Recent activity">
      {items.slice(0, 8).map((e) => (
        <span key={e.id} className="activity-chip">
          {e.action.replaceAll("_", " ")}
          {e.createdAt ? ` · ${new Date(e.createdAt).toLocaleString()}` : ""}
        </span>
      ))}
    </div>
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
