import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { useSession } from "../lib/session";
import { useBranding } from "../lib/branding";
import type { Role } from "../lib/types";
import { ROLE_LABEL } from "../lib/types";
import { homeFor, LOGIN_PORTALS } from "../lib/routes";
import { Button, Field, Form } from "../ui/kit";
import CoinLogin from "../ui/CoinLogin";
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
  const roleName = owner ? "ERP Owner" : ROLE_LABEL[expected];

  return (
    <CoinLogin
      back={<Link to={owner ? "/" : "/portals"} className="back-link">← {owner ? "Home" : "All portals"}</Link>}
      logo={
        school?.logoUrl && !owner
          ? <img src={school.logoUrl} alt="" />
          : <div className="mark">{letter}</div>
      }
      school={owner ? "Platform console" : name}
      role={roleName}
      hint={owner ? "Sign in with your platform owner account." : `Use your generated username or email.`}
    >
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
        <div className="row" style={{ marginTop: 8 }}>
          <Button type="submit" kind="brass" loadingText="Signing in…">
            Sign in
          </Button>
        </div>
      </Form>
    </CoinLogin>
  );
}
