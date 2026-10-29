import { useEffect, useMemo, useState } from "react";
import { admissionApi } from "../../api/services";
import { pretty } from "../../lib/format";
import { Badge, Button, Empty, ErrorBox, Field, Form, Modal, QueryState, Table, useAsync } from "../../ui/kit";
import { useToast } from "../../lib/toast";

const DAYS = [
  { id: 1, label: "Mon" },
  { id: 2, label: "Tue" },
  { id: 3, label: "Wed" },
  { id: 4, label: "Thu" },
  { id: 5, label: "Fri" },
  { id: 6, label: "Sat" },
  { id: 7, label: "Sun" },
];

function asTime(iso?: unknown) {
  if (!iso) return "—";
  const d = new Date(String(iso));
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

function minutesOf(t: string) {
  const [h, m] = String(t || "00:00").slice(0, 5).split(":").map((x) => Number(x) || 0);
  return h * 60 + m;
}

function windowInside(inner: { start: string; end: string }, outerStart: string, outerEnd: string) {
  return minutesOf(inner.start) >= minutesOf(outerStart) && minutesOf(inner.end) <= minutesOf(outerEnd);
}

function statusLabel(raw: unknown) {
  const s = String(raw || "");
  if (s === "SUBMITTED") return "Pending";
  if (s === "REVIEWED") return "Under Review";
  if (s === "CANCELLED") return "Cancelled";
  return pretty(s);
}

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
  const [openStart, setOpenStart] = useState("09:00");
  const [openEnd, setOpenEnd] = useState("13:00");
  const [minutes, setMinutes] = useState("30");
  const [closed, setClosed] = useState<number[]>([7]);
  const [extras, setExtras] = useState<{ start: string; end: string; minutes: string }[]>([]);
  const [openId, setOpenId] = useState<string | null>(null);
  const [detail, setDetail] = useState<Record<string, unknown> | null>(null);
  const [detailBusy, setDetailBusy] = useState(false);

  useEffect(() => {
    if (!settings) return;
    setOpenStart(String(settings.openStart || "09:00").slice(0, 5));
    setOpenEnd(String(settings.openEnd || "13:00").slice(0, 5));
    setMinutes(String(settings.slotMinutes || 30));
    const days = Array.isArray(settings.closedDays) ? settings.closedDays.map(Number) : [7];
    setClosed(days.length ? days : [7]);
    const unique = new Map<string, { start: string; end: string; minutes: string }>();
    for (const p of cfg.data?.openPeriods || []) {
      const start = String(p.startTime || "").slice(0, 5);
      const end = String(p.endTime || "").slice(0, 5);
      const slot = String(p.slotMinutes || 30);
      const k = `${start}|${end}|${slot}`;
      unique.set(k, { start, end, minutes: slot });
    }
    const primaryStart = String(settings.openStart || "09:00").slice(0, 5);
    const primaryEnd = String(settings.openEnd || "13:00").slice(0, 5);
    const primaryMinutes = String(settings.slotMinutes || 30);
    const primary = `${primaryStart}|${primaryEnd}|${primaryMinutes}`;
    setExtras([...unique.values()].filter((s) => {
      if (`${s.start}|${s.end}|${s.minutes}` === primary) return false;
      /* Old overlapping windows (09:00–13:00 inside a new 08:00–15:00) caused duplicate times. */
      return !windowInside(s, primaryStart, primaryEnd);
    }));
  }, [settings, cfg.data?.openPeriods]);

  const saveFlag = async (patch: Record<string, unknown>) => {
    await admissionApi.saveSettings(patch);
    toast("ok", "Admission settings saved");
    await cfg.reload();
  };

  const toggleClosed = (id: number) => {
    setClosed((cur) => (cur.includes(id) ? cur.filter((d) => d !== id) : [...cur, id].sort((a, b) => a - b)));
  };

  const openApp = async (id: string) => {
    setOpenId(id);
    setDetailBusy(true);
    try {
      setDetail(await admissionApi.application(id));
    } catch (e) {
      setDetail(null);
      toast("err", e instanceof Error ? e.message : "Unable to open application");
    } finally {
      setDetailBusy(false);
    }
  };

  const decide = async (status: "ACCEPTED" | "REJECTED") => {
    if (!openId) return;
    setDetailBusy(true);
    try {
      const next = await admissionApi.setStatus(openId, status);
      setDetail(next);
      toast("ok", status === "ACCEPTED" ? "Appointment accepted" : "Application rejected");
      await apps.reload();
    } catch (e) {
      toast("err", e instanceof Error ? e.message : "Could not update status");
    } finally {
      setDetailBusy(false);
    }
  };

  const payload = useMemo(() => (detail?.payload && typeof detail.payload === "object" ? detail.payload as Record<string, unknown> : {}), [detail]);
  const appt = detail?.appointment && typeof detail.appointment === "object" ? detail.appointment as Record<string, unknown> : null;
  const decided = detail?.status === "ACCEPTED" || detail?.status === "REJECTED";

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
            <h3>Appointment hours</h3>
            <p className="hint">Set opening, closing and length. Every normal school day is available unless you mark it closed. Sunday starts closed. Times such as 09:00, 09:30 are generated automatically — do not create each slot.</p>
            <Form busyLabel="Saving hours…" onSubmit={async () => {
              await admissionApi.saveSettings({
                closedDays: closed,
                openStart,
                openEnd,
                slotMinutes: Number(minutes),
                sessions: [
                  { startTime: openStart, endTime: openEnd, slotMinutes: Number(minutes) },
                  ...extras
                    .filter((s) => `${s.start}|${s.end}|${s.minutes}` !== `${openStart}|${openEnd}|${minutes}`)
                    .filter((s) => !windowInside(s, openStart, openEnd))
                    .map((s) => ({ startTime: s.start, endTime: s.end, slotMinutes: Number(s.minutes) })),
                ],
              });
              toast("ok", "Appointment hours saved");
              await cfg.reload();
            }}>
              <div className="row">
                <Field label="Opens"><input type="time" value={openStart} onChange={(e) => setOpenStart(e.target.value)} /></Field>
                <Field label="Closes"><input type="time" value={openEnd} onChange={(e) => setOpenEnd(e.target.value)} /></Field>
                <Field label="Duration">
                  <select value={minutes} onChange={(e) => setMinutes(e.target.value)}>
                    {["15", "20", "30", "45", "60"].map((m) => <option key={m} value={m}>{m} min</option>)}
                  </select>
                </Field>
              </div>
              <p className="hint" style={{ marginTop: 8 }}>Closed days — appointments are not offered</p>
              <div className="row">
                {DAYS.map((d) => (
                  <label key={d.id} className="chip">
                    <input type="checkbox" checked={closed.includes(d.id)} onChange={() => toggleClosed(d.id)} />
                    {d.label}
                  </label>
                ))}
              </div>
              {extras.map((s, i) => (
                <div className="row" key={`ex-${i}`} style={{ marginTop: 8 }}>
                  <Field label="Also opens"><input type="time" value={s.start} onChange={(e) => setExtras((xs) => xs.map((x, n) => n === i ? { ...x, start: e.target.value } : x))} /></Field>
                  <Field label="Closes"><input type="time" value={s.end} onChange={(e) => setExtras((xs) => xs.map((x, n) => n === i ? { ...x, end: e.target.value } : x))} /></Field>
                  <Field label="Duration">
                    <select value={s.minutes} onChange={(e) => setExtras((xs) => xs.map((x, n) => n === i ? { ...x, minutes: e.target.value } : x))}>
                      {["15", "20", "30", "45", "60"].map((m) => <option key={m} value={m}>{m} min</option>)}
                    </select>
                  </Field>
                  <Button kind="ghost" onClick={() => setExtras((xs) => xs.filter((_, n) => n !== i))}>Remove</Button>
                </div>
              ))}
              <div className="row" style={{ marginTop: 8 }}>
                <Button type="submit" kind="brass" loadingText="Saving hours…">Save hours</Button>
                <Button kind="ghost" onClick={() => setExtras((xs) => [...xs, { start: "16:00", end: "19:00", minutes }])}>Add another opening period</Button>
              </div>
            </Form>
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
        <h3>Applications & appointments</h3>
        <QueryState status={apps} label="applications">
          {!(apps.data?.content || []).length ? <Empty title="No applications yet" /> : (
            <Table
              headers={["Student", "Campus", "Appointment", "Status", ""]}
              rows={(apps.data?.content || []).map((a) => [
                String(a.studentName),
                String(a.campusName || "—"),
                a.appointment && typeof a.appointment === "object"
                  ? asTime((a.appointment as { slotStart?: string }).slotStart)
                  : "—",
                <Badge key={String(a.id)} value={statusLabel(a.status)} />,
                <Button key={`o-${String(a.id)}`} kind="ghost" onClick={() => void openApp(String(a.id))}>Open</Button>,
              ])}
            />
          )}
        </QueryState>
      </div>
      <Modal title="Application" open={Boolean(openId)} onClose={() => { setOpenId(null); setDetail(null); }}>
        {detailBusy && !detail ? <p className="hint">Loading application…</p> : null}
        {detail ? (
          <>
            <p className="hint" style={{ marginTop: 0 }}>
              {statusLabel(detail.status)} · submitted {asTime(detail.createdAt)}
            </p>
            <div className="grid two">
              <p><strong>Student</strong><br />{String(detail.studentName)}</p>
              <p><strong>Guardian</strong><br />{String(detail.guardianName)}</p>
              <p><strong>CNIC / B-Form</strong><br />{String(detail.cnic)}</p>
              <p><strong>Mobile</strong><br />{String(detail.phone)}</p>
              <p><strong>Campus</strong><br />{String(detail.campusName || "—")}</p>
              <p><strong>Appointment</strong><br />{appt ? `${asTime(appt.slotStart)} · ${statusLabel(appt.status)}` : "Not booked"}</p>
            </div>
            {Object.keys(payload).length ? (
              <div className="card" style={{ marginTop: 12 }}>
                <h3>Submitted answers</h3>
                {Object.entries(payload).map(([k, v]) => (
                  <p key={k}><strong>{pretty(k)}</strong><br />{v == null || String(v) === "" ? "—" : String(v)}</p>
                ))}
              </div>
            ) : null}
            <div className="row" style={{ marginTop: 16 }}>
              <Button kind="ok" disabled={detailBusy || decided} loading={detailBusy} loadingText="Saving…" onClick={() => void decide("ACCEPTED")}>Accept</Button>
              <Button kind="danger" disabled={detailBusy || decided} loading={detailBusy} loadingText="Saving…" onClick={() => void decide("REJECTED")}>Reject</Button>
            </div>
            {decided ? <p className="hint">This decision is saved. The application stays in history.</p> : null}
          </>
        ) : null}
      </Modal>
    </>
  );
}
