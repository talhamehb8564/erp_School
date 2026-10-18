import { useState } from "react";
import { admissionApi } from "../../api/services";
import { pretty } from "../../lib/format";
import { Badge, Button, Empty, ErrorBox, Field, Form, QueryState, Table, useAsync } from "../../ui/kit";
import { useToast } from "../../lib/toast";

const DAYS = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export default function AdmissionsPage() {
  const toast = useToast();
  const cfg = useAsync(() => admissionApi.config());
  const apps = useAsync(() => admissionApi.applications());
  const settings = cfg.data?.settings;
  const [cta, setCta] = useState("");
  const [label, setLabel] = useState("");
  const [key, setKey] = useState("");
  const [type, setType] = useState("TEXT");
  const [required, setRequired] = useState(false);
  const [day, setDay] = useState("1");
  const [start, setStart] = useState("09:00");
  const [end, setEnd] = useState("13:00");
  const [minutes, setMinutes] = useState("30");

  const saveFlag = async (patch: Record<string, unknown>) => {
    await admissionApi.saveSettings(patch);
    toast("ok", "Admission settings saved");
    await cfg.reload();
  };

  return (
    <>
      <div className="page-title">
        <div>
          <div className="crumbs">School / Admissions</div>
          <h1>Admission form</h1>
          <p>Public Apply for Admission uses this builder. Name, guardian, CNIC and mobile stay compulsory.</p>
        </div>
      </div>
      <QueryState status={cfg} label="admission settings">
        {cfg.error && !cfg.data ? <ErrorBox error={cfg.error} onRetry={() => void cfg.reload()} /> : null}
        <div className="grid two">
          <div className="card">
            <h3>Public form</h3>
            <p>Feature {cfg.data?.featureEnabled === false ? "OFF at owner" : "ON"}.</p>
            <label className="row">
              <input type="checkbox" checked={Boolean(settings?.enabled)} onChange={(e) => void saveFlag({ enabled: e.target.checked })} />
              Accept applications
            </label>
            <label className="row">
              <input type="checkbox" checked={Boolean(settings?.appointmentEnabled)} onChange={(e) => void saveFlag({ appointmentEnabled: e.target.checked })} />
              Offer appointment slots
            </label>
            <label className="row">
              <input type="checkbox" checked={Boolean(settings?.appointmentRequired)} onChange={(e) => void saveFlag({ appointmentRequired: e.target.checked })} />
              Appointment required
            </label>
            <Form busyLabel="Saving…" onSubmit={async () => {
              await admissionApi.saveSettings({ ctaLabel: cta || settings?.ctaLabel });
              toast("ok", "Button label saved");
              await cfg.reload();
            }}>
              <Field label="Website button label">
                <input value={cta || String(settings?.ctaLabel || "")} onChange={(e) => setCta(e.target.value)} placeholder="Apply for Admission" />
              </Field>
              <Button type="submit" kind="brass" loadingText="Saving…">Save label</Button>
            </Form>
          </div>
          <div className="card">
            <h3>Open periods</h3>
            <Form busyLabel="Adding…" onSubmit={async () => {
              await admissionApi.addPeriod({ dayOfWeek: Number(day), startTime: start, endTime: end, slotMinutes: Number(minutes) });
              toast("ok", "Open period added");
              await cfg.reload();
            }}>
              <div className="row">
                <Field label="Day">
                  <select value={day} onChange={(e) => setDay(e.target.value)}>
                    {DAYS.map((d, i) => i ? <option key={d} value={i}>{d}</option> : null)}
                  </select>
                </Field>
                <Field label="Start"><input type="time" value={start} onChange={(e) => setStart(e.target.value)} /></Field>
                <Field label="End"><input type="time" value={end} onChange={(e) => setEnd(e.target.value)} /></Field>
                <Field label="Slot">
                  <select value={minutes} onChange={(e) => setMinutes(e.target.value)}>
                    {["15", "20", "30", "45", "60"].map((m) => <option key={m} value={m}>{m} min</option>)}
                  </select>
                </Field>
              </div>
              <Button type="submit" kind="brass" loadingText="Adding…">Add period</Button>
            </Form>
            {!(cfg.data?.openPeriods || []).length ? <Empty title="No open hours yet" /> : (
              <Table
                headers={["Day", "Hours", "Slot", ""]}
                rows={(cfg.data?.openPeriods || []).map((p) => [
                  DAYS[Number(p.dayOfWeek)] || p.dayOfWeek,
                  `${p.startTime}–${p.endTime}`,
                  `${p.slotMinutes} min`,
                  <Button key={String(p.id)} kind="ghost" loadingText="Removing…" onClick={async () => {
                    await admissionApi.deletePeriod(String(p.id));
                    await cfg.reload();
                  }}>Remove</Button>,
                ])}
              />
            )}
          </div>
        </div>
        <div className="card" style={{ marginTop: 16 }}>
          <h3>Fields</h3>
          <Form busyLabel="Adding…" onSubmit={async () => {
            await admissionApi.addField({ fieldKey: key, label, fieldType: type, required });
            setKey(""); setLabel("");
            toast("ok", "Field added");
            await cfg.reload();
          }}>
            <div className="row">
              <Field label="Key"><input value={key} onChange={(e) => setKey(e.target.value)} placeholder="previousSchool" /></Field>
              <Field label="Label"><input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Previous school" /></Field>
              <Field label="Type">
                <select value={type} onChange={(e) => setType(e.target.value)}>
                  {["TEXT", "TEXTAREA", "TEL", "EMAIL", "DATE", "NUMBER", "SELECT"].map((t) => <option key={t}>{t}</option>)}
                </select>
              </Field>
              <label className="row"><input type="checkbox" checked={required} onChange={(e) => setRequired(e.target.checked)} /> Required</label>
            </div>
            <Button type="submit" kind="brass" loadingText="Adding…">Add field</Button>
          </Form>
          <Table
            headers={["Field", "Type", "Required", ""]}
            rows={(cfg.data?.fields || []).map((f) => [
              <div key={String(f.id)}>
                <strong>{String(f.label)}</strong>
                <div className="hint">{String(f.fieldKey)}{f.locked ? " · core" : ""}</div>
              </div>,
              String(f.fieldType),
              f.required ? "Yes" : "No",
              f.locked ? "—" : (
                <Button key={`d-${String(f.id)}`} kind="ghost" loadingText="Removing…" onClick={async () => {
                  await admissionApi.deleteField(String(f.id));
                  await cfg.reload();
                }}>Remove</Button>
              ),
            ])}
          />
        </div>
      </QueryState>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Applications</h3>
        <QueryState status={apps} label="applications">
          {!(apps.data?.content || []).length ? <Empty title="No applications yet" /> : (
            <Table
              headers={["Student", "Guardian", "CNIC", "Mobile", "Status"]}
              rows={(apps.data?.content || []).map((a) => [
                String(a.studentName),
                String(a.guardianName),
                String(a.cnic),
                String(a.phone),
                <Badge key={String(a.id)} value={String(a.status)} />,
              ])}
            />
          )}
        </QueryState>
      </div>
    </>
  );
}
