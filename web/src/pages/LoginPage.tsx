import { useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { useSession } from "../lib/session";
import type { Role } from "../lib/types";
import { ROLE_LABEL } from "../lib/types";
import { homeFor } from "../lib/routes";
import { Button, Field, Form } from "../ui/kit";
import { useToast } from "../lib/toast";

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
  const nav = useNavigate();
  const toast = useToast();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  if (user) return <Navigate to={homeFor(user)} replace />;
  if (!expected) return <Navigate to="/" replace />;

  return (
    <div className="auth-page">
      <div className="auth-card">
        <Link to="/" className="kicker">
          ← All portals
        </Link>
        <h1>{owner ? "ERP Owner" : ROLE_LABEL[expected]}</h1>
        <p className="hint">
          {owner
            ? "Sign in with erp.owner or owner@erpschool.local against /api/v1/auth/login."
            : "Use the generated username (GVS-ADM-0001) or the account email. Same live /api/v1/auth/login endpoint."}
        </p>
        <Form
          busyLabel="Signing in…"
          onSubmit={async () => {
            const u = await login(username, password, expected);
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
