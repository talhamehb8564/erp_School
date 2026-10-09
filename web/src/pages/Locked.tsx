import { useEffect, useState } from "react";
import { fileApi, settingsApi, subscriptionApi } from "../api/services";
import { useSession } from "../lib/session";
import { Button, ErrorBox, Field, Form, Loading } from "../ui/kit";
import { useToast } from "../lib/toast";
import { pretty } from "../lib/format";

export default function Locked() {
  const { tenant, user, logout, reload } = useSession();
  const toast = useToast();
  const [ref, setRef] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const admin = user?.role === "SCHOOL_ADMIN";

  return (
    <div className="locked">
      <div className="card">
        <div className="kicker">Subscription</div>
        <h1>School access is locked</h1>
        <p>
          Your school's ERP subscription is currently inactive. Please complete the payment and submit the payment
          proof to reactivate your account.
        </p>
        <p>
          School: <strong>{tenant?.name || "—"}</strong> · Status <strong>{pretty(tenant?.status)}</strong>
        </p>
        {admin ? (
          <Form
            busyLabel="Uploading…"
            onSubmit={async () => {
              if (!file) throw new Error("Choose a payment proof file first.");
              const sub = await subscriptionApi.current();
              const uploaded = await fileApi.upload(file);
              await subscriptionApi.submitPayment(sub.id, { slipUrl: uploaded.url, transactionRef: ref || undefined });
              toast("ok", "Payment proof submitted to the ERP owner.");
              await reload();
            }}
          >
            <SettingsHint />
            <Field label="Bank / wallet reference">
              <input value={ref} onChange={(e) => setRef(e.target.value)} placeholder="TRX / slip number" />
            </Field>
            <Field label="Payment proof (PDF or image)">
              <input type="file" accept=".pdf,image/*" onChange={(e) => setFile(e.target.files?.[0] || null)} required />
            </Field>
            <div className="row">
              <Button type="submit" kind="brass" disabled={!file} loadingText="Uploading…">
                Submit payment proof
              </Button>
              <Button kind="ghost" loadingText="Signing out…" onClick={() => logout()}>
                Sign out
              </Button>
            </div>
          </Form>
        ) : (
          <Button kind="ghost" loadingText="Signing out…" onClick={() => logout()}>
            Sign out
          </Button>
        )}
      </div>
    </div>
  );
}

function SettingsHint() {
  const [text, setText] = useState<string>("");
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const load = () => {
    setLoading(true);
    setError(null);
    void settingsApi
      .get()
      .then((s) => {
        const bits = [s.bankName, s.accountTitle, s.accountNumber, s.jazzcash, s.easypaisa, s.paymentInstructions]
          .filter(Boolean)
          .join(" · ");
        setText(bits || "Ask the ERP owner for transfer instructions.");
      })
      .catch((e) => setError(e))
      .finally(() => setLoading(false));
  };
  useEffect(() => {
    load();
  }, []);
  if (loading) return <Loading label="Loading payment instructions…" />;
  if (error) return <ErrorBox error={error} onRetry={load} title="Unable to load payment instructions" />;
  return <div className="notice">{text}</div>;
}
