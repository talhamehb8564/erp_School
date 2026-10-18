import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { publicAdmissionApi } from "../api/services";
import { useBranding } from "../lib/branding";
import { Button, Empty, ErrorBox, Field, Form, QueryState, useAsync } from "../ui/kit";

type FieldDef = {
  fieldKey: string;
  label: string;
  fieldType: string;
  required?: boolean;
  options?: string;
};

export default function ApplyAdmission() {
  const { school } = useBranding();
  const formQ = useAsync(() => publicAdmissionApi.form(school?.code), [school?.code]);
  const form = formQ.data;
  const fields = (form?.fields || []) as FieldDef[];
  const campuses = (form?.campuses || []) as { id: string; name: string }[];
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [campusId, setCampusId] = useState("");
  const [date, setDate] = useState("");
  const [slotStart, setSlotStart] = useState("");
  const [done, setDone] = useState<Record<string, unknown> | null>(null);
  const slotsQ = useAsync(
    () => (form?.appointmentEnabled && date
      ? publicAdmissionApi.slots(school?.code, date, campusId || undefined)
      : Promise.resolve([])),
    [form?.appointmentEnabled, date, campusId, school?.code],
  );

  const set = (key: string, value: string) => setAnswers((prev) => ({ ...prev, [key]: value }));

  const inputFor = (f: FieldDef) => {
    const v = answers[f.fieldKey] || "";
    if (f.fieldType === "TEXTAREA") {
      return <textarea rows={3} value={v} required={f.required} onChange={(e) => set(f.fieldKey, e.target.value)} />;
    }
    if (f.fieldType === "SELECT") {
      const opts = String(f.options || "").split(/[,|\n]/).map((s) => s.trim()).filter(Boolean);
      return (
        <select value={v} required={f.required} onChange={(e) => set(f.fieldKey, e.target.value)}>
          <option value="">Select</option>
          {opts.map((o) => <option key={o}>{o}</option>)}
        </select>
      );
    }
    const type = f.fieldType === "TEL" ? "tel" : f.fieldType === "EMAIL" ? "email" : f.fieldType === "DATE" ? "date" : f.fieldType === "NUMBER" ? "number" : "text";
    return <input type={type} value={v} required={f.required} onChange={(e) => set(f.fieldKey, e.target.value)} />;
  };

  const slots = useMemo(() => slotsQ.data || [], [slotsQ.data]);

  return (
    <div className="site apply-page">
      <header className="site-nav">
        <Link to="/" className="site-brand">
          {school?.logoUrl ? <img src={school.logoUrl} alt="" /> : <div className="mark">{(school?.name || "S").charAt(0)}</div>}
          <strong>{school?.name || "School"}</strong>
        </Link>
        <Link to="/" className="btn ghost">Back to website</Link>
      </header>
      <section className="site-section">
        <p className="kicker">Admissions</p>
        <h1>{String(form?.ctaLabel || "Apply for Admission")}</h1>
        <QueryState status={formQ} label="admission form">
          {form && form.enabled === false ? (
            <Empty title="Admissions are not open" />
          ) : done ? (
            <div className="card">
              <h3>Application received</h3>
              <p>{String(done.studentName)} · CNIC {String(done.cnic)}</p>
              {done.appointment ? (
                <p>Appointment {String((done.appointment as { slotStart?: string }).slotStart || "")}</p>
              ) : null}
              <Link to="/" className="btn brass">Return home</Link>
            </div>
          ) : !fields.length ? (
            <Empty title="The school has not published an admission form" />
          ) : (
            <Form busyLabel="Submitting…" onSubmit={async () => {
              const res = await publicAdmissionApi.submit(school?.code, {
                campusId: campusId || undefined,
                answers,
                slotStart: slotStart || undefined,
              });
              setDone(res);
            }}>
              {formQ.error ? <ErrorBox error={formQ.error} onRetry={() => void formQ.reload()} /> : null}
              {campuses.length ? (
                <Field label="Campus">
                  <select value={campusId} onChange={(e) => setCampusId(e.target.value)}>
                    <option value="">Any campus</option>
                    {campuses.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </Field>
              ) : null}
              {fields.map((f) => (
                <Field key={f.fieldKey} label={`${f.label}${f.required ? " *" : ""}`}>
                  {inputFor(f)}
                </Field>
              ))}
              {form?.appointmentEnabled ? (
                <div className="card" style={{ margin: "12px 0" }}>
                  <h3>Appointment {form.appointmentRequired ? "(required)" : "(optional)"}</h3>
                  <Field label="Date">
                    <input type="date" value={date} required={Boolean(form.appointmentRequired)} onChange={(e) => { setDate(e.target.value); setSlotStart(""); }} />
                  </Field>
                  {date ? (
                    <QueryState status={slotsQ} label="open slots">
                      {!slots.length ? <Empty title="No open slots on this date" /> : (
                        <div className="row">
                          {slots.map((s) => (
                            <Button
                              key={String(s.start)}
                              kind={slotStart === s.start ? "ok" : "ghost"}
                              onClick={() => setSlotStart(String(s.start))}
                            >
                              {new Date(String(s.start)).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                            </Button>
                          ))}
                        </div>
                      )}
                    </QueryState>
                  ) : null}
                </div>
              ) : null}
              <Button type="submit" kind="brass" loadingText="Submitting…">Submit application</Button>
            </Form>
          )}
        </QueryState>
      </section>
    </div>
  );
}
