import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "../api/services";
import { LOGIN_PORTALS } from "../lib/routes";
import { useSession } from "../lib/session";
import { Button, Field, Form } from "../ui/kit";
import { useToast } from "../lib/toast";

const RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/;

export default function ChangePassword() {
  const { logout, user } = useSession();
  const nav = useNavigate();
  const toast = useToast();
  const [currentPassword, setCurrent] = useState("");
  const [username, setUsername] = useState(user?.username || "");
  const [newPassword, setNew] = useState("");
  const [confirm, setConfirm] = useState("");

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="kicker">Security</div>
        <h1>Account credentials</h1>
        <p className="hint">
          {user?.mustChangePassword
            ? "Temporary passwords must be replaced before using the ERP. You may also choose a new username."
            : "Change username, password, or both. You will sign in again with the new credentials."}{" "}
          Passwords are 8–72 characters with upper, lower, digit and a special character. Students may use CNIC / B-Form as username.
        </p>
        <Form
          busyLabel="Saving credentials…"
          onSubmit={async () => {
            const nextUsername = username.trim();
            const usernameChanged = nextUsername.toLowerCase() !== (user?.username || "").toLowerCase();
            if (!nextUsername || nextUsername.length < 3) {
              throw new Error("Username must be at least 3 characters.");
            }
            if (user?.mustChangePassword && !newPassword) {
              throw new Error("Temporary passwords must be replaced before continuing.");
            }
            if (!usernameChanged && !newPassword) {
              throw new Error("Change your username, your password, or both.");
            }
            if (newPassword) {
              if (newPassword !== confirm) throw new Error("New password and confirmation do not match.");
              if (currentPassword === newPassword) throw new Error("New password must be different from the current password.");
              if (!RULE.test(newPassword)) {
                throw new Error("Password must be 8–72 characters with upper, lower, digit and a special character.");
              }
            } else if (confirm) {
              throw new Error("Enter the new password in both fields, or leave both blank to keep the current password.");
            }
            const updated = await authApi.changePassword(
              currentPassword,
              newPassword || undefined,
              nextUsername,
            );
            if (!updated?.id) {
              throw new Error("The server did not confirm the credential update.");
            }
            toast("ok", usernameChanged
              ? `Username is now ${updated.username}. Sign in again.`
              : "Password updated. Sign in again.");
            await logout({ remote: false });
            nav(LOGIN_PORTALS, { replace: true });
          }}
        >
          <Field label="Username">
            <input value={username} autoComplete="username" onChange={(e) => setUsername(e.target.value)} required minLength={3} />
          </Field>
          <Field label="Current password">
            <input type="password" autoComplete="current-password" value={currentPassword} onChange={(e) => setCurrent(e.target.value)} required />
          </Field>
          <Field label={user?.mustChangePassword ? "New password" : "New password (leave blank to keep current)"}>
            <input type="password" autoComplete="new-password" value={newPassword} onChange={(e) => setNew(e.target.value)} minLength={newPassword || confirm ? 8 : undefined} maxLength={72} />
          </Field>
          <Field label="Confirm new password">
            <input type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} minLength={newPassword || confirm ? 8 : undefined} maxLength={72} />
          </Field>
          <Button type="submit" kind="brass" loadingText="Saving credentials…">
            Save credentials
          </Button>
        </Form>
      </div>
    </div>
  );
}
