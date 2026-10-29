import { useEffect, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useBranding } from "../lib/branding";
import RotatingLine from "../ui/RotatingLine";

const roles: { to: string; title: string; hint: string; icon: ReactNode }[] = [
  { to: "/login/admin", title: "School Admin", hint: "Users, campuses, website and billing", icon: <IconAdmin /> },
  { to: "/login/principal", title: "Principal", hint: "School-wide academics, staff and reports", icon: <IconPrincipal /> },
  { to: "/login/teacher", title: "Teacher", hint: "Lectures, attendance, homework and exams", icon: <IconTeacher /> },
  { to: "/login/student", title: "Student", hint: "Timetable, homework, online exams and results", icon: <IconStudent /> },
  { to: "/login/parent", title: "Parent", hint: "Children, attendance, results and challans", icon: <IconParent /> },
  { to: "/login/account", title: "Account Officer", hint: "Fees, proofs, salaries and collection", icon: <IconAccount /> },
];

export default function PortalPage() {
  const { school, campus } = useBranding();
  useEffect(() => {
    void import("./LoginPage");
  }, []);
  const name = school?.name || "School";
  const letter = name.trim().charAt(0).toUpperCase() || "S";
  return (
    <div className="site portal-page">
      <header className="site-nav portal-nav">
        <Link to="/" className="site-brand">
          {school?.logoUrl ? <img src={school.logoUrl} alt="" /> : <div className="mark">{letter}</div>}
          <div>
            <strong>{name} Portal</strong>
            {campus ? <div className="hint">{campus.name}</div> : null}
          </div>
        </Link>
        <Link to="/" className="back-link">← Back to website</Link>
      </header>
      <section className="portal-hero">
        <p className="kicker">All logins</p>
        <h1>{name} Portal</h1>
        <RotatingLine />
        <p className="hint">Choose your role. Each card opens that portal only.</p>
      </section>
      <div className="role-grid portal-grid">
        {roles.map((r, i) => (
          <Link key={r.to} to={r.to} className="role-card" style={{ animationDelay: `${i * 50}ms` }}>
            <span className="role-icon" aria-hidden>{r.icon}</span>
            <div>
              <strong>{r.title}</strong>
              <small>{r.hint}</small>
            </div>
          </Link>
        ))}
        <Link to="/admin" className="role-card wide" style={{ animationDelay: "320ms" }}>
          <span className="role-icon" aria-hidden><IconOwner /></span>
          <div>
            <strong>ERP Owner</strong>
            <small>Platform console — schools and subscriptions</small>
          </div>
        </Link>
      </div>
    </div>
  );
}

function Ico({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden>
      <path fill="currentColor" d={d} />
    </svg>
  );
}
function IconAdmin() {
  return <Ico d="M4 18V8l8-4 8 4v10h-5v-6H9v6z" />;
}
function IconPrincipal() {
  return <Ico d="M12 3 4 7v2c0 5 3.4 9.4 8 10.7C16.6 18.4 20 14 20 9V7l-8-4zm0 6a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z" />;
}
function IconTeacher() {
  return <Ico d="M5 5h14v2H5zm0 4h9v2H5zm0 4h14v2H5zm0 4h9v2H5z" />;
}
function IconStudent() {
  return <Ico d="M12 4 2 9l10 5 8-4v6h2V9L12 4zm-6 9.2v3.3L12 20l6-3.5v-3.3L12 16z" />;
}
function IconParent() {
  return <Ico d="M9 11a3 3 0 1 0-3-3 3 3 0 0 0 3 3zm8 0a2.5 2.5 0 1 0-2.5-2.5A2.5 2.5 0 0 0 17 11zM4 20v-1.4A5 5 0 0 1 9 14a5 5 0 0 1 5 4.6V20zm10.2 0v-1.2A6.2 6.2 0 0 0 14 16a4.4 4.4 0 0 1 6 3.4V20z" />;
}
function IconAccount() {
  return <Ico d="M4 6h16v2H4zm0 5h10v2H4zm0 5h16v2H4zm14-6 2 2-5 5-2-2z" />;
}
function IconOwner() {
  return <Ico d="M4 10h16v10H4zm2 2v6h12v-6zM8 4h8l2 4H6z" />;
}
