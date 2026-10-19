import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { useSession } from "../lib/session";
import { useBranding } from "../lib/branding";
import type { Role } from "../lib/types";
import { ROLE_LABEL } from "../lib/types";
import { homeFor, LOGIN_PORTALS } from "../lib/routes";
import { Button, Field, Form } from "../ui/kit";
import RotatingLine from "../ui/RotatingLine";
import { useToast } from "../lib/toast";
import { prefetchPortal } from "../lib/prefetch";

const MAP: Record<string, Role> = {
  teacher: "TEACHER",
  principal: "PRINCIPAL",
  student: "STUDENT",
  parent: "PARENT",
  account: "ACCOUNT_OFFICER",
  admin: "SCHOOL_ADMIN",
  owner: "ERP_OWNER",
};

export default function LoginPage({ owner = false }: { owner?: boolean }) {
  const { role: slug } = useParams();
  const expected = owner ? "ERP_OWNER" : MAP[slug || ""];
  const { user, login } = useSession();
  const { school } = useBranding();
  const nav = useNavigate();
  const toast = useToast();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  useEffect(() => {
    if (owner) {
      void import("./OwnerApp");
    } else {
      void import("./SchoolApp");
      void import("./school/Dashboard");
    }
  }, [owner]);

  if (user) return <Navigate to={homeFor(user)} replace />;
  if (!expected) return <Navigate to={LOGIN_PORTALS} replace />;

  const name = owner ? "ERP Owner" : school?.name || "School";
  const letter = (school?.name || "S").trim().charAt(0).toUpperCase();

  return (
    <div className="auth-page">
      <div className="auth-card">
        <Link to={owner ? "/" : "/portals"} className="back-link">← {owner ? "Home" : "All portals"}</Link>
        <div className="site-brand" style={{ margin: "12px 0 8px" }}>
          {school?.logoUrl && !owner ? <img src={school.logoUrl} alt="" style={{ width: 42, height: 42, objectFit: "cover", borderRadius: 12 }} /> : <div className="mark">{letter}</div>}
          <div>
            <strong>{owner ? "Platform console" : name}</strong>
            <div className="hint">{owner ? "ERP Owner" : `${ROLE_LABEL[expected]} portal`}</div>
          </div>
        </div>
        <h1>{owner ? "ERP Owner" : ROLE_LABEL[expected]}</h1>
        <RotatingLine />
        <p className="hint">
          {owner
            ? "Sign in with your platform owner account."
            : `Sign in to ${name}. Use your generated username or email.`}
        </p>
        <Form
          busyLabel="Signing in…"
          onSubmit={async () => {
            const u = await login(username, password, expected);
            prefetchPortal(u.role);
            toast("ok", `Signed in as ${u.fullName || u.username}`);
            nav(homeFor(u), { replace: true });
          }}
        >
          <Field label="Username or email">
            <input value={username} autoComplete="username" onChange={(e) => setUsername(e.target.value)} required />
          </Field>
          <Field label="Password">
            <input type="password" value={password} autoComplete="current-password" onChange={(e) => setPassword(e.target.value)} required />
          </Field>
          <div className="row" style={{ marginTop: 12 }}>
            <Button type="submit" kind="brass" loadingText="Signing in…">
              Sign in
            </Button>
          </div>
        </Form>
      </div>
    </div>
  );
}
