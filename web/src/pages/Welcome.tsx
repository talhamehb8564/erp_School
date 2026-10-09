import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { healthApi, setupApi } from "../api/services";
import { Button, Field, Form } from "../ui/kit";
import { useToast } from "../lib/toast";

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
  const [needsSetup, setNeedsSetup] = useState(false);
  const [schoolName, setSchoolName] = useState("");
  const [b1, setB1] = useState("Main Campus");
  const [b2, setB2] = useState("Canal Campus");
  const [b3, setB3] = useState("Cantt Campus");
  const [created, setCreated] = useState<{ username: string; password?: string; message?: string } | null>(null);
  const toast = useToast();

  useEffect(() => {
    void healthApi
      .ping()
      .then(() => {
        setApiOk(true);
        return setupApi.status();
      })
      .then((s) => setNeedsSetup(Boolean(s?.needsSetup)))
      .catch((e) => {
        setApiOk(false);
        setApiMsg(e instanceof Error ? e.message : "API unreachable");
      });
  }, []);

  return (
    <div className="welcome">
      <section className="welcome-art">
        <div className="brand-mark">
          <div className="mark">S</div>
          <div>
            School ERP
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
        {apiOk === null ? (
          <p className="hint" role="status">Checking API…</p>
        ) : null}
        {apiOk === false ? (
          <div className="error-box" style={{ textAlign: "left", marginBottom: 16 }}>
            Unable to reach the ERP API. {apiMsg}
          </div>
        ) : null}
        {needsSetup && !created ? (
          <>
            <div className="kicker">First-time setup</div>
            <h2 className="serif" style={{ fontSize: 36, margin: "10px 0 8px" }}>Create your school</h2>
            <p className="hint">No school exists yet. Name it and we will create 3 branches (campuses).</p>
            <Form busyLabel="Creating school…" onSubmit={async () => {
              const res = await setupApi.complete({ schoolName, branches: [b1, b2, b3] });
              setCreated({
                username: res.admin.username,
                password: res.admin.temporaryPassword,
                message: res.message,
              });
              setNeedsSetup(false);
              toast("ok", res.message || "School created");
            }}>
              <Field label="School name"><input value={schoolName} onChange={(e) => setSchoolName(e.target.value)} required /></Field>
              <Field label="Branch 1"><input value={b1} onChange={(e) => setB1(e.target.value)} required /></Field>
              <Field label="Branch 2"><input value={b2} onChange={(e) => setB2(e.target.value)} required /></Field>
              <Field label="Branch 3"><input value={b3} onChange={(e) => setB3(e.target.value)} required /></Field>
              <Button type="submit" kind="brass" loadingText="Creating school…">Create school and 3 branches</Button>
            </Form>
          </>
        ) : (
          <>
            <div className="kicker">Choose your portal</div>
            <h2 className="serif" style={{ fontSize: 36, margin: "10px 0 8px" }}>Sign in with your real account</h2>
            <p className="hint">Each card uses the same backend authentication. You will land on the dashboard for your role.</p>
            {created ? (
              <div className="notice">
                {created.message} Sign in as <span className="pwd">{created.username}</span>
                {created.password ? <> / <span className="pwd">{created.password}</span></> : null}
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
          </>
        )}
      </section>
    </div>
  );
}
