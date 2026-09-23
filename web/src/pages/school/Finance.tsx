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
  const { classes, className, sections } = useLookups();
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
  const payInfo = useAsync(() => (user?.role === "PARENT" || user?.role === "STUDENT" ? settingsApi.get() : Promise.resolve(null)), [user?.role]);
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [studentId, setStudentId] = useState("");
  const [month, setMonth] = useState(monthStart());
  const [dueDate, setDueDate] = useState("");
  const [chargeName, setChargeName] = useState("");
  const [chargeAmt, setChargeAmt] = useState("");
  const [extraCharges, setExtraCharges] = useState<{ name: string; amount: number }[]>([]);
  const [discPercent, setDiscPercent] = useState("");
  const [discAmount, setDiscAmount] = useState("");
  const [discStudent, setDiscStudent] = useState("");
  const [discReason, setDiscReason] = useState("");
  const chargeTypes = useAsync(() => (ops ? feeApi.chargeTypes() : Promise.resolve([])), [user?.role]);
  const classStudents = useAsync(
    () => (ops && classId ? studentApi.list(classId, sectionId || undefined) : Promise.resolve(null)),
    [ops, classId, sectionId],
  );
  const [stForm, setStForm] = useState({ name: "", classId: "", academicYear: "2026-2027", tuitionAmount: "8000" });
  const [picked, setPicked] = useState<string[]>([]);
  const [slip, setSlip] = useState<(Record<string, unknown> & { id?: string }) | null>(null);
  const [proofFor, setProofFor] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [ref, setRef] = useState("");
  const [uploadPct, setUploadPct] = useState<number | null>(null);

  if (user?.role === "PARENT" || user?.role === "STUDENT") {
    const child = (kids.data || []).find((k) => k.id === sid) || me.data;
    return (
      <>
        <div className="page-title"><div><h1>Fee challans</h1><p>Pay by uploading a bank slip — no online gateway</p></div></div>
        {user.role === "PARENT" ? <ChildSwitch childrenList={kids.data || []} /> : null}
        {payInfo.data && (payInfo.data.bankName || payInfo.data.accountNumber || payInfo.data.paymentInstructions) ? (
          <div className="card" style={{ marginBottom: 16 }}>
            <h3>How to pay</h3>
            <p>{payInfo.data.bankName ? `${payInfo.data.bankName} · ` : ""}{payInfo.data.accountTitle || ""} {payInfo.data.accountNumber || ""}{payInfo.data.iban ? ` · IBAN ${payInfo.data.iban}` : ""}</p>
            {payInfo.data.jazzcash ? <p>JazzCash {payInfo.data.jazzcash}</p> : null}
            {payInfo.data.easypaisa ? <p>Easypaisa {payInfo.data.easypaisa}</p> : null}
            {payInfo.data.paymentInstructions ? <p className="hint">{payInfo.data.paymentInstructions}</p> : null}
          </div>
        ) : null}
        <QueryState status={mine} label="fee challans">
          <Table
            headers={["Name", "Roll", "Class", "Campus", "Challan", "Month", "Due", "Amount", "Status", "Slip", ""]}
            rows={(mine.data || []).map((c) => [
              c.studentName || child?.user?.fullName || "—",
              c.rollNumber || child?.rollNumber || "—",
              className(c.classId || child?.classId),
              c.campusName || "—",
              c.challanNumber,
              fmtDate(c.month),
              fmtDate(c.dueDate),
              money(c.totalPayable),
              <Badge key={c.id} value={c.status} />,
              c.proofs?.[0]?.slipUrl ? <FileLink key={`sl${c.id}`} href={c.proofs[0].slipUrl} label="View slip" /> : "—",
              <div key={`act${c.id}`} className="row">
                <Button kind="ghost" onClick={async () => { setSlip(await feeApi.getChallan(c.id)); }}>Fee slip</Button>
                {c.status === "PAID" ? "Paid" : <Button kind="ghost" onClick={() => { setProofFor(c.id); setFile(null); setRef(""); setUploadPct(null); }}>Upload proof</Button>}
              </div>,
            ])}
          />
        </QueryState>
        <Modal title="Payment proof" open={!!proofFor} onClose={() => { setProofFor(null); setFile(null); setUploadPct(null); }}>
          <Form busyLabel="Uploading…" onSubmit={async () => {
            if (!file || !proofFor) throw new Error("Choose a slip file first.");
            setUploadPct(0);
            const up = await fileApi.upload(file, setUploadPct);
            if (!up?.url) throw new Error("Upload did not return a file URL.");
            await feeApi.submitProof(proofFor, { slipUrl: up.url, transactionRef: ref });
            toast("ok", "Proof submitted for verification");
            setProofFor(null);
            setFile(null);
            setUploadPct(null);
            void mine.reload();
          }}>
            <Field label="Reference"><input value={ref} onChange={(e) => setRef(e.target.value)} /></Field>
            <Field label="Slip screenshot or PDF">
              <input type="file" accept="image/*,.pdf,.doc,.docx" onChange={(e) => setFile(e.target.files?.[0] || null)} required />
            </Field>
            {file ? <p className="hint" style={{ margin: 0 }}>{file.name} · {Math.round(file.size / 1024)} KB{file.type ? ` · ${file.type}` : ""}</p> : null}
            {uploadPct != null ? <p className="hint">Uploading {uploadPct}%</p> : null}
            <Button type="submit" kind="brass" loadingText="Uploading…">Submit</Button>
          </Form>
        </Modal>
        <ChallanSlipModal slip={slip} onClose={() => setSlip(null)} className={className} />
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
              if (!classId && !studentId) throw new Error("Select a class, section or student.");
              const ids = picked.length ? picked : studentId ? [studentId] : undefined;
              const created = await feeApi.generate({
                classId: classId || undefined,
                sectionId: sectionId || undefined,
                studentIds: ids,
                month,
                dueDate: dueDate || undefined,
                charges: extraCharges,
                discountPercent: discPercent && !(ids && ids.length === 1) ? undefined : discPercent ? Number(discPercent) : undefined,
                discountAmount: discAmount && !(ids && ids.length === 1) ? undefined : discAmount ? Number(discAmount) : undefined,
              });
              toast("ok", created.length ? `${created.length} challans generated` : "No new challans — already issued for this month");
              setStudentId("");
              void pending.reload();
            }}>
              <Field label="Class">
                <select value={classId} onChange={(e) => { setClassId(e.target.value); setSectionId(""); setStudentId(""); }}>
                  <option value="">Select</option>
                  {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </Field>
              <Field label="Section (optional — blank = whole class)">
                <select value={sectionId} onChange={(e) => { setSectionId(e.target.value); setStudentId(""); }} disabled={!classId}>
                  <option value="">All sections</option>
                  {(sections[classId] || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </Field>
              <Field label="Student (optional — blank = section/class)">
                <select value={studentId} onChange={(e) => { setStudentId(e.target.value); setPicked(e.target.value ? [e.target.value] : []); }} disabled={!classId}>
                  <option value="">All students in selection</option>
                  {(classStudents.data?.content || []).map((s) => (
                    <option key={s.id} value={s.id}>{s.user?.fullName || s.admissionNumber} · roll {s.rollNumber || "—"}</option>
                  ))}
                </select>
              </Field>
              <Field label="Or multi-select students">
                <select
                  multiple
                  size={Math.min(6, Math.max(3, (classStudents.data?.content || []).length || 3))}
                  value={picked}
                  disabled={!classId}
                  onChange={(e) => {
                    const next = Array.from(e.target.selectedOptions).map((o) => o.value);
                    setPicked(next);
                    setStudentId(next.length === 1 ? next[0] : "");
                  }}
                >
                  {(classStudents.data?.content || []).map((s) => (
                    <option key={s.id} value={s.id}>{s.user?.fullName || s.admissionNumber} · roll {s.rollNumber || "—"}</option>
                  ))}
                </select>
              </Field>
              <Field label="Month"><input type="month" value={month.slice(0, 7)} onChange={(e) => setMonth(`${e.target.value}-01`)} /></Field>
              <Field label="Due date"><input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} /></Field>
              <Field label="One-off discount % (this generate, selected student)">
                <input value={discPercent} onChange={(e) => setDiscPercent(e.target.value)} placeholder="e.g. 10" disabled={!studentId} />
              </Field>
              <Field label="Custom charges this generate">
                <div className="row">
                  <input value={chargeName} onChange={(e) => setChargeName(e.target.value)} placeholder="AC / exam / camp" />
                  <input value={chargeAmt} onChange={(e) => setChargeAmt(e.target.value)} placeholder="Amount" />
                  <Button kind="ghost" onClick={() => {
                    if (!chargeName.trim() || !chargeAmt) return;
                    setExtraCharges((c) => [...c, { name: chargeName.trim(), amount: Number(chargeAmt) }]);
                    setChargeName(""); setChargeAmt("");
                  }}>Add</Button>
                </div>
                {(chargeTypes.data || []).map((t) => (
                  <Button key={t.id} kind="ghost" onClick={() => setExtraCharges((c) => [...c, { name: t.name, amount: t.defaultAmount }])}>{t.name}</Button>
                ))}
                {extraCharges.length ? <p className="hint">{extraCharges.map((c) => `${c.name} ${c.amount}`).join(" · ")}</p> : null}
              </Field>
              <Button type="submit" kind="brass" loadingText="Generating…">Generate & send</Button>
            </Form>
            <Form busyLabel="Saving charge…" onSubmit={async () => {
              if (!chargeName.trim()) throw new Error("Charge name required");
              await feeApi.saveChargeType({ name: chargeName.trim(), defaultAmount: Number(chargeAmt || 0) });
              toast("ok", "Charge type saved"); void chargeTypes.reload();
            }}>
              <Button type="submit" kind="ghost" loadingText="Saving…">Save as reusable charge</Button>
            </Form>
            <Form busyLabel="Applying discount…" onSubmit={async () => {
              const sid = discStudent || studentId;
              if (!sid) throw new Error("Select a student for the standing discount.");
              await feeApi.applyDiscount(sid, { percent: discPercent ? Number(discPercent) : undefined, amount: discAmount ? Number(discAmount) : undefined, reason: discReason });
              toast("ok", "Standing discount saved for next challans");
            }}>
              <Field label="Standing discount student">
                <select value={discStudent} onChange={(e) => setDiscStudent(e.target.value)}>
                  <option value="">Use generate student</option>
                  {(classStudents.data?.content || []).map((s) => (
                    <option key={s.id} value={s.id}>{s.user?.fullName || s.admissionNumber}</option>
                  ))}
                </select>
              </Field>
              <Field label="Fixed amount"><input value={discAmount} onChange={(e) => setDiscAmount(e.target.value)} /></Field>
              <Field label="Reason"><input value={discReason} onChange={(e) => setDiscReason(e.target.value)} /></Field>
              <Button type="submit" kind="ghost" loadingText="Saving…">Save standing discount</Button>
            </Form>
          </div>
        ) : (
          <div className="card">
            <h3>Unpaid challans</h3>
            <QueryState status={unpaid} label="unpaid challans">
              <Table
                headers={["Name", "Roll", "Class", "Challan", "Month", "Due", "Amount", "Status"]}
                rows={(unpaid.data || []).map((c) => [
                  c.studentName || "—",
                  c.rollNumber || "—",
                  className(c.classId),
                  c.challanNumber,
                  fmtDate(c.month),
                  fmtDate(c.dueDate),
                  money(c.totalPayable),
                  pretty(c.status),
                ])}
              />
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
                headers={["Student", "Roll", "Challan", "Month", "Amount", "Ref", "Slip", "Status", ""]}
                rows={(pending.data || []).map((p) => [
                  p.studentName || "—",
                  p.rollNumber || "—",
                  p.challanNumber || p.challanId?.slice(0, 8) || p.id.slice(0, 8),
                  fmtDate(p.month),
                  money(p.totalPayable),
                  p.transactionRef || "—",
                  <FileLink key={`s${p.id}`} href={p.slipUrl} label="Open slip" />,
                  pretty(p.status),
                  <div key={p.id} className="row">
                    <Button kind="ok" loadingText="Approving…" onClick={async () => { await feeApi.reviewProof(p.id, true); toast("ok", "Approved — challan PAID"); void pending.reload(); }}>Approve</Button>
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

function ChallanSlipModal({
  slip,
  onClose,
  className,
}: {
  slip: (Record<string, unknown> & { id?: string }) | null;
  onClose: () => void;
  className: (id?: string) => string;
}) {
  if (!slip) return null;
  const charges = Array.isArray(slip.charges) ? (slip.charges as { name?: string; amount?: number }[]) : [];
  return (
    <Modal title="Fee slip" open onClose={onClose}>
      <div className="card">
        <h3>{String(slip.schoolName || "School")}</h3>
        <p className="hint">{[slip.schoolAddress, slip.schoolCity, slip.schoolPhone].filter(Boolean).join(" · ")}</p>
        <p>{String(slip.bankName || "")} · {String(slip.accountTitle || "")} {String(slip.accountNumber || "")}{slip.iban ? ` · IBAN ${String(slip.iban)}` : ""}</p>
        <p>{String(slip.studentName || "—")} · roll {String(slip.rollNumber || "—")} · {className(slip.classId as string | undefined)}</p>
        <p>Challan {String(slip.challanNumber || "—")} · month {fmtDate(slip.month as string | undefined)} · due {fmtDate(slip.dueDate as string | undefined)}</p>
        <Table
          headers={["Line", "Amount"]}
          rows={[
            ["Tuition", money(slip.tuitionFee as number)],
            ["Previous outstanding", money(slip.previousOutstanding as number)],
            ["Additional charges", money(slip.additionalCharges as number)],
            ["Discount", money(slip.discountAmount as number)],
            ...charges.map((c) => [c.name || "Charge", money(c.amount)]),
            ["Total payable", money(slip.totalPayable as number)],
          ]}
        />
        <p><Badge value={String(slip.status || "")} /></p>
      </div>
    </Modal>
  );
}

function canOperateFees(role?: string) {
  return role === "ACCOUNT_OFFICER" || role === "ERP_OWNER";
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
  const [adjustId, setAdjustId] = useState<string | null>(null);
  const [deduct, setDeduct] = useState("");
  const [bonus, setBonus] = useState("");
  const [adjNotes, setAdjNotes] = useState("");
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
          headers={["Staff", "Base", "Deductions", "Bonus", "Net", "Status", ""]}
          rows={(monthRows.data || []).map((s) => [
            (users.data?.content || []).find((u) => u.id === s.staffUserId)?.fullName || s.staffUserId.slice(0, 8),
            money(s.baseSalary),
            money(s.otherDeductions),
            money(s.bonuses),
            money(s.netPay),
            <Badge key={s.id} value={s.status} />,
            !ops || s.status === "PAID" ? pretty(s.status) : (
              <div key={`pay${s.id}`} className="row">
                <Button kind="ghost" onClick={() => { setAdjustId(s.id); setDeduct(String(s.otherDeductions ?? "")); setBonus(String(s.bonuses ?? "")); setAdjNotes(""); }}>Adjust</Button>
                <Button loadingText="Updating…" onClick={async () => { await salaryApi.pay(s.id, today()); toast("ok", "Marked paid"); void monthRows.reload(); }}>Mark paid</Button>
              </div>
            ),
          ])}
        />
      </QueryState>
      <Modal title="Salary deduction / bonus" open={!!adjustId} onClose={() => setAdjustId(null)}>
        <Form busyLabel="Saving…" onSubmit={async () => {
          if (!adjustId) return;
          await salaryApi.adjust(adjustId, {
            otherDeductions: deduct ? Number(deduct) : 0,
            bonuses: bonus ? Number(bonus) : 0,
            notes: adjNotes,
          });
          toast("ok", "Salary updated");
          setAdjustId(null);
          void monthRows.reload();
        }}>
          <Field label="Other deductions"><input value={deduct} onChange={(e) => setDeduct(e.target.value)} /></Field>
          <Field label="Bonus"><input value={bonus} onChange={(e) => setBonus(e.target.value)} /></Field>
          <Field label="Notes"><input value={adjNotes} onChange={(e) => setAdjNotes(e.target.value)} /></Field>
          <Button type="submit" kind="brass" loadingText="Saving…">Save</Button>
        </Form>
      </Modal>
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
            <Field label="IBAN"><input value={current.iban || ""} onChange={(e) => setForm({ ...form, iban: e.target.value })} /></Field>
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
                if (!up?.url) throw new Error("Upload did not return a file URL.");
                await subscriptionApi.submitPayment(s.id, { slipUrl: up.url, transactionRef: ref });
                toast("ok", "Proof sent to owner"); void sub.reload();
              }}>
                <Field label="Reference"><input value={ref} onChange={(e) => setRef(e.target.value)} /></Field>
                <Field label="Slip"><input type="file" accept="image/*,.pdf" onChange={(e) => setFile(e.target.files?.[0] || null)} required /></Field>
                {file ? <p className="hint" style={{ margin: 0 }}>{file.name} · {Math.round(file.size / 1024)} KB</p> : null}
                <Button type="submit" kind="brass" loadingText="Uploading…">Submit proof</Button>
              </Form>
            ) : <p>Latest status is managed by the ERP owner.</p>}
          </div>
        )}
      </QueryState>
    </>
  );
}
