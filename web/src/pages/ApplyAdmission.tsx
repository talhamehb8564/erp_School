import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { publicAdmissionApi } from "../api/services";
import { useBranding } from "../lib/branding";
import { contactFor } from "../lib/site";
import { Button, Empty, ErrorBox, Field, Form, QueryState, useAsync } from "../ui/kit";
import { CampusContact, WhatsAppFloat } from "../ui/CampusContact";

type FieldDef = {
  fieldKey: string;
  label: string;
  fieldType: string;
  required?: boolean;
  options?: string;
};

function slotLabel(iso: string) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function maskCnic(raw: unknown) {
  const digits = String(raw || "").replace(/\D/g, "");
  if (digits.length < 5) return "••••";
  return `${digits.slice(0, 5)}-*******-${digits.slice(-1)}`;
}

export default function ApplyAdmission() {
  const { school, campuses, campus, setCampusId: setBrandCampus } = useBranding();
  const formQ = useAsync(() => publicAdmissionApi.form(), []);
  const form = formQ.data;
  const fields = (form?.fields || []) as FieldDef[];
  const formCampuses = ((form?.campuses || []) as { id: string; name: string }[]);
  const campusOptions = formCampuses.length ? formCampuses : campuses.map((c) => ({ id: c.id, name: c.name }));
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [campusId, setCampusId] = useState(campus?.id || "");
  const [date, setDate] = useState("");
  const [slotStart, setSlotStart] = useState("");
  const [help, setHelp] = useState(false);
  const [done, setDone] = useState<Record<string, unknown> | null>(null);
  const [slots, setSlots] = useState<{ start: string; end: string; booked?: boolean }[]>([]);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [slotsError, setSlotsError] = useState<unknown>(null);

  const loadSlots = useCallback(async () => {
    if (!form?.appointmentEnabled || !date) {
      setSlots([]);
      setSlotsLoading(false);
      setSlotsError(null);
      return;
    }
    setSlots([]);
    setSlotStart("");
    setSlotsLoading(true);
    setSlotsError(null);
    try {
      const rows = await publicAdmissionApi.slots(school?.code, date, campusId || undefined);
      setSlots(Array.isArray(rows) ? rows : []);
    } catch (e) {
      setSlots([]);
      setSlotsError(e);
    } finally {
      setSlotsLoading(false);
    }
  }, [form?.appointmentEnabled, date, campusId, school?.code]);

  useEffect(() => {
    if (campus?.id && !campusId) setCampusId(campus.id);
  }, [campus?.id, campusId]);

  useEffect(() => {
    void loadSlots();
  }, [loadSlots]);

  const pickCampus = (id: string) => {
    setCampusId(id);
    setSlotStart("");
    if (id) setBrandCampus(id);
  };

  const selectedCampus = campuses.find((c) => c.id === campusId) || campus;
  const contact = contactFor(selectedCampus, school);
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

  const slotsQ = { data: slots, loading: slotsLoading, error: slotsError, reload: () => void loadSlots() };

  return (
    <div className="site apply-page">
      <header className="site-nav apply-nav">
        <Link to="/" className="site-brand">
          {school?.logoUrl ? <img src={school.logoUrl} alt="" /> : <div className="mark">{(school?.name || "S").charAt(0)}</div>}
          <div>
            <strong>{school?.name || "School"}</strong>
          </div>
        </Link>
        <div className="site-nav-tools">
          {campusOptions.length ? (
            <select className="site-branch" value={campusId} onChange={(e) => pickCampus(e.target.value)} aria-label="Select campus">
              <option value="">Select campus</option>
              {campusOptions.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          ) : null}
          <Link to="/portals" className="btn ghost sm site-nav-portal">School Portal</Link>
          <Link to="/" className="btn ghost sm">Website</Link>
        </div>
      </header>
      <section className="site-section apply-layout">
        <div>
          <p className="kicker">Admissions</p>
          <h1>{String(form?.ctaLabel || "Apply for Admission")}</h1>
          <QueryState status={formQ} label="admission form">
            {form && form.enabled === false ? (
              <Empty title="Admissions are not open" />
            ) : done ? (
              <div className="card">
                <h3>Application received</h3>
                <p>{String(done.studentName)} · CNIC {maskCnic(done.cnic)}</p>
                {done.appointment ? (
                  <p>Appointment {slotLabel(String((done.appointment as { slotStart?: string }).slotStart || ""))}</p>
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
                {campusOptions.length ? (
                  <Field label="Campus">
                    <select value={campusId} onChange={(e) => pickCampus(e.target.value)}>
                      <option value="">Select campus</option>
                      {campusOptions.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
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
                    <p className="hint">Times are generated from this school’s opening hours. Booked times cannot be selected again.</p>
                    <Field label="Date">
                      <input type="date" value={date} required={Boolean(form.appointmentRequired)} onChange={(e) => { setDate(e.target.value); setSlotStart(""); setSlots([]); }} />
                    </Field>
                    {date ? (
                      <QueryState status={slotsQ} label="open slots">
                        {!slots.length ? <Empty title="No open slots on this date" /> : (
                          <div className="slot-grid">
                            {slots.map((s) => {
                              const booked = Boolean(s.booked);
                              const on = slotStart === s.start;
                              return (
                                <button
                                  key={String(s.start)}
                                  type="button"
                                  className={`slot ${booked ? "booked" : ""} ${on ? "on" : ""}`}
                                  disabled={booked}
                                  onClick={() => setSlotStart(String(s.start))}
                                >
                                  <strong>{slotLabel(String(s.start))}</strong>
                                  <span>{booked ? "Booked" : "Available"}</span>
                                </button>
                              );
                            })}
                          </div>
                        )}
                      </QueryState>
                    ) : null}
                  </div>
                ) : null}
                <div className="row" style={{ marginTop: 8 }}>
                  <Button type="submit" kind="brass" loadingText="Submitting…">Submit application</Button>
                  <span className="apply-help-toggle">
                    <Button kind="ghost" onClick={() => setHelp((v) => !v)}>
                      {help ? "Hide contact" : "Contact us"}
                    </Button>
                  </span>
                </div>
              </Form>
            )}
          </QueryState>
        </div>
        <aside className={`apply-help ${help ? "open" : ""}`}>
          <div className="card">
            <CampusContact campus={selectedCampus} school={school} />
            {contact.whatsapp ? (
              <a className="btn ok" href={contact.whatsapp} target="_blank" rel="noopener noreferrer" style={{ marginTop: 12 }}>
                WhatsApp this campus
              </a>
            ) : null}
          </div>
        </aside>
      </section>
      <WhatsAppFloat href={contact.whatsapp} />
    </div>
  );
}
