import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { healthApi } from "../api/services";

const roles = [
  { to: "/login/teacher", title: "Teacher", hint: "Lectures, attendance, homework and marks" },
  { to: "/login/principal", title: "Principal", hint: "School-wide academics, staff and reports" },
  { to: "/login/student", title: "Student", hint: "Timetable, homework, results and fees" },
  { to: "/login/parent", title: "Parent", hint: "Children, attendance, results and challans" },
  { to: "/login/account", title: "Account Officer", hint: "Fees, proofs, salaries and collection" },
  { to: "/login/admin", title: "School Admin", hint: "Users, campuses, classes and billing" },
];

export default function Welcome() {
  const [apiOk, setApiOk] = useState<boolean | null>(null);
  const [apiMsg, setApiMsg] = useState("");
  useEffect(() => {
    void healthApi
      .ping()
      .then(() => setApiOk(true))
      .catch((e) => {
        setApiOk(false);
        setApiMsg(e instanceof Error ? e.message : "API unreachable");
      });
  }, []);
  return (
    <div className="welcome">
      <section className="welcome-art">
        <div className="brand-mark">
          <div className="mark">A</div>
          <div>
            Atrium ERP
            <div style={{ opacity: 0.7, fontSize: 13, fontWeight: 460 }}>School operating system</div>
          </div>
        </div>
        <div>
          <div className="kicker" style={{ color: "#e8c39e" }}>Multi-tenant · JWT · Neon PostgreSQL</div>
          <h1>Welcome to School ERP</h1>
          <p>
            A premium workspace for every role in the school — from the classroom to the accounts office —
            connected to the live Spring Boot backend, not a demo catalogue.
          </p>
        </div>
        <p style={{ opacity: 0.7 }}>Lahore · Asia/Karachi · /api/v1</p>
      </section>
      <section className="welcome-panel">
        <div className="kicker">Choose your portal</div>
        <h2 className="serif" style={{ fontSize: 36, margin: "10px 0 8px" }}>Sign in with your real account</h2>
        <p className="hint">Each card uses the same backend authentication. You will land on the dashboard for your role.</p>
        {apiOk === false ? (
          <div className="error-box" style={{ textAlign: "left", marginBottom: 16 }}>
            Spring Boot API is not reachable at /api/v1. {apiMsg}
          </div>
        ) : null}
        <div className="role-grid">
          {roles.map((r) => (
            <Link key={r.to} to={r.to} className="role-card">
              <strong>{r.title}</strong>
              <small>{r.hint}</small>
            </Link>
          ))}
          <Link to="/admin" className="role-card wide">
            <strong>ERP Owner / Super Admin</strong>
            <small>Platform console at /admin — schools, subscriptions, payment proofs</small>
          </Link>
        </div>
      </section>
    </div>
  );
}
