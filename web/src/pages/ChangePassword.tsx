import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "../api/services";
import { useSession } from "../lib/session";
import { Button, Field, Form } from "../ui/kit";
import { useToast } from "../lib/toast";

const RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/;

export default function ChangePassword() {
  const { logout, user } = useSession();
  const nav = useNavigate();
  const toast = useToast();
  const [currentPassword, setCurrent] = useState("");
  const [newPassword, setNew] = useState("");
  const [confirm, setConfirm] = useState("");

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="kicker">Security</div>
        <h1>Change password</h1>
        <p className="hint">
          {user?.mustChangePassword
            ? "Temporary passwords must be replaced before using the ERP."
            : "Update your password, then sign in again with the new one."}{" "}
          8–72 characters with upper, lower, digit and a special character.
        </p>
        <Form
          busyLabel="Changing password…"
          onSubmit={async () => {
            if (newPassword !== confirm) throw new Error("New password and confirmation do not match.");
            if (currentPassword === newPassword) throw new Error("New password must be different from the current password.");
            if (!RULE.test(newPassword)) {
              throw new Error("Password must be 8–72 characters with upper, lower, digit and a special character.");
            }
            await authApi.changePassword(currentPassword, newPassword);
            toast("ok", "Password changed. Sign in again with the new password.");
            await logout({ remote: false });
            nav(user?.role === "ERP_OWNER" ? "/admin" : "/", { replace: true });
          }}
        >
          <Field label="Current password">
            <input type="password" autoComplete="current-password" value={currentPassword} onChange={(e) => setCurrent(e.target.value)} required />
          </Field>
          <Field label="New password">
            <input type="password" autoComplete="new-password" value={newPassword} onChange={(e) => setNew(e.target.value)} required minLength={8} maxLength={72} />
          </Field>
          <Field label="Confirm new password">
            <input type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required minLength={8} maxLength={72} />
          </Field>
          <Button type="submit" kind="brass" loadingText="Changing password…">
            Update password
          </Button>
        </Form>
      </div>
    </div>
  );
}
