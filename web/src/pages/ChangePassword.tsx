import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "../api/services";
import { useSession } from "../lib/session";
import { Button, Field, Form } from "../ui/kit";
import { useToast } from "../lib/toast";

export default function ChangePassword() {
  const { logout } = useSession();
  const nav = useNavigate();
  const toast = useToast();
  const [currentPassword, setCurrent] = useState("");
  const [newPassword, setNew] = useState("");

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="kicker">Security</div>
        <h1>Change password</h1>
        <p className="hint">Temporary passwords must be replaced before using the ERP. Upper, lower, digit and special character. 8–72 characters.</p>
        <Form
          busyLabel="Changing password…"
          onSubmit={async () => {
            await authApi.changePassword(currentPassword, newPassword);
            toast("ok", "Password changed. Sign in again.");
            await logout();
            nav("/", { replace: true });
          }}
        >
          <Field label="Current password">
            <input type="password" value={currentPassword} onChange={(e) => setCurrent(e.target.value)} required />
          </Field>
          <Field label="New password">
            <input type="password" value={newPassword} onChange={(e) => setNew(e.target.value)} required />
          </Field>
          <Button type="submit" kind="brass" loadingText="Changing password…">
            Update password
          </Button>
        </Form>
      </div>
    </div>
  );
}
