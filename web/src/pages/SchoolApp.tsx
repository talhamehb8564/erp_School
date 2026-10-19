import { Suspense, useEffect, useState } from "react";
import { NavLink, Navigate, Outlet, useLocation } from "react-router-dom";
import { auditApi, notificationApi } from "../api/services";
import { LookupsProvider, lookupsNeeded, useLookups } from "../lib/lookups";
import { LOGIN_PORTALS } from "../lib/routes";
import { useSession } from "../lib/session";
import { applyBrand, applyTheme, readTheme, type Theme } from "../lib/theme";
import { useBranding } from "../lib/branding";
import { PATH_FEATURE, useFeatures } from "../lib/features";
import { useSidebarWidth } from "../lib/sidebar";
import { SCHOOL_NAV } from "../lib/nav";
import Locked from "./Locked";
import ChangePassword from "./ChangePassword";
import { Button, ErrorBox, Loading } from "../ui/kit";
import UserMenu from "../ui/UserMenu";
import ErrorBoundary from "../ui/ErrorBoundary";

const NAV_GROUPS: { label: string; tos: string[] }[] = [
  { label: "Home", tos: ["/app", "/app/notifications", "/app/password"] },
  { label: "People", tos: ["/app/students", "/app/users", "/app/campuses"] },
  { label: "Academics", tos: ["/app/academics", "/app/timetable", "/app/attendance", "/app/teacher-attendance", "/app/homework", "/app/exams", "/app/online-exams"] },
  { label: "Finance", tos: ["/app/fees", "/app/salaries", "/app/reports", "/app/billing"] },
  { label: "School", tos: ["/app/website", "/app/admissions", "/app/announcements", "/app/calendar", "/app/settings"] },
];

export default function SchoolApp() {
  const { user, tenant, locked, logout } = useSession();
  const { school } = useBranding();
  const { on } = useFeatures();
  const { onResizePointerDown } = useSidebarWidth();
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState<Theme>(readTheme());
  const [unread, setUnread] = useState(0);
  const loc = useLocation();

  useEffect(() => { setOpen(false); }, [loc.pathname]);

  useEffect(() => {
    if (!user || locked) return;
    const run = () => {
      void notificationApi.unread().then((r) => setUnread(r.unread)).catch(() => undefined);
    };
    const idle = (window as Window & { requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number }).requestIdleCallback;
    if (typeof idle === "function") {
      const id = idle(run, { timeout: 400 });
      const cancel = (window as Window & { cancelIdleCallback?: (id: number) => void }).cancelIdleCallback;
      return () => cancel?.(id);
    }
    const t = window.setTimeout(run, 0);
    return () => window.clearTimeout(t);
  }, [user, locked]);

  useEffect(() => {
    const primary = tenant?.primaryColor || school?.primaryColor;
    const accent = tenant?.accentColor || school?.accentColor;
    const secondary = tenant?.secondaryColor || school?.secondaryColor;
    if (primary || accent || secondary) applyBrand(primary, accent, secondary);
    const title = tenant?.name?.trim() || school?.name;
    if (title) document.title = title;
  }, [tenant?.primaryColor, tenant?.accentColor, tenant?.secondaryColor, tenant?.name, school?.primaryColor, school?.accentColor, school?.secondaryColor, school?.name]);

  if (!user) return <Navigate to={LOGIN_PORTALS} replace />;
  if (user.role === "ERP_OWNER") return <Navigate to="/admin/app" replace />;
  if (user.mustChangePassword && loc.pathname !== "/app/password") return <ChangePassword />;
  if (locked && loc.pathname !== "/app/billing") return <Locked />;

  const role = user.role;
  const items = SCHOOL_NAV.filter((i) => i.roles.includes(role) && on(PATH_FEATURE[i.to]));
  const current = items.find((i) => i.to === loc.pathname)
    || items.filter((i) => i.to !== "/app" && loc.pathname.startsWith(i.to)).sort((a, b) => b.to.length - a.to.length)[0];
  const toggleTheme = () => {
    const next = theme === "light" ? "dark" : "light";
    applyTheme(next);
    setTheme(next);
  };

  return (
    <LookupsProvider
      enabled={!locked && lookupsNeeded(loc.pathname) && role !== "STUDENT" && role !== "PARENT"}
      prefetch={false}
    >
      <div className="shell" data-role={role}>
        {open ? <button type="button" className="scrim" aria-label="Close menu" onClick={() => setOpen(false)} /> : null}
        <aside className={`sidebar ${open ? "open" : ""}`}>
          <button type="button" className="sidebar-resizer" aria-label="Resize sidebar" onPointerDown={onResizePointerDown} />
          <BrandMark name={tenant?.name || school?.name || "School"} code={tenant?.code || school?.code} logoUrl={tenant?.logoUrl || school?.logoUrl} />
          {NAV_GROUPS.map((g) => {
            const shown = items.filter((i) => g.tos.includes(i.to));
            if (!shown.length) return null;
            return (
              <div key={g.label} className="nav-group">
                <div className="nav-sec">{g.label}</div>
                {shown.map((i) => (
                  <NavLink key={i.to} to={i.to} end={i.to === "/app"} className="nav-link" onClick={() => setOpen(false)}>
                    {i.label}
                    {i.to === "/app/notifications" && unread ? ` (${unread})` : ""}
                  </NavLink>
                ))}
              </div>
            );
          })}
          <div className="sidebar-foot">
            <Button kind="ghost" loadingText="Signing out…" onClick={() => logout()}>
              Sign out
            </Button>
          </div>
        </aside>
        <div className="main">
          <header className="topbar">
            <button type="button" className="icon-btn burger" aria-label="Open menu" onClick={() => setOpen((v) => !v)}>
              ☰
            </button>
            <div className="topbar-copy">
              <div className="crumbs">{role.replaceAll("_", " ")}{current ? ` · ${current.label}` : ""}</div>
              <strong>{user.fullName}</strong>
            </div>
            <div className="row">
              <button type="button" className="icon-btn" onClick={toggleTheme} title="Theme">{theme === "dark" ? "☀" : "☾"}</button>
              <UserMenu passwordTo="/app/password" />
            </div>
          </header>
          <div className="content">
            <LookupsAlert />
            <ActivityBar enabled={loc.pathname === "/app" && (role === "SCHOOL_ADMIN" || role === "PRINCIPAL")} />
            <ErrorBoundary>
              <Suspense fallback={<Loading label="Loading…" />}>
                <Outlet />
              </Suspense>
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
    <div className="brand-mark">
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

function ActivityBar({ enabled }: { enabled: boolean }) {
  const { locked } = useSession();
  const [items, setItems] = useState<{ id: string; action: string; createdAt?: string }[]>([]);
  useEffect(() => {
    if (!enabled || locked) return;
    const run = () => {
      void auditApi.mine().then((page) => setItems(page.content || [])).catch(() => undefined);
    };
    const idle = (window as Window & { requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number }).requestIdleCallback;
    if (typeof idle === "function") {
      const id = idle(run, { timeout: 400 });
      const cancel = (window as Window & { cancelIdleCallback?: (id: number) => void }).cancelIdleCallback;
      return () => cancel?.(id);
    }
    const t = window.setTimeout(run, 0);
    return () => window.clearTimeout(t);
  }, [enabled, locked]);
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
