import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { academicApi, onlineExamApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import { Badge, Button, Empty, Field, Form, QueryState, Table, useAsync } from "../../ui/kit";

type QuestionDraft = {
  id?: string;
  questionType: string;
  prompt: string;
  marks?: number | string;
  optionA?: string;
  optionB?: string;
  optionC?: string;
  optionD?: string;
  correctOption?: string;
  referenceAnswer?: string;
  optionChoice?: string;
  textAnswer?: string;
  obtainedMarks?: number | string;
  autoGraded?: boolean;
};

function str(v: unknown, fallback = "") {
  return v == null ? fallback : String(v);
}
function num(v: unknown) {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}
function toLocalInput(iso?: string) {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}
function fromLocalInput(v: string) {
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) throw new Error("Start date/time is required");
  return d.toISOString();
}
function clock(seconds: number) {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  if (h) return `${h}:${String(m).padStart(2, "0")}:${String(r).padStart(2, "0")}`;
  return `${m}:${String(r).padStart(2, "0")}`;
}

function useDeadline(remainingSeconds?: unknown, deadlineAt?: unknown) {
  const end = useMemo(() => {
    if (typeof remainingSeconds === "number") return Date.now() + remainingSeconds * 1000;
    if (typeof deadlineAt === "string") {
      const t = new Date(deadlineAt).getTime();
      return Number.isNaN(t) ? null : t;
    }
    return null;
  }, [remainingSeconds, deadlineAt]);
  const [left, setLeft] = useState(() => (end ? Math.max(0, Math.floor((end - Date.now()) / 1000)) : 0));
  useEffect(() => {
    if (!end) return;
    const tick = () => setLeft(Math.max(0, Math.floor((end - Date.now()) / 1000)));
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [end]);
  return { left, end };
}

export default function OnlineExamsPage() {
  const { examId } = useParams();
  const { user } = useSession();
  const student = user?.role === "STUDENT";
  if (examId) return student ? <StudentTake examId={examId} /> : <StaffExam examId={examId} />;
  return student ? <StudentList /> : <StaffList />;
}

function StudentList() {
  const list = useAsync(() => onlineExamApi.mine());
  const { className, subjectName } = useLookups();
  return (
    <>
      <div className="page-title"><div><h1>Online exams</h1><p>Timed papers. Remaining time comes from the server when you start.</p></div></div>
      <QueryState status={list} label="online exams">
        {!(list.data || []).length ? <Empty title="No online exams" hint="Scheduled papers for your class appear here." /> : (
          <Table
            headers={["Exam", "Subject", "Starts", "Duration", "Status", ""]}
            rows={(list.data || []).map((e) => {
              const id = str(e.id);
              const status = str(e.status);
              const attempt = str(e.attemptStatus);
              let action = <span className="hint">—</span>;
              if (attempt === "IN_PROGRESS" || status === "LIVE") action = <Link to={`/app/online-exams/${id}`} className="btn sm brass">Open</Link>;
              else if (status === "SCHEDULED") action = <Link to={`/app/online-exams/${id}`} className="btn sm ghost">Waiting</Link>;
              else if (attempt) action = <Link to={`/app/online-exams/${id}`} className="btn sm ghost">Result</Link>;
              return [
                str(e.title),
                subjectName(str(e.subjectId)) + " · " + className(str(e.classId)),
                e.startAt ? new Date(str(e.startAt)).toLocaleString() : "—",
                `${num(e.durationMinutes)} min`,
                <Badge key={id} value={attempt || status} />,
                action,
              ];
            })}
          />
        )}
      </QueryState>
    </>
  );
}

function StudentTake({ examId }: { examId: string }) {
  const toast = useToast();
  const nav = useNavigate();
  const paper = useAsync(async () => {
    const mine = await onlineExamApi.mine();
    const row = mine.find((x) => str(x.id) === examId);
    const attempt = str(row?.attemptStatus);
    const status = str(row?.status);
    if (status === "LIVE" || attempt === "IN_PROGRESS") {
      return onlineExamApi.start(examId);
    }
    if (attempt && attempt !== "IN_PROGRESS") {
      return onlineExamApi.result(examId);
    }
    if (row) return row;
    throw new Error("Exam not found for this student");
  }, [examId]);
  const data = paper.data;
  const inProgress = str(data?.attemptStatus) === "IN_PROGRESS";
  const questions = ((data?.questions as QuestionDraft[]) || []);
  const [answers, setAnswers] = useState<Record<string, { optionChoice?: string; textAnswer?: string }>>({});
  const dirty = useRef(false);
  const { left, end } = useDeadline(data?.remainingSeconds, data?.deadlineAt);

  useEffect(() => {
    const next: Record<string, { optionChoice?: string; textAnswer?: string }> = {};
    for (const q of questions) {
      if (!q.id) continue;
      next[q.id] = { optionChoice: q.optionChoice, textAnswer: q.textAnswer };
    }
    setAnswers(next);
    dirty.current = false;
  }, [data?.attemptId]);

  useEffect(() => {
    if (!inProgress) return;
    const t = window.setInterval(() => {
      if (!dirty.current) return;
      dirty.current = false;
      const body = Object.entries(answers).map(([questionId, a]) => ({ questionId, ...a }));
      void onlineExamApi.saveAnswers(examId, body).catch(() => { dirty.current = true; });
    }, 2500);
    return () => window.clearInterval(t);
  }, [answers, examId, inProgress]);

  const autoFired = useRef(false);
  useEffect(() => {
    if (!inProgress || !end || left > 0 || autoFired.current) return;
    autoFired.current = true;
    void (async () => {
      try {
        const body = Object.entries(answers).map(([questionId, a]) => ({ questionId, ...a }));
        await onlineExamApi.saveAnswers(examId, body).catch(() => undefined);
        await onlineExamApi.autoSubmit(examId);
        toast("ok", "Time is up — paper submitted");
        await paper.reload();
      } catch (e) {
        toast("err", e instanceof Error ? e.message : "Auto-submit failed");
      }
    })();
  }, [left, end, inProgress, answers, examId, paper, toast]);

  const setAns = (id: string, patch: { optionChoice?: string; textAnswer?: string }) => {
    dirty.current = true;
    setAnswers((cur) => ({ ...cur, [id]: { ...cur[id], ...patch } }));
  };

  return (
    <>
      <div className="page-title">
        <div>
          <Link to="/app/online-exams" className="kicker">← Online exams</Link>
          <h1>{str(data?.title, "Online exam")}</h1>
          <p>{str(data?.description)}</p>
        </div>
        {inProgress ? <div className="exam-timer" data-low={left < 60 ? "1" : undefined}>{clock(left)}</div> : null}
      </div>
      <QueryState status={paper} label="exam">
        {!data ? <Empty title="Exam unavailable" /> : null}
        {data && str(data.status) === "SCHEDULED" && !inProgress ? (
          <div className="card"><p>This exam has not started. Starts {new Date(str(data.startAt)).toLocaleString()}.</p></div>
        ) : null}
        {data && !inProgress && !str(data.attemptStatus) && str(data.status) !== "SCHEDULED" && str(data.status) !== "LIVE" ? (
          <div className="card"><p>This exam is closed and no attempt was recorded.</p></div>
        ) : null}
        {inProgress ? (
          <Form busyLabel="Submitting…" onSubmit={async () => {
            const body = Object.entries(answers).map(([questionId, a]) => ({ questionId, ...a }));
            await onlineExamApi.saveAnswers(examId, body);
            await onlineExamApi.submit(examId);
            toast("ok", "Submitted");
            await paper.reload();
          }}>
            {data?.shortRule ? <p className="hint">{str(data.shortRule)}</p> : null}
            {data?.longRule ? <p className="hint">{str(data.longRule)}</p> : null}
            {questions.map((q, i) => (
              <div className="card" key={q.id || i} style={{ marginBottom: 12 }}>
                <p className="kicker">{q.questionType} · {q.marks} marks</p>
                <h3 style={{ marginTop: 0 }}>{i + 1}. {q.prompt}</h3>
                {q.questionType === "MCQ" ? (
                  <div className="exam-options">
                    {(["A", "B", "C", "D"] as const).map((opt) => {
                      const label = opt === "A" ? q.optionA : opt === "B" ? q.optionB : opt === "C" ? q.optionC : q.optionD;
                      if (!label) return null;
                      return (
                        <label key={opt} className="chip">
                          <input
                            type="radio"
                            name={q.id}
                            checked={answers[q.id || ""]?.optionChoice === opt}
                            onChange={() => q.id && setAns(q.id, { optionChoice: opt })}
                          />
                          {opt}. {label}
                        </label>
                      );
                    })}
                  </div>
                ) : (
                  <Field label={q.questionType === "LONG" ? "Long answer" : "Short answer"}>
                    <textarea
                      rows={q.questionType === "LONG" ? 8 : 4}
                      value={answers[q.id || ""]?.textAnswer || ""}
                      onChange={(e) => q.id && setAns(q.id, { textAnswer: e.target.value })}
                    />
                  </Field>
                )}
              </div>
            ))}
            <Button type="submit" kind="brass" loadingText="Submitting…">Submit paper</Button>
          </Form>
        ) : data && str(data.attemptStatus) && str(data.attemptStatus) !== "IN_PROGRESS" ? (
          <div className="card">
            <Badge value={str(data.attemptStatus)} />
            <p>{str(data.message, "Your attempt is recorded.")}</p>
            {data.grade != null ? (
              <p><strong>{str(data.obtainedMarks)}</strong> / {str(data.totalMarks)} · {str(data.percentage)}% · {str(data.grade)} · {str(data.passStatus)}</p>
            ) : null}
            <Button kind="ghost" onClick={() => nav("/app/online-exams")}>Back to list</Button>
          </div>
        ) : null}
      </QueryState>
    </>
  );
}

function StaffList() {
  const list = useAsync(() => onlineExamApi.list());
  const { className, subjectName, sections, classes, subjects } = useLookups();
  const { user } = useSession();
  const toast = useToast();
  const nav = useNavigate();
  const assigns = useAsync(() => (user?.role === "TEACHER" ? academicApi.assignments() : Promise.resolve([])), [user?.role]);
  const teacher = user?.role === "TEACHER";
  const allowedClassIds = teacher ? new Set((assigns.data || []).map((a) => a.classId)) : null;
  const classOpts = allowedClassIds ? classes.filter((c) => allowedClassIds.has(c.id)) : classes;
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    title: "", description: "", classId: "", sectionId: "", subjectId: "",
    startAt: "", durationMinutes: "60", passingMarks: "", mcqMarks: "1", shortMarks: "2", longMarks: "5",
    shortRequired: "", longRequired: "",
  });
  const subjectOpts = teacher
    ? subjects.filter((s) => (assigns.data || []).some((a) => a.subjectId === s.id && a.classId === form.classId && (!form.sectionId || a.sectionId === form.sectionId)))
    : subjects;
  return (
    <>
      <div className="page-title">
        <div><h1>Online exams</h1><p>Timed MCQ, short and long papers. Answer keys never go to students.</p></div>
        <Button kind="brass" onClick={() => setOpen(true)}>New exam</Button>
      </div>
      <QueryState status={list} label="online exams">
        <Table
          headers={["Exam", "Class", "Starts", "Duration", "Status", ""]}
          rows={(list.data || []).map((e) => {
            const id = str(e.id);
            return [
              str(e.title),
              `${className(str(e.classId))} · ${subjectName(str(e.subjectId))}`,
              e.startAt ? new Date(str(e.startAt)).toLocaleString() : "—",
              `${num(e.durationMinutes)} min`,
              <Badge key={id} value={str(e.status)} />,
              <Link key={`${id}-open`} to={`/app/online-exams/${id}`} className="btn sm ghost">Open</Link>,
            ];
          })}
        />
      </QueryState>
      {open ? (
        <div className="card" style={{ marginTop: 16 }}>
          <h3>Create exam</h3>
          <Form busyLabel="Creating…" onSubmit={async () => {
            const created = await onlineExamApi.create({
              title: form.title,
              description: form.description,
              classId: form.classId,
              sectionId: form.sectionId,
              subjectId: form.subjectId,
              startAt: fromLocalInput(form.startAt),
              examDate: fromLocalInput(form.startAt).slice(0, 10),
              durationMinutes: Number(form.durationMinutes),
              passingMarks: form.passingMarks ? Number(form.passingMarks) : null,
              mcqMarks: Number(form.mcqMarks),
              shortMarks: Number(form.shortMarks),
              longMarks: Number(form.longMarks),
              shortRequired: form.shortRequired ? Number(form.shortRequired) : null,
              longRequired: form.longRequired ? Number(form.longRequired) : null,
            });
            toast("ok", "Draft created");
            nav(`/app/online-exams/${str(created.id)}`);
          }}>
            <Field label="Title"><input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required /></Field>
            <Field label="Description"><textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
            <Field label="Class">
              <select value={form.classId} onChange={(e) => setForm({ ...form, classId: e.target.value, sectionId: "", subjectId: "" })} required>
                <option value="">Select</option>
                {classOpts.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="Section">
              <select value={form.sectionId} onChange={(e) => setForm({ ...form, sectionId: e.target.value })} required>
                <option value="">Select</option>
                {(sections[form.classId] || []).filter((s) => !teacher || (assigns.data || []).some((a) => a.sectionId === s.id && a.classId === form.classId)).map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </Field>
            <Field label="Subject">
              <select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} required>
                <option value="">Select</option>
                {subjectOpts.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <Field label="Start"><input type="datetime-local" value={form.startAt} onChange={(e) => setForm({ ...form, startAt: e.target.value })} required /></Field>
            <Field label="Duration (minutes)"><input type="number" min={1} value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} required /></Field>
            <Field label="Passing marks"><input type="number" value={form.passingMarks} onChange={(e) => setForm({ ...form, passingMarks: e.target.value })} /></Field>
            <div className="row">
              <Field label="Default MCQ marks"><input type="number" value={form.mcqMarks} onChange={(e) => setForm({ ...form, mcqMarks: e.target.value })} /></Field>
              <Field label="Default short marks"><input type="number" value={form.shortMarks} onChange={(e) => setForm({ ...form, shortMarks: e.target.value })} /></Field>
              <Field label="Default long marks"><input type="number" value={form.longMarks} onChange={(e) => setForm({ ...form, longMarks: e.target.value })} /></Field>
            </div>
            <div className="row">
              <Field label="Attempt any N short"><input type="number" value={form.shortRequired} onChange={(e) => setForm({ ...form, shortRequired: e.target.value })} /></Field>
              <Field label="Attempt any N long"><input type="number" value={form.longRequired} onChange={(e) => setForm({ ...form, longRequired: e.target.value })} /></Field>
            </div>
            <div className="row">
              <Button type="submit" kind="brass" loadingText="Creating…">Create draft</Button>
              <Button kind="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            </div>
          </Form>
        </div>
      ) : null}
    </>
  );
}

