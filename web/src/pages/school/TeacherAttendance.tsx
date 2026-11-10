import { useMemo, useState } from "react";
import { attendanceApi } from "../../api/services";
import { pretty } from "../../lib/format";
import { Badge, Button, Empty, ErrorBox, Field, QueryState, Table, useAsync } from "../../ui/kit";

const STATUSES = ["PRESENT", "ABSENT", "LATE", "LEAVE"] as const;

function iso(d: Date) {
  return d.toISOString().slice(0, 10);
}

export default function TeacherAttendancePage() {
  const today = iso(new Date());
  const [date, setDate] = useState(today);
  const [q, setQ] = useState("");
  const [from, setFrom] = useState(iso(new Date(Date.now() - 30 * 86400000)));
  const [to, setTo] = useState(today);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [err, setErr] = useState<unknown>(null);

  const dayQ = useAsync(() => attendanceApi.teacherDay(date), [date]);
  const histQ = useAsync(() => attendanceApi.teacherHistory(from, to), [from, to]);

  const rows = useMemo(() => {
    const list = dayQ.data || [];
    const needle = q.trim().toLowerCase();
    if (!needle) return list;
    return list.filter((r) =>
      `${r.fullName || ""} ${r.username || ""}`.toLowerCase().includes(needle));
  }, [dayQ.data, q]);

  const mark = async (teacherUserId: string, status: string) => {
    setErr(null);
    setBusyId(teacherUserId + status);
    try {
      await attendanceApi.markTeacher({ teacherUserId, date, status });
      await dayQ.reload();
      await histQ.reload();
    } catch (e) {
      setErr(e);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <div className="page-title">
        <div>
          <div className="crumbs">Academics / Staff attendance</div>
          <h1>Teacher attendance</h1>
          <p>One Present / Absent (or Late / Leave) mark per teacher per date. History is stored, not a flag on the teacher row.</p>
        </div>
      </div>
      {err ? <ErrorBox error={err} title="Could not save teacher attendance" /> : null}
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="row" style={{ alignItems: "flex-end" }}>
          <Field label="Date"><input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></Field>
          <Field label="Filter teacher">
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Name or username" />
          </Field>
        </div>
      </div>
      <QueryState status={dayQ} label="teachers for this date">
        {!rows.length ? <Empty title="No active teachers for this date" /> : (
          <div className="card table-wrap">
            <Table
              headers={["Teacher", "Today", "30-day %", "Mark"]}
              rows={rows.map((r) => [
                <div key={`${r.teacherUserId}-n`}>
                  <strong>{r.fullName}</strong>
                  <div className="hint">{r.username}</div>
                </div>,
                r.status ? <Badge key={`${r.teacherUserId}-s`} value={String(r.status)} /> : "—",
                `${r.percent ?? 0}% (${r.presentDays ?? 0}/${r.markedDays ?? 0})`,
                <div key={`${r.teacherUserId}-m`} className="row">
                  {STATUSES.map((s) => (
                    <Button
                      key={s}
                      kind={r.status === s ? "ok" : "ghost"}
                      loading={busyId === r.teacherUserId + s}
                      loadingText="Saving…"
                      onClick={() => void mark(String(r.teacherUserId), s)}
                    >
                      {pretty(s)}
                    </Button>
                  ))}
                </div>,
              ])}
            />
          </div>
        )}
      </QueryState>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>History</h3>
        <div className="row" style={{ alignItems: "flex-end" }}>
          <Field label="From"><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
          <Field label="To"><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
        </div>
        <QueryState status={histQ} label="teacher attendance history">
          {!(histQ.data || []).length ? <Empty title="No marks in this range" /> : (
            <Table
              headers={["Date", "Teacher id", "Status", "Remarks"]}
              rows={(histQ.data || []).map((h) => [
                h.attendanceDate,
                h.teacherUserId,
                <Badge key={h.id} value={h.status} />,
                h.remarks || "—",
              ])}
            />
          )}
        </QueryState>
      </div>
    </>
  );
}
