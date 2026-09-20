import { useState } from "react";
import { feeApi, fileApi, salaryApi, settingsApi, studentApi, subscriptionApi, userApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { fmtDate, money, monthStart, pretty, today } from "../../lib/format";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import { Badge, Button, Empty, Field, FileLink, Form, Modal, QueryState, Table, useAsync } from "../../ui/kit";
import ChildSwitch, { useActiveStudentId } from "./ChildSwitch";
import type { SchoolSettings } from "../../lib/types";

export function FeesPage() {
  const { user } = useSession();
  const { classes } = useLookups();
  const toast = useToast();
  const ops = canOperateFees(user?.role);
  const viewer = canViewFees(user?.role);
  const structures = useAsync(() => (viewer ? feeApi.structures() : Promise.resolve([])), [user?.role]);
  const pending = useAsync(() => (ops ? feeApi.pendingProofs() : Promise.resolve([])), [user?.role]);
  const unpaid = useAsync(() => (user?.role === "PRINCIPAL" ? feeApi.byStatus("UNPAID") : Promise.resolve([])), [user?.role]);
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
        <QueryState status={mine} label="fee challans">
          <Table
            headers={["Challan", "Month", "Due", "Amount", "Status", ""]}
            rows={(mine.data || []).map((c) => [
              c.challanNumber, fmtDate(c.month), fmtDate(c.dueDate), money(c.totalPayable), <Badge key={c.id} value={c.status} />,
              c.status === "PAID" ? "—" : <Button key={`p${c.id}`} kind="ghost" onClick={() => setProofFor(c.id)}>Upload proof</Button>,
            ])}
          />
        </QueryState>
        <Modal title="Payment proof" open={!!proofFor} onClose={() => setProofFor(null)}>
          <Form busyLabel="Uploading…" onSubmit={async () => {
            if (!file || !proofFor) throw new Error("Choose a slip file first.");
            const up = await fileApi.upload(file);
            await feeApi.submitProof(proofFor, { slipUrl: up.url, transactionRef: ref });
            toast("ok", "Proof submitted"); setProofFor(null); void mine.reload();
          }}>
            <Field label="Reference"><input value={ref} onChange={(e) => setRef(e.target.value)} /></Field>
            <Field label="Slip"><input type="file" accept="image/*,.pdf,.doc,.docx" onChange={(e) => setFile(e.target.files?.[0] || null)} required /></Field>
            <Button type="submit" kind="brass" loadingText="Uploading…">Submit</Button>
          </Form>
        </Modal>
      </>
    );
  }

  return (
    <>
      <div className="page-title"><div><h1>Fees</h1><p>Structures, monthly challans and proof verification</p></div>
        {ops ? (
          <div className="row">
            <Button kind="ghost" loadingText="Updating…" onClick={async () => { const n = await feeApi.markOverdue(); toast("ok", `${n.length} marked overdue`); }}>Mark overdue</Button>
          </div>
        ) : null}
      </div>
      <div className="grid two">
        <div className="card">
          <h3>Fee structures</h3>
          <QueryState status={structures} label="fee structures">
            <Table headers={["Name", "Amount"]} rows={(structures.data || []).map((s) => [s.name, money(s.tuitionAmount)])} />
          </QueryState>
          {ops ? (
            <Form busyLabel="Saving…" onSubmit={async () => { await feeApi.saveStructure({ ...stForm, tuitionAmount: Number(stForm.tuitionAmount) }); toast("ok", "Structure saved"); void structures.reload(); }}>
              <Field label="Name"><input value={stForm.name} onChange={(e) => setStForm({ ...stForm, name: e.target.value })} required /></Field>
              <Field label="Class">
                <select value={stForm.classId} onChange={(e) => setStForm({ ...stForm, classId: e.target.value })} required>
                  <option value="">Select</option>
                  {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </Field>
              <Field label="Tuition"><input value={stForm.tuitionAmount} onChange={(e) => setStForm({ ...stForm, tuitionAmount: e.target.value })} /></Field>
              <Button type="submit" loadingText="Saving…">Save structure</Button>
            </Form>
          ) : null}
        </div>
        {ops ? (
          <div className="card">
            <h3>Generate monthly challans</h3>
            <Form busyLabel="Generating…" onSubmit={async () => {
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
              <Button type="submit" kind="brass" loadingText="Generating…">Generate</Button>
            </Form>
          </div>
        ) : (
          <div className="card">
            <h3>Unpaid challans</h3>
            <QueryState status={unpaid} label="unpaid challans">
              <Table headers={["Challan", "Amount", "Status"]} rows={(unpaid.data || []).map((c) => [c.challanNumber, money(c.totalPayable), pretty(c.status)])} />
            </QueryState>
          </div>
        )}
      </div>
      {ops ? (
        <div className="card" style={{ marginTop: 16 }}>
          <h3>Pending proofs</h3>
          <QueryState status={pending} label="pending proofs">
            {!(pending.data || []).length ? <Empty title="Nothing to review" /> : (
              <Table
                headers={["Proof", "Ref", "Slip", "Status", ""]}
                rows={(pending.data || []).map((p) => [
                  p.id.slice(0, 8), p.transactionRef || "—", <FileLink key={`s${p.id}`} href={p.slipUrl} />, pretty(p.status),
                  <div key={p.id} className="row">
                    <Button kind="ok" loadingText="Approving…" onClick={async () => { await feeApi.reviewProof(p.id, true); toast("ok", "Approved"); void pending.reload(); }}>Approve</Button>
                    <Button kind="danger" loadingText="Rejecting…" onClick={async () => { await feeApi.reviewProof(p.id, false, "Rejected"); toast("info", "Rejected"); void pending.reload(); }}>Reject</Button>
                  </div>,
                ])}
              />
            )}
          </QueryState>
        </div>
      ) : null}
    </>
  );
}

function canOperateFees(role?: string) {
  return role === "SCHOOL_ADMIN" || role === "ACCOUNT_OFFICER" || role === "ERP_OWNER";
}

function canViewFees(role?: string) {
  return canOperateFees(role) || role === "PRINCIPAL";
}

export function SalariesPage() {
  const { user } = useSession();
  const toast = useToast();
  const ops = canOperateFees(user?.role);
  const viewer = canViewFees(user?.role);
  const mine = useAsync(() => salaryApi.mine(), []);
  const month = monthStart();
  const monthRows = useAsync(() => (viewer ? salaryApi.month(month) : Promise.resolve([])), [user?.role, month]);
  const users = useAsync(() => (viewer ? userApi.list() : Promise.resolve({ content: [] as { id: string; fullName?: string; username?: string; role?: string }[], page: 0, size: 0, totalElements: 0, totalPages: 0, first: true, last: true })), [user?.role]);
  const [uid, setUid] = useState("");
  const [base, setBase] = useState("50000");
  if (user?.role === "TEACHER") {
    return (
      <>
        <div className="page-title"><div><h1>My salary</h1></div></div>
        <QueryState status={mine} label="salary">
          <Table headers={["Month", "Net", "Status"]} rows={(mine.data || []).map((s) => [fmtDate(s.month), money(s.netPay), <Badge key={s.id} value={s.status} />])} />
        </QueryState>
      </>
    );
  }
  return (
    <>
      <div className="page-title"><div><h1>Payroll</h1><p>Staff profiles and monthly generation</p></div>
        {ops ? <Button kind="brass" loadingText="Generating…" onClick={async () => { const rows = await salaryApi.generate(month); toast("ok", `${rows.length} salary rows`); void monthRows.reload(); }}>Generate this month</Button> : null}
      </div>
      {ops ? (
        <div className="card" style={{ marginBottom: 16 }}>
          <h3>Salary profile</h3>
          <Form busyLabel="Saving…" onSubmit={async () => { await salaryApi.upsertProfile(uid, Number(base)); toast("ok", "Profile saved"); }}>
            <Field label="Teacher">
              <select value={uid} onChange={(e) => setUid(e.target.value)} required>
                <option value="">Select</option>
                {(users.data?.content || []).filter((u) => u.role === "TEACHER").map((u) => <option key={u.id} value={u.id}>{u.fullName || u.username}</option>)}
              </select>
            </Field>
            <Field label="Base salary"><input value={base} onChange={(e) => setBase(e.target.value)} /></Field>
            <Button type="submit" loadingText="Saving…">Save profile</Button>
          </Form>
        </div>
      ) : null}
      <QueryState status={monthRows} label="payroll">
        <Table
          headers={["Staff", "Net", "Status", ""]}
          rows={(monthRows.data || []).map((s) => [
            (users.data?.content || []).find((u) => u.id === s.staffUserId)?.fullName || s.staffUserId.slice(0, 8),
            money(s.netPay),
            <Badge key={s.id} value={s.status} />,
            !ops || s.status === "PAID" ? pretty(s.status) : <Button key={`pay${s.id}`} loadingText="Updating…" onClick={async () => { await salaryApi.pay(s.id, today()); toast("ok", "Marked paid"); void monthRows.reload(); }}>Mark paid</Button>,
          ])}
        />
      </QueryState>
    </>
  );
}

export function SettingsPage() {
  const toast = useToast();
  const s = useAsync(() => settingsApi.get());
  const [form, setForm] = useState<SchoolSettings>({});
  const loaded = s.data;
  const current = { ...loaded, ...form };
  return (
    <>
      <div className="page-title"><div><h1>Payment settings</h1><p>Shown to parents and locked schools when paying</p></div></div>
      <QueryState status={s} label="payment settings">
        <div className="card" style={{ maxWidth: 640 }}>
          <Form busyLabel="Saving…" onSubmit={async () => { await settingsApi.save(current); toast("ok", "Settings saved"); }}>
            <Field label="Bank name"><input value={current.bankName || ""} onChange={(e) => setForm({ ...form, bankName: e.target.value })} /></Field>
            <Field label="Account title"><input value={current.accountTitle || ""} onChange={(e) => setForm({ ...form, accountTitle: e.target.value })} /></Field>
            <Field label="Account number"><input value={current.accountNumber || ""} onChange={(e) => setForm({ ...form, accountNumber: e.target.value })} /></Field>
            <Field label="JazzCash"><input value={current.jazzcash || ""} onChange={(e) => setForm({ ...form, jazzcash: e.target.value })} /></Field>
            <Field label="Easypaisa"><input value={current.easypaisa || ""} onChange={(e) => setForm({ ...form, easypaisa: e.target.value })} /></Field>
            <Field label="Instructions"><textarea value={current.paymentInstructions || ""} onChange={(e) => setForm({ ...form, paymentInstructions: e.target.value })} /></Field>
            <Button type="submit" kind="brass" loadingText="Saving…">Save</Button>
          </Form>
        </div>
      </QueryState>
    </>
  );
}

export function BillingPage() {
  const toast = useToast();
  const sub = useAsync(() => subscriptionApi.current());
  const [file, setFile] = useState<File | null>(null);
  const [ref, setRef] = useState("");
  const s = sub.data;
  return (
    <>
      <div className="page-title"><div><h1>School subscription</h1><p>Manual payment proof — ERP owner verifies</p></div></div>
      <QueryState status={sub} label="subscription">
        {!s ? <Empty title="No subscription record" /> : (
          <div className="card">
            <p><Badge value={s.status} /> · {money(s.amount, s.currency)}</p>
            <p>Period {fmtDate(s.periodStart)} → {fmtDate(s.periodEnd)}</p>
            {(s.status === "PENDING" || s.status === "REJECTED") ? (
              <Form busyLabel="Uploading…" onSubmit={async () => {
                if (!file) throw new Error("Choose a slip file first.");
                const up = await fileApi.upload(file);
                await subscriptionApi.submitPayment(s.id, { slipUrl: up.url, transactionRef: ref });
                toast("ok", "Proof sent to owner"); void sub.reload();
              }}>
                <Field label="Reference"><input value={ref} onChange={(e) => setRef(e.target.value)} /></Field>
                <Field label="Slip"><input type="file" accept="image/*,.pdf" onChange={(e) => setFile(e.target.files?.[0] || null)} required /></Field>
                <Button type="submit" kind="brass" loadingText="Uploading…">Submit proof</Button>
              </Form>
            ) : <p>Latest status is managed by the ERP owner.</p>}
          </div>
        )}
      </QueryState>
    </>
  );
}
