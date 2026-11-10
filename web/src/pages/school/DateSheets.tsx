import { useMemo, useState } from "react";
import { academicApi, examApi, onlineExamApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { fmtDate, fmtTime } from "../../lib/format";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import type { DateSheet, DateSheetEntry } from "../../lib/types";
import { Button, Empty, Field, Form, Modal, QueryState, Table, useAsync } from "../../ui/kit";

type Row = {
  subjectId: string;
  examDate: string;
  startTime: string;
  endTime: string;
  examType: "THEORY" | "ONLINE";
  notes: string;
  onlineExamId: string;
  sectionId: string;
};

const blankRow = (): Row => ({
  subjectId: "",
  examDate: "",
  startTime: "09:00",
  endTime: "11:00",
  examType: "THEORY",
  notes: "",
  onlineExamId: "",
  sectionId: "",
});

function hhmm(t?: string) {
  return t ? t.slice(0, 5) : "";
}

export default function DateSheetsPage() {
  const { user } = useSession();
  const canManage = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";
  const { classes, sections, className } = useLookups();
  const sessions = useAsync(() => examApi.sessions(), []);
  const [sessionId, setSessionId] = useState("");
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [status, setStatus] = useState("");
  const [academicSession, setAcademicSession] = useState("");
  const list = useAsync(
    () => examApi.dateSheets({
      sessionId: sessionId || undefined,
      classId: classId || undefined,
      sectionId: sectionId || undefined,
      published: status || undefined,
      academicSession: academicSession || undefined,
    }),
    [sessionId, classId, sectionId, status, academicSession],
  );
  const [editor, setEditor] = useState<DateSheet | null | "new">(null);
  const [printSheet, setPrintSheet] = useState<DateSheet | null>(null);

  const years = useMemo(() => {
    const set = new Set<string>();
    for (const s of sessions.data || []) if (s.academicSession) set.add(s.academicSession);
    for (const c of classes) if (c.academicSession) set.add(c.academicSession);
    return Array.from(set);
  }, [sessions.data, classes]);

  const openNew = () => setEditor("new");
  const classSections = classId ? (sections[classId] || []) : [];

  return (
    <>
      <div className="page-title">
        <div>
          <h1>Date sheet</h1>
          <p>Schedule internal exams by class. Theory is the default. Online rows link an existing online exam and do not start it.</p>
        </div>
        {canManage ? <Button kind="brass" onClick={openNew}>New date sheet</Button> : null}
      </div>
      <div className="card date-sheet-toolbar" style={{ marginBottom: 16 }}>
        <div className="grid two date-sheet-filters">
          <Field label="Academic session">
            <select value={academicSession} onChange={(e) => setAcademicSession(e.target.value)}>
              <option value="">All</option>
              {years.map((y) => <option key={y} value={y}>{y}</option>)}
            </select>
          </Field>
          <Field label="Exam">
            <select value={sessionId} onChange={(e) => setSessionId(e.target.value)}>
              <option value="">All exams</option>
              {(sessions.data || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Class">
            <select value={classId} onChange={(e) => { setClassId(e.target.value); setSectionId(""); }}>
              <option value="">All classes</option>
              {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Section">
            <select value={sectionId} onChange={(e) => setSectionId(e.target.value)} disabled={!classId}>
              <option value="">All sections</option>
              {classSections.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Status">
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All</option>
              <option value="true">Published</option>
              <option value="false">Draft</option>
            </select>
          </Field>
        </div>
      </div>
      <QueryState status={list} label="date sheets">
        {(list.data || []).length === 0 ? (
          <Empty title="No date sheets yet" hint={canManage ? "Create a draft for a class, then preview and publish it." : "Published date sheets for your class will appear here."} />
        ) : (
          <Table
            headers={["Exam", "Class", "Status", "Subjects", "Type", ""]}
            rows={(list.data || []).map((sheet) => [
              <span key="e">{sheet.examName || "Exam"}{sheet.academicSession ? ` · ${sheet.academicSession}` : ""}</span>,
              sheet.className || className(sheet.classId),
              sheet.published ? (sheet.historical ? "Published · closed" : "Published") : "Draft",
              String(sheet.entries?.length || 0),
              (sheet.entries || []).some((e) => e.examType === "ONLINE")
                ? ((sheet.entries || []).every((e) => e.examType === "ONLINE") ? "Online" : "Theory + Online")
                : "Theory",
              <div key="a" className="row" style={{ flexWrap: "wrap" }}>
                <Button kind="ghost" onClick={() => setPrintSheet(sheet)}>Preview / print</Button>
                {canManage && !sheet.historical ? (
                  <Button kind="ghost" onClick={() => setEditor(sheet)}>{sheet.published ? "View" : "Edit"}</Button>
                ) : null}
              </div>,
            ])}
          />
        )}
      </QueryState>
      {editor ? (
        <DateSheetEditor
          existing={editor === "new" ? null : editor}
          canManage={canManage}
          examSessions={sessions.data || []}
          onClose={() => setEditor(null)}
          onSaved={() => { setEditor(null); void list.reload(); }}
        />
      ) : null}
      {printSheet ? (
        <DateSheetPrint sheet={printSheet} onClose={() => setPrintSheet(null)} />
      ) : null}
    </>
  );
}

function DateSheetEditor({
  existing,
  canManage,
  examSessions,
  onClose,
  onSaved,
}: {
  existing: DateSheet | null;
  canManage: boolean;
  examSessions: { id: string; name: string; academicSession?: string }[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { classes, sections } = useLookups();
  const online = useAsync(() => onlineExamApi.list().catch(() => []), []);
  const toast = useToast();
  const locked = Boolean(existing?.published || existing?.historical) || !canManage;
  const [examSessionId, setExamSessionId] = useState(existing?.examSessionId || "");
  const [classId, setClassId] = useState(existing?.classId || "");
  const [sectionIds, setSectionIds] = useState<string[]>(() => {
    const ids = (existing?.entries || []).map((e) => e.sectionId).filter(Boolean) as string[];
    return Array.from(new Set(ids));
  });
  const [notes, setNotes] = useState(existing?.notes || "");
  const [rows, setRows] = useState<Row[]>(() => {
    const list = existing?.entries || [];
    if (!list.length) return [blankRow()];
    return list.map((e) => ({
      subjectId: e.subjectId,
      examDate: (e.examDate || "").slice(0, 10),
      startTime: hhmm(e.startTime) || "09:00",
      endTime: hhmm(e.endTime) || "11:00",
      examType: e.examType === "ONLINE" ? "ONLINE" : "THEORY",
      notes: e.notes || "",
      onlineExamId: e.onlineExamId || "",
      sectionId: e.sectionId || "",
    }));
  });
  const classSubjects = useAsync(
    () => (classId ? academicApi.classSubjects(classId) : Promise.resolve([])),
    [classId],
    Boolean(classId),
  );
  const subjectOptions = classSubjects.data?.length ? classSubjects.data : [];
  const classSecs = classId ? (sections[classId] || []) : [];

  const payload = () => ({
    examSessionId,
    classId,
    notes,
    entries: rows.map((r) => ({
      subjectId: r.subjectId,
      examDate: r.examDate,
      startTime: r.startTime || null,
      endTime: r.endTime || null,
      examType: r.examType || "THEORY",
      notes: r.notes || null,
      onlineExamId: r.examType === "ONLINE" && r.onlineExamId ? r.onlineExamId : null,
      sectionId: r.sectionId || null,
      sectionIds: existing ? [] : sectionIds,
    })),
  });

  return (
    <Modal title={existing ? `${existing.examName || "Date sheet"} · ${existing.className || ""}` : "New date sheet"} open onClose={onClose}>
      {locked ? <p className="hint">This date sheet is view-only{existing?.historical ? " because the exam period is closed." : existing?.published ? ". Unpublish it to edit." : "."}</p> : null}
      <Form busyLabel="Saving…" onSubmit={async () => {
        if (locked) return;
        for (const r of rows) {
          if (r.startTime && r.endTime && r.endTime <= r.startTime) {
            throw new Error("End time must be later than start time.");
          }
          if (r.examType === "ONLINE" && !r.onlineExamId) {
            throw new Error("Link an existing online exam, or switch the row back to Theory.");
          }
        }
        if (existing) await examApi.updateDateSheet(existing.id, payload());
        else await examApi.createDateSheet(payload());
        toast("ok", "Date sheet saved as draft");
        onSaved();
      }}>
        <div className="grid two">
          <Field label="Exam">
            <select value={examSessionId} onChange={(e) => setExamSessionId(e.target.value)} required disabled={locked || Boolean(existing)}>
              <option value="">Select exam</option>
              {examSessions.map((s) => <option key={s.id} value={s.id}>{s.name}{s.academicSession ? ` · ${s.academicSession}` : ""}</option>)}
            </select>
          </Field>
          <Field label="Class">
            <select value={classId} onChange={(e) => { setClassId(e.target.value); setSectionIds([]); }} required disabled={locked || Boolean(existing)}>
              <option value="">Select class</option>
              {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
        </div>
        {classId && classSubjects.data && !classSubjects.data.length ? (
          <p className="hint">Assign subjects to this class under Academics before scheduling them.</p>
        ) : null}
        <Field label="Sections (leave empty for every section)">
          <div className="row" style={{ flexWrap: "wrap", gap: 8 }}>
            {classSecs.map((s) => (
              <label key={s.id} className="hint" style={{ display: "flex", gap: 6, alignItems: "center" }}>
                <input
                  type="checkbox"
                  disabled={locked}
                  checked={sectionIds.includes(s.id)}
                  onChange={(e) => setSectionIds((cur) => e.target.checked ? [...cur, s.id] : cur.filter((id) => id !== s.id))}
                />
                {s.name}
              </label>
            ))}
            {!classSecs.length ? <span className="hint">All sections of this class</span> : null}
          </div>
        </Field>
        <Field label="Instructions (optional)">
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} disabled={locked} rows={2} />
        </Field>
        <div className="row" style={{ justifyContent: "space-between", margin: "8px 0" }}>
          <strong>Subject schedule</strong>
          {!locked ? <Button kind="ghost" onClick={() => setRows((r) => [...r, blankRow()])}>Add subject</Button> : null}
        </div>
        {rows.map((row, i) => {
          const linked = (online.data || []).filter((ex) => String(ex.classId || "") === classId && String(ex.subjectId || "") === row.subjectId);
          return (
            <div key={i} className="card" style={{ marginBottom: 10 }}>
              <div className="grid two">
                <Field label="Subject">
                  <select value={row.subjectId} disabled={locked} required onChange={(e) => setRows((all) => all.map((r, n) => n === i ? { ...r, subjectId: e.target.value, onlineExamId: "" } : r))}>
                    <option value="">Select subject</option>
                    {subjectOptions.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </Field>
                <Field label="Date">
                  <input type="date" value={row.examDate} disabled={locked} required onChange={(e) => setRows((all) => all.map((r, n) => n === i ? { ...r, examDate: e.target.value } : r))} />
                </Field>
                <Field label="Start">
                  <input type="time" value={row.startTime} disabled={locked} required onChange={(e) => setRows((all) => all.map((r, n) => n === i ? { ...r, startTime: e.target.value } : r))} />
                </Field>
                <Field label="End">
                  <input type="time" value={row.endTime} disabled={locked} required onChange={(e) => setRows((all) => all.map((r, n) => n === i ? { ...r, endTime: e.target.value } : r))} />
                </Field>
                <Field label="Type">
                  <select value={row.examType} disabled={locked} onChange={(e) => setRows((all) => all.map((r, n) => n === i ? { ...r, examType: e.target.value as Row["examType"], onlineExamId: e.target.value === "THEORY" ? "" : r.onlineExamId } : r))}>
                    <option value="THEORY">Theory</option>
                    <option value="ONLINE">Online</option>
                  </select>
                </Field>
                {row.examType === "ONLINE" ? (
                  <Field label="Online exam">
                    <select value={row.onlineExamId} disabled={locked} required onChange={(e) => setRows((all) => all.map((r, n) => n === i ? { ...r, onlineExamId: e.target.value } : r))}>
                      <option value="">Select existing online exam</option>
                      {linked.map((ex) => <option key={String(ex.id)} value={String(ex.id)}>{String(ex.title || "Online exam")}</option>)}
                    </select>
                    {!linked.length ? <span className="hint">Create the online exam first. This date sheet will not start or publish it.</span> : null}
                  </Field>
                ) : null}
              </div>
              <Field label="Notes (optional)">
                <input value={row.notes} disabled={locked} onChange={(e) => setRows((all) => all.map((r, n) => n === i ? { ...r, notes: e.target.value } : r))} />
              </Field>
              {!locked && rows.length > 1 ? (
                <Button kind="ghost" onClick={() => setRows((all) => all.filter((_, n) => n !== i))}>Remove</Button>
              ) : null}
            </div>
          );
        })}
        <p className="hint">Times use the school timezone (Asia/Karachi). Theory is in-person and does not create an online exam.</p>
        <div className="row">
          {!locked ? <Button type="submit" kind="brass" loadingText="Saving…">Save draft</Button> : null}
          {existing && canManage && !existing.historical && !existing.published ? (
            <Button kind="ok" loadingText="Publishing…" onClick={async () => {
              await examApi.publishDateSheet(existing.id);
              toast("ok", "Date sheet published");
              onSaved();
            }}>Publish</Button>
          ) : null}
          {existing && canManage && existing.published && !existing.historical ? (
            <Button kind="ghost" loadingText="Unpublishing…" onClick={async () => {
              await examApi.unpublishDateSheet(existing.id);
              toast("ok", "Date sheet unpublished");
              onSaved();
            }}>Unpublish</Button>
          ) : null}
          {existing && canManage && !existing.published && !existing.historical ? (
            <Button kind="danger" loadingText="Deleting…" onClick={async () => {
              if (!window.confirm("Delete this draft date sheet?")) return;
              await examApi.removeDateSheet(existing.id);
              toast("ok", "Date sheet deleted");
              onSaved();
            }}>Delete draft</Button>
          ) : null}
        </div>
      </Form>
    </Modal>
  );
}

function DateSheetPrint({ sheet, onClose }: { sheet: DateSheet; onClose: () => void }) {
  const rows: DateSheetEntry[] = sheet.entries || [];
  return (
    <Modal title="Date sheet preview" open onClose={onClose}>
      <div className="date-sheet-print">
        <p className="kicker">{sheet.published ? "Published" : "Draft preview"}</p>
        <h2 style={{ margin: "4px 0 8px" }}>{sheet.examName} · {sheet.className}</h2>
        {sheet.academicSession ? <p className="hint">{sheet.academicSession}</p> : null}
        {sheet.notes ? <p>{sheet.notes}</p> : null}
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Subject</th>
              <th>Section</th>
              <th>Time</th>
              <th>Type</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={r.id || i}>
                <td>{fmtDate(r.examDate)}</td>
                <td>{r.subjectName || "—"}</td>
                <td>{r.sectionName || "All sections"}</td>
                <td>{fmtTime(r.startTime)} – {fmtTime(r.endTime)}</td>
                <td>{r.examType === "ONLINE" ? "Online" : "Theory"}{r.onlineExamTitle ? ` · ${r.onlineExamTitle}` : ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!rows.length ? <Empty title="No subjects scheduled" /> : null}
      </div>
      <div className="row date-sheet-toolbar" style={{ marginTop: 16 }}>
        <Button kind="brass" onClick={() => window.print()}>Print / save PDF</Button>
        <Button kind="ghost" onClick={onClose}>Close</Button>
      </div>
    </Modal>
  );
}
