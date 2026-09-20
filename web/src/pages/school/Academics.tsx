import { useState } from "react";
import { academicApi, campusApi, userApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { dayName, fmtTime } from "../../lib/format";
import { useToast } from "../../lib/toast";
import { Button, Empty, Field, Form, Modal, QueryState, Table, useAsync } from "../../ui/kit";
import { useSession } from "../../lib/session";
import { studentApi } from "../../api/services";
import { useActiveStudentId } from "./ChildSwitch";
import ChildSwitch from "./ChildSwitch";

export function CampusesPage() {
  const list = useAsync(() => campusApi.list());
  const [open, setOpen] = useState(false);
  const toast = useToast();
  const [form, setForm] = useState({ name: "", code: "", city: "", phone: "" });
  return (
    <>
      <div className="page-title"><div><h1>Campuses</h1><p>Physical school sites</p></div>
        <Button kind="brass" onClick={() => setOpen(true)}>Add campus</Button></div>
      <QueryState status={list} label="campuses">
        <Table headers={["Name", "Code", "City", "Phone"]} rows={(list.data || []).map((c) => [c.name, c.code, c.city || "—", c.phone || "—"])} />
      </QueryState>
      <Modal title="New campus" open={open} onClose={() => setOpen(false)}>
        <Form busyLabel="Saving…" onSubmit={async () => {
          await campusApi.create(form); toast("ok", "Campus saved"); setOpen(false); void list.reload();
        }}>
          <Field label="Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
          <Field label="Code"><input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required /></Field>
          <Field label="City"><input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></Field>
          <Field label="Phone"><input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
          <Button type="submit" kind="brass" loadingText="Saving…">Save</Button>
        </Form>
      </Modal>
    </>
  );
}

export function AcademicsPage() {
  const { user } = useSession();
  const canWrite = user?.role === "SCHOOL_ADMIN";
  const { classes, subjects, sections, reload, loading, error } = useLookups();
  const lookupStatus = { data: loading && !classes.length && !subjects.length ? null : classes, error, loading, reload };
  const toast = useToast();
  const [cOpen, setCOpen] = useState(false);
  const [sOpen, setSOpen] = useState(false);
  const [className, setClassName] = useState("");
  const [grade, setGrade] = useState("");
  const [subjectName, setSubjectName] = useState("");
  const [code, setCode] = useState("");
  const [sectionFor, setSectionFor] = useState("");
  const [sectionName, setSectionName] = useState("");
  return (
    <>
      <div className="page-title"><div><h1>Academics</h1><p>Classes, sections and subjects</p></div>
        {canWrite ? (
          <div className="row">
            <Button onClick={() => setCOpen(true)}>New class</Button>
            <Button kind="ghost" onClick={() => setSOpen(true)}>New subject</Button>
          </div>
        ) : null}
      </div>
      <QueryState status={lookupStatus} label="academics">
      <div className="grid two">
        <div className="card">
          <h3>Classes</h3>
          {!classes.length ? <Empty title="No classes" /> : classes.map((c) => (
            <div key={c.id} style={{ padding: "10px 0", borderBottom: "1px solid var(--line)" }}>
              <strong>{c.name}</strong> · Grade {c.grade || "—"}
              <div style={{ color: "var(--muted)", fontSize: 13 }}>
                Sections: {(sections[c.id] || []).map((s) => s.name).join(", ") || "none"}
              </div>
              {canWrite ? <Button kind="ghost" onClick={() => setSectionFor(c.id)}>Add section</Button> : null}
            </div>
          ))}
        </div>
        <div className="card">
          <h3>Subjects</h3>
          <Table headers={["Name", "Code"]} rows={subjects.map((s) => [s.name, s.code])} />
        </div>
      </div>
      </QueryState>
      <Modal title="New class" open={cOpen} onClose={() => setCOpen(false)}>
        <Form busyLabel="Saving…" onSubmit={async () => {
          await academicApi.createClass({ name: className, grade });
          toast("ok", "Class created"); setCOpen(false); void reload();
        }}>
          <Field label="Name"><input value={className} onChange={(e) => setClassName(e.target.value)} required /></Field>
          <Field label="Grade"><input value={grade} onChange={(e) => setGrade(e.target.value)} /></Field>
          <Button type="submit" kind="brass" loadingText="Saving…">Save</Button>
        </Form>
      </Modal>
      <Modal title="New subject" open={sOpen} onClose={() => setSOpen(false)}>
        <Form busyLabel="Saving…" onSubmit={async () => {
          await academicApi.createSubject({ name: subjectName, code });
          toast("ok", "Subject created"); setSOpen(false); void reload();
        }}>
          <Field label="Name"><input value={subjectName} onChange={(e) => setSubjectName(e.target.value)} required /></Field>
          <Field label="Code"><input value={code} onChange={(e) => setCode(e.target.value)} required /></Field>
          <Button type="submit" kind="brass" loadingText="Saving…">Save</Button>
        </Form>
      </Modal>
      <Modal title="New section" open={!!sectionFor} onClose={() => setSectionFor("")}>
        <Form busyLabel="Saving…" onSubmit={async () => {
          await academicApi.createSection(sectionFor, sectionName);
          toast("ok", "Section created"); setSectionFor(""); void reload();
        }}>
          <Field label="Name"><input value={sectionName} onChange={(e) => setSectionName(e.target.value)} required /></Field>
          <Button type="submit" kind="brass" loadingText="Saving…">Save</Button>
        </Form>
      </Modal>
    </>
  );
}

export function TimetablePage() {
  const { user } = useSession();
  const { classes, sections, subjects, className, subjectName } = useLookups();
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const kids = useAsyncKids(user?.role === "PARENT");
  const meStudent = useAsync(() => (user?.role === "STUDENT" ? studentApi.me() : Promise.resolve(null)), [user?.role]);
  const childId = useActiveStudentId(null);
  const child = (kids.data || []).find((k) => k.id === childId);
  const effectiveClass = user?.role === "PARENT" ? child?.classId : user?.role === "STUDENT" ? meStudent.data?.classId : classId;
  const effectiveSection = user?.role === "PARENT" ? child?.sectionId : user?.role === "STUDENT" ? meStudent.data?.sectionId : sectionId;
  const teacher = user?.role === "TEACHER";
  const slots = useAsync(
    () => teacher
      ? academicApi.teacherTimetable()
      : effectiveClass && effectiveSection
        ? academicApi.timetable(effectiveClass, effectiveSection)
        : Promise.resolve([]),
    [teacher, effectiveClass, effectiveSection],
  );
  const toast = useToast();
  const teachers = useAsync(() => (user?.role === "SCHOOL_ADMIN" ? userApi.list({ role: "TEACHER" }) : Promise.resolve(null)), [user?.role]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ classId: "", sectionId: "", subjectId: "", teacherUserId: "", dayOfWeek: 1, startTime: "08:00", endTime: "08:45" });

  return (
    <>
      <div className="page-title"><div><h1>Timetable</h1><p>Lectures stored as timetable slots</p></div>
        {user?.role === "SCHOOL_ADMIN" ? <Button kind="brass" onClick={() => setOpen(true)}>Add lecture</Button> : null}
      </div>
      {user?.role === "PARENT" ? <ChildSwitch childrenList={kids.data || []} /> : null}
      {!teacher && user?.role !== "PARENT" && user?.role !== "STUDENT" ? (
        <div className="row" style={{ marginBottom: 12 }}>
          <select className="search" value={classId} onChange={(e) => { setClassId(e.target.value); setSectionId(""); }}>
            <option value="">Class</option>
            {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <select className="search" value={sectionId} onChange={(e) => setSectionId(e.target.value)}>
            <option value="">Section</option>
            {(sections[classId] || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
      ) : null}
      <QueryState status={slots} label="timetable">
        <Table
          headers={["Day", "Time", "Class", "Subject"]}
          rows={(slots.data || []).map((s) => [dayName(s.dayOfWeek), `${fmtTime(s.startTime)}–${fmtTime(s.endTime)}`, className(s.classId), subjectName(s.subjectId)])}
        />
      </QueryState>
      <Modal title="Lecture" open={open} onClose={() => setOpen(false)}>
        <Form busyLabel="Saving…" onSubmit={async () => {
          await academicApi.createSlot({ ...form, startTime: form.startTime.length === 5 ? form.startTime + ":00" : form.startTime, endTime: form.endTime.length === 5 ? form.endTime + ":00" : form.endTime, dayOfWeek: Number(form.dayOfWeek) });
          toast("ok", "Lecture created"); setOpen(false); void slots.reload();
        }}>
          <Field label="Class">
            <select value={form.classId} onChange={(e) => setForm({ ...form, classId: e.target.value })} required>
              <option value="">Select</option>
              {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Section">
            <select value={form.sectionId} onChange={(e) => setForm({ ...form, sectionId: e.target.value })} required>
              <option value="">Select</option>
              {(sections[form.classId] || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Subject">
            <select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} required>
              <option value="">Select</option>
              {subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Teacher">
            <select value={form.teacherUserId} onChange={(e) => setForm({ ...form, teacherUserId: e.target.value })} required>
              <option value="">Select</option>
              {(teachers.data?.content || []).map((t) => <option key={t.id} value={t.id}>{t.fullName || t.username}</option>)}
            </select>
          </Field>
          <Field label="Day">
            <select value={form.dayOfWeek} onChange={(e) => setForm({ ...form, dayOfWeek: Number(e.target.value) })}>
              {[1, 2, 3, 4, 5, 6, 7].map((d) => <option key={d} value={d}>{dayName(d)}</option>)}
            </select>
          </Field>
          <Field label="Start"><input type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} /></Field>
          <Field label="End"><input type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} /></Field>
          <Button type="submit" kind="brass" loadingText="Saving…">Save</Button>
        </Form>
      </Modal>
    </>
  );
}

function useAsyncKids(on: boolean) {
  return useAsync(() => (on ? studentApi.children() : Promise.resolve([])), [on]);
}
