import { useMemo, useState } from "react";
import { remarkApi, studentApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { useSession } from "../../lib/session";
import { REMARK_CATEGORIES } from "../../lib/status";
import { useToast } from "../../lib/toast";
import { Badge, Button, Empty, Field, Form, QueryState, Table, useAsync } from "../../ui/kit";
import ChildSwitch, { useActiveStudentId } from "./ChildSwitch";

function str(v: unknown, d = "") {
  return v == null ? d : String(v);
}

export default function RemarksPage() {
  const { user } = useSession();
  const staff = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL" || user?.role === "TEACHER";
  if (staff) return <StaffRemarks />;
  return <FamilyRemarks />;
}

function FamilyRemarks() {
  const { user } = useSession();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const sid = useActiveStudentId(user?.role === "STUDENT" ? "me" : null);
  const studentId = user?.role === "STUDENT" ? undefined : sid || undefined;
  const list = useAsync(
    () => remarkApi.list(user?.role === "STUDENT" ? {} : { studentId }),
    [user?.role, studentId],
    user?.role === "STUDENT" || Boolean(studentId),
  );
  const rows = list.data?.content || [];
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Remarks</h1>
          <p>Comments from teachers and school staff.</p>
        </div>
      </div>
      {user?.role === "PARENT" ? <ChildSwitch childrenList={kids.data || []} /> : null}
      <QueryState status={list} label="remarks">
        {!rows.length ? <Empty title="No remarks" /> : (
          <Table
            headers={["Date", "Category", "Note"]}
            rows={rows.map((r) => [
              str(r.remarkDate),
              <Badge key={str(r.id)} value={str(r.category)} />,
              str(r.body),
            ])}
          />
        )}
      </QueryState>
    </>
  );
}

function StaffRemarks() {
  const { classes, sections, className } = useLookups();
  const toast = useToast();
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [category, setCategory] = useState("GENERAL");
  const [body, setBody] = useState("");
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [picked, setPicked] = useState<Record<string, boolean>>({});
  const sectionOpts = sections[classId] || [];
  const roster = useAsync(
    () => (classId && sectionId ? studentApi.roster(classId, sectionId, "ACTIVE") : Promise.resolve([])),
    [classId, sectionId],
    Boolean(classId && sectionId),
  );
  const history = useAsync(
    () => (classId && sectionId ? remarkApi.list({ classId, sectionId }) : Promise.resolve({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 40, first: true, last: true })),
    [classId, sectionId],
    Boolean(classId && sectionId),
  );
  const students = roster.data || [];
  const selectedIds = useMemo(() => Object.entries(picked).filter(([, v]) => v).map(([id]) => id), [picked]);

  return (
    <>
      <div className="page-title">
        <div>
          <h1>Remarks</h1>
          <p>Write a note for one student or the whole class/section. Parents are notified.</p>
        </div>
      </div>
      <div className="card" style={{ marginBottom: 16 }}>
        <Form
          busyLabel="Saving remarks…"
          onSubmit={async () => {
            const studentIds = selectedIds.length ? selectedIds : undefined;
            if (!studentIds && !(classId && sectionId)) throw new Error("Select a class and section, or specific students.");
            await remarkApi.create({
              studentIds,
              classId: studentIds ? undefined : classId,
              sectionId: studentIds ? undefined : sectionId,
              category,
              body,
              remarkDate: date,
            });
            toast("ok", selectedIds.length ? `Saved ${selectedIds.length} remark(s)` : "Class remarks saved");
            setBody("");
            setPicked({});
            await history.reload();
          }}
        >
          <div className="row">
            <Field label="Class">
              <select value={classId} onChange={(e) => { setClassId(e.target.value); setSectionId(""); setPicked({}); }} required>
                <option value="">Select</option>
                {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="Section">
              <select value={sectionId} onChange={(e) => { setSectionId(e.target.value); setPicked({}); }} required>
                <option value="">Select</option>
                {sectionOpts.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <Field label="Category">
              <select value={category} onChange={(e) => setCategory(e.target.value)}>
                {REMARK_CATEGORIES.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
              </select>
            </Field>
            <Field label="Date">
              <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
            </Field>
          </div>
          <Field label="Remark">
            <textarea value={body} onChange={(e) => setBody(e.target.value)} required rows={3} />
          </Field>
          <QueryState status={roster} label="students">
            {!students.length ? <p className="hint">Pick a class and section to load students.</p> : (
              <>
                <div className="row" style={{ marginBottom: 8 }}>
                  <Button kind="ghost" type="button" onClick={() => {
                    const next: Record<string, boolean> = {};
                    students.forEach((s) => { next[s.id] = true; });
                    setPicked(next);
                  }}>Select class</Button>
                  <Button kind="ghost" type="button" onClick={() => setPicked({})}>Clear</Button>
                  <span className="hint">{selectedIds.length ? `${selectedIds.length} selected` : "None selected — saves for the whole section"}</span>
                </div>
                <div className="row" style={{ flexWrap: "wrap", gap: 8 }}>
                  {students.map((s) => (
                    <label key={s.id} className={`chip ${picked[s.id] ? "on" : ""}`}>
                      <input
                        type="checkbox"
                        checked={Boolean(picked[s.id])}
                        onChange={(e) => setPicked((cur) => ({ ...cur, [s.id]: e.target.checked }))}
                      />
                      {s.fullName || s.rollNumber || s.id.slice(0, 8)}
                    </label>
                  ))}
                </div>
              </>
            )}
          </QueryState>
          <Button type="submit" kind="brass" loadingText="Saving remarks…">Save remark</Button>
        </Form>
      </div>
      <div className="card">
        <h3>History {classId ? `· ${className(classId)}` : ""}</h3>
        <QueryState status={history} label="remark history">
          {!(history.data?.content || []).length ? <Empty title="No remarks yet" /> : (
            <Table
              headers={["Date", "Student", "Category", "Note", ""]}
              rows={(history.data?.content || []).map((r) => {
                const id = str(r.id);
                return [
                  str(r.remarkDate),
                  str(r.studentName, "—"),
                  <Badge key={id} value={str(r.category)} />,
                  str(r.body),
                  r.mine ? (
                    <Button
                      key={`${id}-d`}
                      kind="ghost"
                      onClick={async () => {
                        if (!window.confirm("Delete this remark?")) return;
                        await remarkApi.remove(id);
                        toast("ok", "Deleted");
                        await history.reload();
                      }}
                    >Delete</Button>
                  ) : "",
                ];
              })}
            />
          )}
        </QueryState>
      </div>
    </>
  );
}
