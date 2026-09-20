import { useState } from "react";
import { academicApi, attendanceApi, examApi, fileApi, homeworkApi, studentApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { dayName, fmtDate, pretty, today } from "../../lib/format";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import { Badge, Button, Empty, ErrorBox, Field, FileLink, Form, Loading, Modal, Table, studentLabel, useAsync } from "../../ui/kit";
import ChildSwitch, { useActiveStudentId } from "./ChildSwitch";
import type { AttendanceStatus, StudentUser } from "../../lib/types";

function useStudentDirectory(enabled: boolean) {
  const list = useAsync(() => (enabled ? studentApi.list() : Promise.resolve(null)), [enabled]);
  const map = new Map<string, StudentUser>();
  for (const s of list.data?.content || []) map.set(s.id, s);
  const nameOf = (id: string) => {
    const s = map.get(id);
    return s ? studentLabel(s) : id.slice(0, 8);
  };
  return { students: list.data?.content || [], nameOf };
}

export function AttendancePage() {
  const { user } = useSession();
  const teacher = user?.role === "TEACHER";
  const staff = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL" || teacher;
  const { classes, sections } = useLookups();
  const dir = useStudentDirectory(staff);
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const slots = useAsync(() => {
    if (teacher) return academicApi.teacherTimetable();
    if (classId && sectionId) return academicApi.timetable(classId, sectionId);
    return Promise.resolve([]);
  }, [teacher, classId, sectionId]);
  const [slotId, setSlotId] = useState("");
  const [date, setDate] = useState(today());
  const roster = useAsync(() => (slotId ? attendanceApi.roster(slotId) : Promise.resolve([])), [slotId]);
  const [marks, setMarks] = useState<Record<string, AttendanceStatus>>({});
  const toast = useToast();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const me = useAsync(() => (user?.role === "STUDENT" ? studentApi.me() : Promise.resolve(null)), [user?.role]);
  const sid = useActiveStudentId(me.data?.id);
  const history = useAsync(() => {
    if (!sid) return Promise.resolve([]);
    const from = new Date(); from.setDate(from.getDate() - 30);
    return attendanceApi.studentHistory(sid, from.toISOString().slice(0, 10), today());
  }, [sid]);

  if (user?.role === "PARENT" || user?.role === "STUDENT") {
    return (
      <>
        <div className="page-title"><div><h1>Attendance</h1><p>Last 30 days from lecture rolls</p></div></div>
        {user.role === "PARENT" ? <ChildSwitch childrenList={kids.data || []} /> : null}
        {history.loading ? <Loading /> : history.error ? <ErrorBox error={history.error} /> : (
          <Table headers={["Date", "Status", "Remarks"]} rows={(history.data || []).map((r) => [r.attendanceDate, <Badge key={r.id} value={r.status} />, r.remarks || "—"])} />
        )}
      </>
    );
  }

  return (
    <>
      <div className="page-title"><div><h1>Lecture attendance</h1><p>Mark the roster for a timetable slot</p></div></div>
      <div className="row" style={{ marginBottom: 12 }}>
        {!teacher ? (
          <>
            <select className="search" value={classId} onChange={(e) => { setClassId(e.target.value); setSectionId(""); }}>
              <option value="">Class</option>
              {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            <select className="search" value={sectionId} onChange={(e) => setSectionId(e.target.value)}>
              <option value="">Section</option>
              {(sections[classId] || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </>
        ) : null}
        <select className="search" value={slotId} onChange={(e) => setSlotId(e.target.value)}>
          <option value="">Select lecture</option>
          {(slots.data || []).map((s) => (
            <option key={s.id} value={s.id}>{dayName(s.dayOfWeek)} {s.startTime}</option>
          ))}
        </select>
        <input className="search" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        <Button
          kind="brass"
          disabled={!slotId}
          onClick={async () => {
            try {
              const items = (roster.data || []).map((st) => ({ studentId: st.id, status: marks[st.id] || "PRESENT" }));
              await attendanceApi.markLecture(slotId, { date, marks: items });
              toast("ok", "Attendance saved");
            } catch (e) { toast("err", e instanceof Error ? e.message : "Save failed"); }
          }}
        >
          Save roll
        </Button>
      </div>
      {roster.loading ? <Loading /> : roster.error ? <ErrorBox error={roster.error} /> : (
        <Table
          headers={["Student", "Status"]}
          rows={(roster.data || []).map((st) => [
            dir.nameOf(st.id) !== st.id.slice(0, 8) ? dir.nameOf(st.id) : studentLabel(st),
            <select key={st.id} className="search" value={marks[st.id] || "PRESENT"} onChange={(e) => setMarks((m) => ({ ...m, [st.id]: e.target.value as AttendanceStatus }))}>
              {["PRESENT", "ABSENT", "LATE", "LEAVE"].map((s) => <option key={s}>{s}</option>)}
            </select>,
          ])}
        />
      )}
    </>
  );
}

export function HomeworkPage() {
  const { user } = useSession();
  const { classes, sections, subjects, className, subjectName } = useLookups();
  const list = useAsync(() => homeworkApi.list(), []);
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ classId: "", sectionId: "", subjectId: "", title: "", description: "", dueDate: today() });
  const [active, setActive] = useState<string | null>(null);
  const subs = useAsync(() => (active && user?.role !== "STUDENT" && user?.role !== "PARENT" ? homeworkApi.submissions(active) : Promise.resolve([])), [active, user?.role]);
  const me = useAsync(() => (user?.role === "STUDENT" ? studentApi.me() : Promise.resolve(null)), [user?.role]);
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const sid = useActiveStudentId(me.data?.id);
  const child = (kids.data || []).find((k) => k.id === sid);
  const dir = useStudentDirectory(user?.role !== "STUDENT" && user?.role !== "PARENT");
  const [notes, setNotes] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const homeworkRows = (list.data || []).filter((h) => {
    if (user?.role !== "PARENT") return true;
    if (!child) return false;
    return h.classId === child.classId && h.sectionId === child.sectionId;
  });
  const current = (list.data || []).find((h) => h.id === active);

  return (
    <>
      <div className="page-title">
        <div><h1>Homework</h1><p>Assignments and submissions</p></div>
        {user?.role === "TEACHER" || user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL" ? (
          <Button kind="brass" onClick={() => setOpen(true)}>Assign</Button>
        ) : null}
      </div>
      {user?.role === "PARENT" ? <ChildSwitch childrenList={kids.data || []} /> : null}
      {list.loading ? <Loading /> : list.error ? <ErrorBox error={list.error} /> : (
        <Table
          headers={["Title", "Class", "Subject", "Due", ""]}
          rows={homeworkRows.map((h) => [
            h.title, className(h.classId), subjectName(h.subjectId), fmtDate(h.dueDate),
            <Button key={h.id} kind="ghost" onClick={() => setActive(h.id)}>Open</Button>,
          ])}
        />
      )}
      <Modal title="Assign homework" open={open} onClose={() => setOpen(false)}>
        <Form onSubmit={async () => {
          try {
            await homeworkApi.create(form);
            toast("ok", "Homework assigned"); setOpen(false); void list.reload();
          } catch (e) { toast("err", e instanceof Error ? e.message : "Failed"); }
        }}>
          <Field label="Class"><select value={form.classId} onChange={(e) => setForm({ ...form, classId: e.target.value })} required><option value="">Select</option>{classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
          <Field label="Section"><select value={form.sectionId} onChange={(e) => setForm({ ...form, sectionId: e.target.value })} required><option value="">Select</option>{(sections[form.classId] || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
          <Field label="Subject"><select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} required><option value="">Select</option>{subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
          <Field label="Title"><input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required /></Field>
          <Field label="Description"><textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
          <Field label="Due"><input type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} /></Field>
          <Button type="submit" kind="brass">Publish</Button>
        </Form>
      </Modal>
      <Modal title="Homework" open={!!active} onClose={() => setActive(null)}>
        {current?.attachments?.length ? (
          <div className="row" style={{ marginBottom: 12 }}>
            {current.attachments.map((a) => (
              <FileLink key={a.id || a.fileUrl} href={a.fileUrl} label={a.fileName || "Attachment"} />
            ))}
          </div>
        ) : null}
        {(user?.role === "STUDENT" || user?.role === "PARENT") && sid ? (
          <Form onSubmit={async () => {
            try {
              let fileUrl: string | undefined;
              if (file) fileUrl = (await fileApi.upload(file)).url;
              await homeworkApi.submit(active!, { studentId: sid, fileUrl, notes });
              toast("ok", "Submitted"); setActive(null); setFile(null); setNotes("");
            } catch (e) { toast("err", e instanceof Error ? e.message : "Submit failed"); }
          }}>
            <Field label="Notes"><textarea value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
            <Field label="File"><input type="file" accept=".pdf,image/*,.doc,.docx" onChange={(e) => setFile(e.target.files?.[0] || null)} /></Field>
            <Button type="submit" kind="brass">Submit work</Button>
          </Form>
        ) : subs.loading ? <Loading /> : subs.error ? <ErrorBox error={subs.error} /> : (
          <Table
            headers={["Student", "Status", "Notes", "File", ""]}
            rows={(subs.data || []).map((s) => [
              dir.nameOf(s.studentId), <Badge key={s.id} value={s.status} />, s.notes || "—",
              <FileLink key={`f${s.id}`} href={s.fileUrl} />,
              <Button key={`rv${s.id}`} kind="ghost" onClick={async () => { await homeworkApi.review(s.id, "Reviewed"); toast("ok", "Reviewed"); void subs.reload(); }}>Review</Button>,
            ])}
          />
        )}
      </Modal>
    </>
  );
}

export function ExamsPage() {
  const { user } = useSession();
  const { subjects, subjectName } = useLookups();
  const sessions = useAsync(() => examApi.sessions(), []);
  const toast = useToast();
  const [name, setName] = useState("");
  const [sessionId, setSessionId] = useState("");
  const results = useAsync(() => (sessionId && user?.role !== "STUDENT" && user?.role !== "PARENT" ? examApi.sessionResults(sessionId) : Promise.resolve([])), [sessionId, user?.role]);
  const me = useAsync(() => (user?.role === "STUDENT" ? studentApi.me() : Promise.resolve(null)), [user?.role]);
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const sid = useActiveStudentId(me.data?.id);
  const mine = useAsync(() => (sessionId && sid ? examApi.studentResult(sessionId, sid) : Promise.resolve(null)), [sessionId, sid]);
  const dir = useStudentDirectory(user?.role !== "STUDENT" && user?.role !== "PARENT");
  const [mark, setMark] = useState({ studentId: "", subjectId: "", totalMarks: "100", obtainedMarks: "0" });

  return (
    <>
      <div className="page-title"><div><h1>Marks & results</h1><p>Offline exam sessions — published results only for families</p></div></div>
      {user?.role === "PARENT" ? <ChildSwitch childrenList={kids.data || []} /> : null}
      {(user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL") ? (
        <Form onSubmit={async () => { await examApi.createSession({ name }); toast("ok", "Session created"); void sessions.reload(); }}>
          <div className="row">
            <input className="search" placeholder="Mid-term 2026" value={name} onChange={(e) => setName(e.target.value)} />
            <Button type="submit">Create session</Button>
          </div>
        </Form>
      ) : null}
      <Field label="Exam session">
        <select value={sessionId} onChange={(e) => setSessionId(e.target.value)}>
          <option value="">Select</option>
          {(sessions.data || []).map((s) => <option key={s.id} value={s.id}>{s.name} {s.published ? "(published)" : ""}</option>)}
        </select>
      </Field>
      {user?.role === "STUDENT" || user?.role === "PARENT" ? (
        mine.loading ? <Loading /> : mine.error ? <ErrorBox error={mine.error} /> : mine.data ? (
          <div className="card">
            <p>Grade {mine.data.grade} · {mine.data.percentage}% · {pretty(mine.data.passStatus)}</p>
            <Table headers={["Subject", "Obtained", "Total"]} rows={(mine.data.subjects || []).map((r) => [subjectName(r.subjectId), r.obtainedMarks, r.totalMarks])} />
          </div>
        ) : <Empty title="Select a published session" />
      ) : (
        <>
          {(user?.role === "TEACHER" || user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL") && sessionId ? (
            <div className="card" style={{ marginBottom: 12 }}>
              <h3>Enter marks</h3>
              <Form onSubmit={async () => {
                try {
                  await examApi.upsertResult(sessionId, { ...mark, totalMarks: Number(mark.totalMarks), obtainedMarks: Number(mark.obtainedMarks) });
                  toast("ok", "Result saved"); void results.reload();
                } catch (e) { toast("err", e instanceof Error ? e.message : "Failed"); }
              }}>
                <Field label="Student">
                  <select value={mark.studentId} onChange={(e) => setMark({ ...mark, studentId: e.target.value })} required>
                    <option value="">Select</option>
                    {dir.students.map((s) => <option key={s.id} value={s.id}>{studentLabel(s)}</option>)}
                  </select>
                </Field>
                <Field label="Subject">
                  <select value={mark.subjectId} onChange={(e) => setMark({ ...mark, subjectId: e.target.value })} required>
                    <option value="">Select</option>
                    {subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </Field>
                <div className="row">
                  <Field label="Obtained"><input value={mark.obtainedMarks} onChange={(e) => setMark({ ...mark, obtainedMarks: e.target.value })} /></Field>
                  <Field label="Total"><input value={mark.totalMarks} onChange={(e) => setMark({ ...mark, totalMarks: e.target.value })} /></Field>
                </div>
                <Button type="submit" kind="brass">Save marks</Button>
              </Form>
              {(user.role === "SCHOOL_ADMIN" || user.role === "PRINCIPAL") ? (
                <Button kind="ok" onClick={async () => { await examApi.publish(sessionId); toast("ok", "Published"); void sessions.reload(); }}>Publish results</Button>
              ) : null}
            </div>
          ) : null}
          {results.loading ? <Loading /> : results.error ? <ErrorBox error={results.error} /> : (
            <Table headers={["Student", "Subject", "Marks", "Grade"]} rows={(results.data || []).map((r) => [dir.nameOf(r.studentId), subjectName(r.subjectId), `${r.obtainedMarks}/${r.totalMarks}`, r.grade || "—"])} />
          )}
        </>
      )}
    </>
  );
}