function StaffExam({ examId }: { examId: string }) {
  const toast = useToast();
  const exam = useAsync(() => onlineExamApi.get(examId), [examId]);
  const attempts = useAsync(() => onlineExamApi.attempts(examId), [examId]);
  const data = exam.data;
  const [meta, setMeta] = useState({
    title: "", description: "", classId: "", sectionId: "", subjectId: "",
    startAt: "", durationMinutes: "60", passingMarks: "", mcqMarks: "1", shortMarks: "2", longMarks: "5",
    shortRequired: "", longRequired: "",
  });
  const [questions, setQuestions] = useState<QuestionDraft[]>([]);
  const [grading, setGrading] = useState<string | null>(null);
  const detail = useAsync(() => (grading ? onlineExamApi.attempt(examId, grading) : Promise.resolve(null)), [examId, grading]);
  const { classes, sections, subjects } = useLookups();

  useEffect(() => {
    if (!data) return;
    setMeta({
      title: str(data.title),
      description: str(data.description),
      classId: str(data.classId),
      sectionId: str(data.sectionId),
      subjectId: str(data.subjectId),
      startAt: toLocalInput(str(data.startAt)),
      durationMinutes: str(data.durationMinutes, "60"),
      passingMarks: data.passingMarks == null ? "" : str(data.passingMarks),
      mcqMarks: str(data.mcqMarks, "1"),
      shortMarks: str(data.shortMarks, "2"),
      longMarks: str(data.longMarks, "5"),
      shortRequired: data.shortRequired == null ? "" : str(data.shortRequired),
      longRequired: data.longRequired == null ? "" : str(data.longRequired),
    });
    setQuestions(((data.questions as QuestionDraft[]) || []).map((q) => ({ ...q })));
  }, [data]);

  const status = str(data?.status);
  const editable = status === "DRAFT" || status === "SCHEDULED";
  const questionsLocked = status !== "DRAFT";

  const addQuestion = (questionType: string) => {
    setQuestions((q) => [...q, { questionType, prompt: "", optionA: "", optionB: "", optionC: "", optionD: "", correctOption: "A" }]);
  };
  const patchQ = (i: number, patch: Partial<QuestionDraft>) => {
    setQuestions((qs) => qs.map((q, n) => (n === i ? { ...q, ...patch } : q)));
  };

  return (
    <>
      <div className="page-title">
        <div>
          <Link to="/app/online-exams" className="kicker">← Online exams</Link>
          <h1>{str(data?.title, "Exam")}</h1>
          <p><Badge value={status || "—"} /> · {num(data?.totalMarks)} marks</p>
        </div>
        <div className="row">
          {status === "DRAFT" ? (
            <Button kind="brass" loadingText="Scheduling…" onClick={async () => {
              await onlineExamApi.schedule(examId);
              toast("ok", "Scheduled");
              await exam.reload();
            }}>Schedule</Button>
          ) : null}
          {status === "CLOSED" ? (
            <Button kind="ok" loadingText="Publishing…" onClick={async () => {
              await onlineExamApi.publishResults(examId);
              toast("ok", "Results published");
              await exam.reload();
            }}>Publish results</Button>
          ) : null}
        </div>
      </div>
      <QueryState status={exam} label="exam">
        {data ? (
          <div className="grid two">
            <div className="card">
              <h3>Paper settings</h3>
              <Form busyLabel="Saving…" onSubmit={async () => {
                await onlineExamApi.update(examId, {
                  title: meta.title,
                  description: meta.description,
                  classId: meta.classId,
                  sectionId: meta.sectionId,
                  subjectId: meta.subjectId,
                  startAt: fromLocalInput(meta.startAt),
                  examDate: fromLocalInput(meta.startAt).slice(0, 10),
                  durationMinutes: Number(meta.durationMinutes),
                  passingMarks: meta.passingMarks ? Number(meta.passingMarks) : null,
                  mcqMarks: Number(meta.mcqMarks),
                  shortMarks: Number(meta.shortMarks),
                  longMarks: Number(meta.longMarks),
                  shortRequired: meta.shortRequired ? Number(meta.shortRequired) : null,
                  longRequired: meta.longRequired ? Number(meta.longRequired) : null,
                });
                toast("ok", "Exam updated");
                await exam.reload();
              }}>
                <fieldset disabled={!editable} style={{ border: 0, padding: 0 }}>
                  <Field label="Title"><input value={meta.title} onChange={(e) => setMeta({ ...meta, title: e.target.value })} required /></Field>
                  <Field label="Description"><textarea value={meta.description} onChange={(e) => setMeta({ ...meta, description: e.target.value })} /></Field>
                  <Field label="Class">
                    <select value={meta.classId} onChange={(e) => setMeta({ ...meta, classId: e.target.value, sectionId: "" })} required>
                      {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                    </select>
                  </Field>
                  <Field label="Section">
                    <select value={meta.sectionId} onChange={(e) => setMeta({ ...meta, sectionId: e.target.value })} required>
                      {(sections[meta.classId] || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </select>
                  </Field>
                  <Field label="Subject">
                    <select value={meta.subjectId} onChange={(e) => setMeta({ ...meta, subjectId: e.target.value })} required>
                      {subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </select>
                  </Field>
                  <Field label="Start"><input type="datetime-local" value={meta.startAt} onChange={(e) => setMeta({ ...meta, startAt: e.target.value })} required /></Field>
                  <Field label="Duration (minutes)"><input type="number" min={1} value={meta.durationMinutes} onChange={(e) => setMeta({ ...meta, durationMinutes: e.target.value })} required /></Field>
                  <Field label="Passing marks"><input type="number" value={meta.passingMarks} onChange={(e) => setMeta({ ...meta, passingMarks: e.target.value })} /></Field>
                  <div className="row">
                    <Field label="MCQ marks"><input type="number" value={meta.mcqMarks} onChange={(e) => setMeta({ ...meta, mcqMarks: e.target.value })} /></Field>
                    <Field label="Short marks"><input type="number" value={meta.shortMarks} onChange={(e) => setMeta({ ...meta, shortMarks: e.target.value })} /></Field>
                    <Field label="Long marks"><input type="number" value={meta.longMarks} onChange={(e) => setMeta({ ...meta, longMarks: e.target.value })} /></Field>
                  </div>
                  <div className="row">
                    <Field label="Attempt any N short"><input type="number" value={meta.shortRequired} onChange={(e) => setMeta({ ...meta, shortRequired: e.target.value })} /></Field>
                    <Field label="Attempt any N long"><input type="number" value={meta.longRequired} onChange={(e) => setMeta({ ...meta, longRequired: e.target.value })} /></Field>
                  </div>
                </fieldset>
                {editable ? <Button type="submit" kind="brass" loadingText="Saving…">Save settings</Button> : <p className="hint">Live or closed papers cannot be edited.</p>}
              </Form>
            </div>
            <div className="card">
              <h3>Questions</h3>
              {!questionsLocked ? (
                <div className="row" style={{ marginBottom: 12 }}>
                  <Button kind="ghost" onClick={() => addQuestion("MCQ")}>Add MCQ</Button>
                  <Button kind="ghost" onClick={() => addQuestion("SHORT")}>Add short</Button>
                  <Button kind="ghost" onClick={() => addQuestion("LONG")}>Add long</Button>
                </div>
              ) : <p className="hint">Questions lock after the exam is scheduled.</p>}
              {questions.map((q, i) => (
                <div key={q.id || i} className="exam-q">
                  <div className="row">
                    <strong>{i + 1}. {q.questionType}</strong>
                    {!questionsLocked ? <Button kind="ghost" onClick={() => setQuestions(questions.filter((_, n) => n !== i))}>Remove</Button> : null}
                  </div>
                  <Field label="Prompt"><textarea value={q.prompt} disabled={questionsLocked} onChange={(e) => patchQ(i, { prompt: e.target.value })} /></Field>
                  {q.questionType === "MCQ" ? (
                    <>
                      <Field label="A"><input value={q.optionA || ""} disabled={questionsLocked} onChange={(e) => patchQ(i, { optionA: e.target.value })} /></Field>
                      <Field label="B"><input value={q.optionB || ""} disabled={questionsLocked} onChange={(e) => patchQ(i, { optionB: e.target.value })} /></Field>
                      <Field label="C"><input value={q.optionC || ""} disabled={questionsLocked} onChange={(e) => patchQ(i, { optionC: e.target.value })} /></Field>
                      <Field label="D"><input value={q.optionD || ""} disabled={questionsLocked} onChange={(e) => patchQ(i, { optionD: e.target.value })} /></Field>
                      <Field label="Correct">
                        <select value={q.correctOption || "A"} disabled={questionsLocked} onChange={(e) => patchQ(i, { correctOption: e.target.value })}>
                          <option>A</option><option>B</option><option>C</option><option>D</option>
                        </select>
                      </Field>
                    </>
                  ) : (
                    <Field label="Reference answer (staff only)"><textarea value={q.referenceAnswer || ""} disabled={questionsLocked} onChange={(e) => patchQ(i, { referenceAnswer: e.target.value })} /></Field>
                  )}
                  <Field label="Marks (blank = default)"><input type="number" value={q.marks ?? ""} disabled={questionsLocked} onChange={(e) => patchQ(i, { marks: e.target.value })} /></Field>
                </div>
              ))}
              {!questionsLocked ? (
                <Button kind="brass" loadingText="Saving questions…" onClick={async () => {
                  await onlineExamApi.saveQuestions(examId, questions.map((q) => ({
                    questionType: q.questionType,
                    prompt: q.prompt,
                    marks: q.marks === "" || q.marks == null ? null : Number(q.marks),
                    optionA: q.optionA, optionB: q.optionB, optionC: q.optionC, optionD: q.optionD,
                    correctOption: q.questionType === "MCQ" ? q.correctOption : null,
                    referenceAnswer: q.referenceAnswer,
                  })));
                  toast("ok", "Questions saved");
                  await exam.reload();
                }}>Save questions</Button>
              ) : null}
            </div>
          </div>
        ) : null}
      </QueryState>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Attempts</h3>
        <QueryState status={attempts} label="attempts">
          <Table
            headers={["Student", "Roll", "Status", "Marks", "Grade", ""]}
            rows={(attempts.data || []).map((a) => {
              const id = str(a.id);
              return [
                str(a.studentName),
                str(a.rollNumber, "—"),
                <Badge key={id} value={str(a.status)} />,
                a.obtainedMarks == null ? "—" : `${str(a.obtainedMarks)} / ${str(a.totalMarks)}`,
                str(a.grade, "—"),
                <Button key={`${id}-g`} kind="ghost" onClick={() => setGrading(id)}>Grade</Button>,
              ];
            })}
          />
        </QueryState>
        {grading && detail.data ? (
          <Form busyLabel="Saving marks…" onSubmit={async () => {
            const qs = (detail.data?.questions as QuestionDraft[]) || [];
            await onlineExamApi.grade(examId, grading, qs.filter((q) => q.questionType !== "MCQ").map((q) => ({
              questionId: q.id,
              obtainedMarks: q.obtainedMarks === "" || q.obtainedMarks == null ? null : Number(q.obtainedMarks),
            })));
            toast("ok", "Graded");
            setGrading(null);
            await attempts.reload();
          }}>
            <h3>Grade attempt</h3>
            {((detail.data.questions as QuestionDraft[]) || []).map((q, i) => (
              <div key={q.id || i} className="exam-q">
                <p className="kicker">{q.questionType} · {q.marks} marks {q.autoGraded ? "· auto" : ""}</p>
                <strong>{q.prompt}</strong>
                <p>{q.questionType === "MCQ" ? `Choice ${q.optionChoice || "—"} (key ${q.correctOption || "—"})` : (q.textAnswer || "—")}</p>
                {q.questionType !== "MCQ" ? (
                  <Field label="Obtained">
                    <input
                      type="number"
                      defaultValue={q.obtainedMarks == null ? "" : str(q.obtainedMarks)}
                      onChange={(e) => { q.obtainedMarks = e.target.value; }}
                    />
                  </Field>
                ) : <p className="hint">MCQ is auto-graded.</p>}
              </div>
            ))}
            <div className="row">
              <Button type="submit" kind="brass" loadingText="Saving marks…">Save grades</Button>
              <Button kind="ghost" onClick={() => setGrading(null)}>Close</Button>
            </div>
          </Form>
        ) : null}
      </div>
    </>
  );
}
