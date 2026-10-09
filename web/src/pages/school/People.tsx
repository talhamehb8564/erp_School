import { useState } from "react";
import { studentApi, userApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { pretty } from "../../lib/format";
import { useToast } from "../../lib/toast";
import { Badge, Button, Field, Form, Modal, Pager, QueryState, Search, Table, studentLabel, useAsync, useDebounced } from "../../ui/kit";
import { useSession } from "../../lib/session";
import type { Role } from "../../lib/types";

export function StudentsPage() {
  const { user } = useSession();
  const canEnrol = user?.role === "SCHOOL_ADMIN";
  const { classes, sections, className } = useLookups();
  const [classId, setClassId] = useState("");
  const [session, setSession] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const sessions = Array.from(new Set(classes.map((c) => c.academicSession).filter(Boolean))) as string[];
  const visibleClasses = session ? classes.filter((c) => c.academicSession === session) : classes;
  const list = useAsync(() => studentApi.list(classId || undefined, undefined, page, session || undefined), [classId, page, session]);
  const [open, setOpen] = useState(false);
  const toast = useToast();
  const [form, setForm] = useState({
    firstName: "", lastName: "", cnic: "", classId: "", sectionId: "", parentFirstName: "", parentLastName: "", relationship: "FATHER",
  });
  const [promo, setPromo] = useState({ fromClassId: "", fromSectionId: "", toClassId: "", toSectionId: "" });
  const [promoOpen, setPromoOpen] = useState(false);
  const rows = (list.data?.content || []).filter((s) => {
    const n = `${s.user?.fullName || ""} ${s.admissionNumber || ""}`.toLowerCase();
    return !q || n.includes(q.toLowerCase());
  });
  return (
    <>
      <div className="page-title">
        <div><h1>Students</h1><p>Enrolment writes a user + student row on Neon</p></div>
        {canEnrol ? (
          <div className="row">
            <Button kind="ghost" onClick={() => setPromoOpen(true)}>Promote class</Button>
            <Button kind="brass" onClick={() => setOpen(true)}>Enrol student</Button>
          </div>
        ) : null}
      </div>
      <div className="row" style={{ marginBottom: 12 }}>
        <Search value={q} onChange={setQ} placeholder="Search name or admission no." />
        <select className="search" value={session} onChange={(e) => { setSession(e.target.value); setClassId(""); setPage(0); }}>
          <option value="">All sessions</option>
          {sessions.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <select className="search" value={classId} onChange={(e) => { setClassId(e.target.value); setPage(0); }}>
          <option value="">All classes</option>
          {visibleClasses.map((c) => <option key={c.id} value={c.id}>{c.name}{c.academicSession ? ` (${c.academicSession})` : ""}</option>)}
        </select>
      </div>
      <QueryState status={list} label="students">
        <Table
          headers={canEnrol ? ["Admission", "Name", "CNIC", "Class", "Roll", "Status", ""] : ["Admission", "Name", "CNIC", "Class", "Roll", "Status"]}
          rows={rows.map((s) => {
            const cells = [s.admissionNumber, studentLabel(s), s.cnic || "—", className(s.classId), s.rollNumber || "—", <Badge key={s.id} value={s.status} />];
            if (canEnrol) {
              cells.push(
                <Button key={`in${s.id}`} kind="danger" loadingText="Inactivating…" onClick={async () => {
                  await studentApi.inactivate(s.id, "UNPAID_FEES");
                  toast("ok", "Student inactivated");
                  void list.reload();
                }}>Inactivate</Button>,
              );
            }
            return cells;
          })}
        />
        <Pager page={list.data?.page ?? page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
      </QueryState>
      <Modal title="Enrol student" open={open} onClose={() => setOpen(false)}>
        <Form
          busyLabel="Creating…"
          onSubmit={async () => {
            const res = await studentApi.enroll(form);
            toast("ok", res.message || "Enrolled");
            if (res.studentAccount?.temporaryPassword) toast("info", `Student password: ${res.studentAccount.temporaryPassword}`);
            if (res.parentAccount?.temporaryPassword) toast("info", `Parent password: ${res.parentAccount.temporaryPassword}`);
            setOpen(false);
            void list.reload();
          }}
        >
          <Field label="First name"><input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} required /></Field>
          <Field label="Last name"><input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} required /></Field>
          <Field label="CNIC / B-Form"><input value={form.cnic} onChange={(e) => setForm({ ...form, cnic: e.target.value })} required placeholder="13 digits" /></Field>
          <Field label="Class">
            <select value={form.classId} onChange={(e) => setForm({ ...form, classId: e.target.value, sectionId: "" })} required>
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
          <Field label="Parent first name"><input value={form.parentFirstName} onChange={(e) => setForm({ ...form, parentFirstName: e.target.value })} /></Field>
          <Field label="Parent last name"><input value={form.parentLastName} onChange={(e) => setForm({ ...form, parentLastName: e.target.value })} /></Field>
          <Button type="submit" kind="brass" loadingText="Creating…">Save to database</Button>
        </Form>
      </Modal>
      <Modal title="Promote class / section" open={promoOpen} onClose={() => setPromoOpen(false)}>
        <Form busyLabel="Promoting…" onSubmit={async () => {
          await studentApi.promote(promo);
          toast("ok", "Students promoted");
          setPromoOpen(false);
          void list.reload();
        }}>
          <Field label="From class">
            <select value={promo.fromClassId} onChange={(e) => setPromo({ ...promo, fromClassId: e.target.value, fromSectionId: "" })} required>
              <option value="">Select</option>
              {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="From section">
            <select value={promo.fromSectionId} onChange={(e) => setPromo({ ...promo, fromSectionId: e.target.value })}>
              <option value="">All sections</option>
              {(sections[promo.fromClassId] || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="To class">
            <select value={promo.toClassId} onChange={(e) => setPromo({ ...promo, toClassId: e.target.value, toSectionId: "" })} required>
              <option value="">Select</option>
              {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="To section">
            <select value={promo.toSectionId} onChange={(e) => setPromo({ ...promo, toSectionId: e.target.value })} required>
              <option value="">Select</option>
              {(sections[promo.toClassId] || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Button type="submit" kind="brass" loadingText="Promoting…">Promote</Button>
        </Form>
      </Modal>
    </>
  );
}

export function UsersPage() {
  const { user } = useSession();
  const canManage = user?.role === "SCHOOL_ADMIN";
  const [q, setQ] = useState("");
  const [role, setRole] = useState<Role | "">("");
  const [page, setPage] = useState(0);
  const dq = useDebounced(q);
  const list = useAsync(() => userApi.list({ q: dq || undefined, role: role || undefined, page }), [dq, role, page]);
  const [open, setOpen] = useState(false);
  const toast = useToast();
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "", role: "TEACHER" as Role });
  const [temp, setTemp] = useState<string | null>(null);
  return (
    <>
      <div className="page-title">
        <div><h1>Users</h1><p>Usernames and temporary passwords are generated by the backend</p></div>
        {canManage ? <Button kind="brass" onClick={() => setOpen(true)}>Create user</Button> : null}
      </div>
      {temp ? <div className="notice">Share once: <span className="pwd">{temp}</span></div> : null}
      <div className="row" style={{ marginBottom: 12 }}>
        <Search value={q} onChange={(v) => { setQ(v); setPage(0); }} placeholder="Search users" />
        <select className="search" value={role} onChange={(e) => { setRole(e.target.value as Role | ""); setPage(0); }}>
          <option value="">All roles</option>
          {["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER", "PARENT", "STUDENT"].map((r) => <option key={r}>{r}</option>)}
        </select>
      </div>
      <QueryState status={list} label="users">
        <Table
          headers={["Name", "Username", "Role", "Status", ""]}
          rows={(list.data?.content || []).map((u) => [
            u.fullName, u.username, pretty(u.role), <Badge key={u.id} value={u.status} />,
            canManage ? <Button key={`r${u.id}`} kind="ghost" loadingText="Resetting…" onClick={async () => {
              const r = await userApi.resetPassword(u.id);
              setTemp(`${r.user.username} / ${r.temporaryPassword}`);
              toast("ok", "Password reset");
            }}>Reset password</Button> : "—",
          ])}
        />
        <Pager page={list.data?.page ?? page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
      </QueryState>
      <Modal title="Create user" open={open} onClose={() => setOpen(false)}>
        <Form busyLabel="Creating…" onSubmit={async () => {
          const r = await userApi.create(form);
          setTemp(`${r.user.username} / ${r.temporaryPassword}`);
          toast("ok", "User created");
          setOpen(false);
          void list.reload();
        }}>
          <Field label="First name"><input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} required /></Field>
          <Field label="Last name"><input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} required /></Field>
          <Field label="Email"><input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
          <Field label="Role">
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
              {["TEACHER", "PRINCIPAL", "ACCOUNT_OFFICER", "SCHOOL_ADMIN", "PARENT"].map((r) => <option key={r}>{r}</option>)}
            </select>
          </Field>
          <Button type="submit" kind="brass" loadingText="Creating…">Create</Button>
        </Form>
      </Modal>
    </>
  );
}
