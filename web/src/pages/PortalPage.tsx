import { Link } from "react-router-dom";
import { useBranding } from "../lib/branding";
import RotatingLine from "../ui/RotatingLine";

const roles = [
  { to: "/login/student", title: "Student", hint: "Timetable, homework, online exams and results" },
  { to: "/login/parent", title: "Parent", hint: "Children, attendance, results and challans" },
  { to: "/login/teacher", title: "Teacher", hint: "Lectures, attendance, homework and exams" },
  { to: "/login/principal", title: "Principal", hint: "School-wide academics, staff and reports" },
  { to: "/login/account", title: "Account Officer", hint: "Fees, proofs, salaries and collection" },
  { to: "/login/admin", title: "School Admin", hint: "Users, campuses, website and billing" },
];

export default function PortalPage() {
  const { school, campus } = useBranding();
  const name = school?.name || "School";
  const letter = name.trim().charAt(0).toUpperCase() || "S";
  return (
    <div className="site portal-page">
      <header className="site-nav">
        <Link to="/" className="site-brand">
          {school?.logoUrl ? <img src={school.logoUrl} alt="" /> : <div className="mark">{letter}</div>}
          <div>
            <strong>{name} Portal</strong>
            {campus ? <div className="hint">{campus.name}</div> : null}
          </div>
        </Link>
        <Link to="/" className="btn ghost">Back to website</Link>
      </header>
      <section className="portal-hero">
        <p className="kicker">Sign in</p>
        <h1>{name} Portal</h1>
        <RotatingLine />
        <p className="hint">Choose your role. School name and colours come from the public school profile.</p>
      </section>
      <div className="role-grid portal-grid">
        {roles.map((r) => (
          <Link key={r.to} to={r.to} className="role-card">
            <strong>{r.title}</strong>
            <small>{r.hint}</small>
          </Link>
        ))}
        <Link to="/admin" className="role-card wide">
          <strong>ERP Owner</strong>
          <small>Platform console — schools and subscriptions</small>
        </Link>
      </div>
    </div>
  );
}
