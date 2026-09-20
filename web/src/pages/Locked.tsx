import { useEffect, useState } from "react";
import { fileApi, settingsApi, subscriptionApi } from "../api/services";
import { useSession } from "../lib/session";
import { Button, Field, Form, Loading } from "../ui/kit";
import { useToast } from "../lib/toast";
import { pretty } from "../lib/format";

export default function Locked() {
  const { tenant, user, logout, reload } = useSession();
  const toast = useToast();
  const [ref, setRef] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
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
          School: <strong>{tenant?.name}</strong> · Status <strong>{pretty(tenant?.status)}</strong>
        </p>
        {admin ? (
          <Form
            onSubmit={async () => {
              if (!file) return;
              setBusy(true);
              try {
                const sub = await subscriptionApi.current();
                const uploaded = await fileApi.upload(file);
                await subscriptionApi.submitPayment(sub.id, { slipUrl: uploaded.url, transactionRef: ref || undefined });
                toast("ok", "Payment proof submitted to the ERP owner.");
                await reload();
              } catch (e) {
                toast("err", e instanceof Error ? e.message : "Upload failed");
              } finally {
                setBusy(false);
              }
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
              <Button type="submit" kind="brass" disabled={busy || !file}>
                {busy ? "Uploading…" : "Submit payment proof"}
              </Button>
              <Button kind="ghost" onClick={() => void logout()}>
                Sign out
              </Button>
            </div>
          </Form>
        ) : (
          <Button kind="ghost" onClick={() => void logout()}>
            Sign out
          </Button>
        )}
      </div>
    </div>
  );
}

function SettingsHint() {
  const [text, setText] = useState<string>("");
  useEffect(() => {
    void settingsApi
      .get()
      .then((s) => {
        const bits = [s.bankName, s.accountTitle, s.accountNumber, s.jazzcash, s.easypaisa, s.paymentInstructions]
          .filter(Boolean)
          .join(" · ");
        setText(bits || "Ask the ERP owner for transfer instructions.");
      })
      .catch(() => setText("Ask the ERP owner for transfer instructions."));
  }, []);
  if (!text) return <Loading />;
  return <div className="notice">{text}</div>;
}
