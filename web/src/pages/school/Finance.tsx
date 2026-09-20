import { useState } from "react";
import { feeApi, fileApi, salaryApi, settingsApi, studentApi, subscriptionApi, userApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { fmtDate, money, monthStart, pretty, today } from "../../lib/format";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import { Badge, Button, Empty, ErrorBox, Field, Form, Loading, Modal, Table, useAsync } from "../../ui/kit";
import ChildSwitch, { useActiveStudentId } from "./ChildSwitch";
import type { ChallanStatus, SchoolSettings } from "../../lib/types";

export function FeesPage() {
  const { user } = useSession();
  const { classes } = useLookups();
  const toast = useToast();
  const structures = useAsync(() => (canFinance(user?.role) ? feeApi.structures() : Promise.resolve([])), [user?.role]);
  const pending = useAsync(() => (canFinance(user?.role) ? feeApi.pendingProofs() : Promise.resolve([])), [user?.role]);
  const me = useAsync(() => (user?.role === "STUDENT" ? studentApi.me() : Promise.resolve(null)), [user?.role]);
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const sid = useActiveStudentId(me.data?.id);
  const mine = useAsync(() => (sid ? feeApi.studentChallans(sid) : Promise.resolve([])), [sid]);
  const [classId, setClassId] = useState("");
  const [month, setMonth] = useState(monthStart());
  const [stForm, setStForm] = useState({ name: "", classId: "", academicYear: "2026-2027", tuitionAmount: "8000" });
  const [proofFor, setProofFor] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [ref, setRef] = useState("");

  if (user?.role === "PARENT" || user?.role === "STUDENT") {
    return (
      <>
        <div className="page-title"><div><h1>Fee challans</h1><p>Pay by uploading a bank slip — no online gateway</p></div></div>
        {user.role === "PARENT" ? <ChildSwitch childrenList={kids.data || []} /> : null}
        {mine.loading ? <Loading /> : mine.error ? <ErrorBox error={mine.error} /> : (
          <Table
            headers={["Challan", "Month", "Due", "Amount", "Status", ""]}
            rows={(mine.data || []).map((c) => [
              c.challanNumber, fmtDate(c.month), fmtDate(c.dueDate), money(c.totalPayable), <Badge key={c.id} value={c.status} />,
              c.status === "PAID" ? "—" : <Button key={`p${c.id}`} kind="ghost" onClick={() => setProofFor(c.id)}>Upload proof</Button>,
            ])}
          />
        )}
        <Modal title="Payment proof" open={!!proofFor} onClose={() => setProofFor(null)}>
          <Form onSubmit={async () => {
            if (!file || !proofFor) return;
            try {
              const up = await fileApi.upload(file);
              await feeApi.submitProof(proofFor, { slipUrl: up.url, transactionRef: ref });
              toast("ok", "Proof submitted"); setProofFor(null); void mine.reload();
            } catch (e) { toast("err", e instanceof Error ? e.message : "Failed"); }
          }}>
            <Field label="Reference"><input value={ref} onChange={(e) => setRef(e.target.value)} /></Field>
            <Field label="Slip"><input type="file" accept="image/*,.pdf" onChange={(e) => setFile(e.target.files?.[0] || null)} required /></Field>
            <Button type="submit" kind="brass">Submit</Button>
          </Form>
        </Modal>
      </>
    );
  }

  return (
    <>
      <div className="page-title"><div><h1>Fees</h1><p>Structures, monthly challans and proof verification</p></div>
        <div className="row">
          <Button kind="ghost" onClick={async () => { const n = await feeApi.markOverdue(); toast("ok", `${n.length} marked overdue`); }}>Mark overdue</Button>
        </div>
      </div>
      <div className="grid two">
        <div className="card">
          <h3>Fee structures</h3>
          {structures.loading ? <Loading /> : <Table headers={["Name", "Amount"]} rows={(structures.data || []).map((s) => [s.name, money(s.tuitionAmount)])} />}
          <Form onSubmit={async () => { await feeApi.saveStructure({ ...stForm, tuitionAmount: Number(stForm.tuitionAmount) }); toast("ok", "Structure saved"); void structures.reload(); }}>
            <Field label="Name"><input value={stForm.name} onChange={(e) => setStForm({ ...stForm, name: e.target.value })} required /></Field>
            <Field label="Class">
              <select value={stForm.classId} onChange={(e) => setStForm({ ...stForm, classId: e.target.value })} required>
                <option value="">Select</option>
                {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="Tuition"><input value={stForm.tuitionAmount} onChange={(e) => setStForm({ ...stForm, tuitionAmount: e.target.value })} /></Field>
            <Button type="submit">Save structure</Button>
          </Form>
        </div>
        <div className="card">
          <h3>Generate monthly challans</h3>
          <Form onSubmit={async () => {
            const created = await feeApi.generate({ classId, month });
            toast("ok", `${created.length} challans generated`);
          }}>
            <Field label="Class">
              <select value={classId} onChange={(e) => setClassId(e.target.value)} required>
                <option value="">Select</option>
                {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="Month"><input type="month" value={month.slice(0, 7)} onChange={(e) => setMonth(`${e.target.value}-01`)} /></Field>
            <Button type="submit" kind="brass">Generate</Button>
          </Form>
        </div>
      </div>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Pending proofs</h3>
        {pending.loading ? <Loading /> : !(pending.data || []).length ? <Empty title="Nothing to review" /> : (
          <Table
            headers={["Proof", "Ref", "Status", ""]}
            rows={(pending.data || []).map((p) => [
              p.id.slice(0, 8), p.transactionRef || "—", pretty(p.status),
              <div key={p.id} className="row">
                <Button kind="ok" onClick={async () => { await feeApi.reviewProof(p.id, true); toast("ok", "Approved"); void pending.reload(); }}>Approve</Button>
                <Button kind="danger" onClick={async () => { await feeApi.reviewProof(p.id, false, "Rejected"); toast("info", "Rejected"); void pending.reload(); }}>Reject</Button>
              </div>,
            ])}
          />
        )}
      </div>
    </>
  );
}

function canFinance(role?: string) {
  return role === "SCHOOL_ADMIN" || role === "ACCOUNT_OFFICER" || role === "PRINCIPAL" || role === "ERP_OWNER";
}

export function SalariesPage() {
  const { user } = useSession();
  const toast = useToast();
  const mine = useAsync(() => salaryApi.mine(), []);
  const month = monthStart();
  const monthRows = useAsync(() => (canFinance(user?.role) ? salaryApi.month(month) : Promise.resolve([])), [user?.role, month]);
  const users = useAsync(() => (canFinance(user?.role) ? userApi.list({ role: "TEACHER" }) : Promise.resolve({ content: [] as { id: string; fullName?: string }[], page: 0, size: 0, totalElements: 0, totalPages: 0, first: true, last: true })), [user?.role]);
  const [uid, setUid] = useState("");
  const [base, setBase] = useState("50000");
  if (user?.role === "TEACHER") {
    return (
      <>
        <div className="page-title"><div><h1>My salary</h1></div></div>
        {mine.loading ? <Loading /> : <Table headers={["Month", "Net", "Status"]} rows={(mine.data || []).map((s) => [fmtDate(s.month), money(s.netPay), <Badge key={s.id} value={s.status} />])} />}
      </>
    );
  }
  return (
    <>
      <div className="page-title"><div><h1>Payroll</h1><p>Staff profiles and monthly generation</p></div>
        <Button kind="brass" onClick={async () => { const rows = await salaryApi.generate(month); toast("ok", `${rows.length} salary rows`); void monthRows.reload(); }}>Generate this month</Button>
      </div>
      <div className="card" style={{ marginBottom: 16 }}>
        <h3>Salary profile</h3>
        <Form onSubmit={async () => { await salaryApi.upsertProfile(uid, Number(base)); toast("ok", "Profile saved"); }}>
          <Field label="Teacher">
            <select value={uid} onChange={(e) => setUid(e.target.value)} required>
              <option value="">Select</option>
              {(users.data?.content || []).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
            </select>
          </Field>
          <Field label="Base salary"><input value={base} onChange={(e) => setBase(e.target.value)} /></Field>
          <Button type="submit">Save profile</Button>
        </Form>
      </div>
      {monthRows.loading ? <Loading /> : (
        <Table
          headers={["Staff", "Net", "Status", ""]}
          rows={(monthRows.data || []).map((s) => [
            s.staffUserId.slice(0, 8), money(s.netPay), <Badge key={s.id} value={s.status} />,
            s.status === "PAID" ? "Paid" : <Button key={`pay${s.id}`} onClick={async () => { await salaryApi.pay(s.id, today()); toast("ok", "Marked paid"); void monthRows.reload(); }}>Mark paid</Button>,
          ])}
        />
      )}
    </>
  );
}

export function SettingsPage() {
  const toast = useToast();
  const s = useAsync(() => settingsApi.get());
  const [form, setForm] = useState<SchoolSettings>({});
  const loaded = s.data;
  const current = { ...loaded, ...form };
  if (s.loading) return <Loading />;
  return (
    <>
      <div className="page-title"><div><h1>Payment settings</h1><p>Shown to parents and locked schools when paying</p></div></div>
      <div className="card" style={{ maxWidth: 640 }}>
        <Form onSubmit={async () => { await settingsApi.save(current); toast("ok", "Settings saved"); }}>
          <Field label="Bank name"><input value={current.bankName || ""} onChange={(e) => setForm({ ...form, bankName: e.target.value })} /></Field>
          <Field label="Account title"><input value={current.accountTitle || ""} onChange={(e) => setForm({ ...form, accountTitle: e.target.value })} /></Field>
          <Field label="Account number"><input value={current.accountNumber || ""} onChange={(e) => setForm({ ...form, accountNumber: e.target.value })} /></Field>
          <Field label="JazzCash"><input value={current.jazzcash || ""} onChange={(e) => setForm({ ...form, jazzcash: e.target.value })} /></Field>
          <Field label="Easypaisa"><input value={current.easypaisa || ""} onChange={(e) => setForm({ ...form, easypaisa: e.target.value })} /></Field>
          <Field label="Instructions"><textarea value={current.paymentInstructions || ""} onChange={(e) => setForm({ ...form, paymentInstructions: e.target.value })} /></Field>
          <Button type="submit" kind="brass">Save</Button>
        </Form>
      </div>
    </>
  );
}

export function BillingPage() {
  const toast = useToast();
  const sub = useAsync(async () => {
    try { return await subscriptionApi.current(); } catch { return null; }
  });
  const [file, setFile] = useState<File | null>(null);
  const [ref, setRef] = useState("");
  if (sub.loading) return <Loading />;
  const s = sub.data;
  return (
    <>
      <div className="page-title"><div><h1>School subscription</h1><p>Manual payment proof — ERP owner verifies</p></div></div>
      {!s ? <Empty title="No subscription record" /> : (
        <div className="card">
          <p><Badge value={s.status} /> · {money(s.amount, s.currency)}</p>
          <p>Period {fmtDate(s.periodStart)} → {fmtDate(s.periodEnd)}</p>
          {(s.status === "PENDING" || s.status === "REJECTED") ? (
            <Form onSubmit={async () => {
              if (!file) return;
              const up = await fileApi.upload(file);
              await subscriptionApi.submitPayment(s.id, { slipUrl: up.url, transactionRef: ref });
              toast("ok", "Proof sent to owner"); void sub.reload();
            }}>
              <Field label="Reference"><input value={ref} onChange={(e) => setRef(e.target.value)} /></Field>
              <Field label="Slip"><input type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} required /></Field>
              <Button type="submit" kind="brass">Submit proof</Button>
            </Form>
          ) : <p>Latest status is managed by the ERP owner.</p>}
        </div>
      )}
    </>
  );
}
