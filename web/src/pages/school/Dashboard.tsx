import { useSession } from "../../lib/session";
import { portalApi, reportApi, studentApi } from "../../api/services";
import { Badge, Empty, ErrorBox, Loading, Stat, Table, useAsync } from "../../ui/kit";
import { money, pretty } from "../../lib/format";
import ChildSwitch from "./ChildSwitch";

export default function Dashboard() {
  const { user } = useSession();
  if (user?.role === "TEACHER") return <TeacherDash />;
  if (user?.role === "ACCOUNT_OFFICER") return <AccountDash />;
  if (user?.role === "PARENT") return <ParentDash />;
  if (user?.role === "STUDENT") return <StudentDash />;
  return <StaffDash />;
}

function StaffDash() {
  const d = useAsync(() => reportApi.schoolDashboard());
  if (d.loading) return <Loading />;
  if (d.error) return <ErrorBox error={d.error} />;
  const m = d.data || {};
  return (
    <>
      <div className="page-title">
        <div>
          <h1>School dashboard</h1>
          <p>Live aggregates from /api/v1/dashboard/school</p>
        </div>
      </div>
      <div className="grid stats">
        <Stat label="Students" value={m.students} />
        <Stat label="Active students" value={m.activeStudents} />
        <Stat label="Teachers" value={m.teachers} />
        <Stat label="Unpaid challans" value={m.unpaidChallans} />
      </div>
      <div className="grid two" style={{ marginTop: 16 }}>
        <div className="card">
          <h3>Operations</h3>
          <p>Parents {m.parents} · Pending fee proofs {m.pendingFeeProofs} · Active users {m.activeUsers}</p>
        </div>
        <div className="card">
          <h3>Today</h3>
          <p>Use the sidebar to mark attendance, publish results, or generate monthly challans. Every action writes to Neon.</p>
        </div>
      </div>
    </>
  );
}

function TeacherDash() {
  const d = useAsync(() => portalApi.teacher());
  if (d.loading) return <Loading />;
  if (d.error) return <ErrorBox error={d.error} />;
  const lectures = (d.data?.todayLectures as { id: string; startTime: string; endTime: string }[]) || [];
  const homework = (d.data?.homework as { id: string; title: string; dueDate: string }[]) || [];
  return (
    <>
      <div className="page-title"><div><h1>Teacher desk</h1><p>Today’s lectures and assigned homework</p></div></div>
      <div className="grid two">
        <div className="card">
          <h3>Today’s lectures</h3>
          {!lectures.length ? <Empty title="No lectures today" /> : (
            <Table headers={["Slot", "Start", "End"]} rows={lectures.map((l) => [l.id.slice(0, 8), l.startTime, l.endTime])} />
          )}
        </div>
        <div className="card">
          <h3>Homework</h3>
          {!homework.length ? <Empty title="No homework assigned" /> : (
            <Table headers={["Title", "Due"]} rows={homework.map((h) => [h.title, h.dueDate])} />
          )}
        </div>
      </div>
    </>
  );
}

function AccountDash() {
  const d = useAsync(() => portalApi.account());
  if (d.loading) return <Loading />;
  if (d.error) return <ErrorBox error={d.error} />;
  const pending = (d.data?.pendingProofs as { id: string; transactionRef?: string; status?: string }[]) || [];
  return (
    <>
      <div className="page-title"><div><h1>Finance desk</h1><p>Challans and payment proofs</p></div></div>
      <div className="grid stats">
        <Stat label="Students" value={d.data?.students as number} />
        <Stat label="Unpaid" value={d.data?.unpaidChallans as number} />
        <Stat label="Pending proofs" value={pending.length} />
      </div>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Proofs to verify</h3>
        {!pending.length ? <Empty title="Queue is clear" /> : (
          <Table headers={["Proof", "Ref", "Status"]} rows={pending.map((p) => [p.id.slice(0, 8), p.transactionRef || "—", <Badge key={p.id} value={p.status} />])} />
        )}
      </div>
    </>
  );
}

function ParentDash() {
  const d = useAsync(() => portalApi.parent());
  const kids = useAsync(() => studentApi.children());
  if (d.loading || kids.loading) return <Loading />;
  if (d.error) return <ErrorBox error={d.error} />;
  const fees = (d.data?.fees as { challanNumber: string; totalPayable: number; status: string }[]) || [];
  return (
    <>
      <div className="page-title"><div><h1>Family</h1><p>One parent login, many children</p></div></div>
      <ChildSwitch childrenList={kids.data || []} />
      <div className="card">
        <h3>Fee challans</h3>
        {!fees.length ? <Empty title="No challans" /> : (
          <Table headers={["Challan", "Amount", "Status"]} rows={fees.map((f) => [f.challanNumber, money(f.totalPayable), pretty(f.status)])} />
        )}
      </div>
    </>
  );
}

function StudentDash() {
  const d = useAsync(() => portalApi.student());
  if (d.loading) return <Loading />;
  if (d.error) return <ErrorBox error={d.error} />;
  const hw = (d.data?.homework as { title: string; dueDate: string }[]) || [];
  const fees = (d.data?.fees as { challanNumber: string; totalPayable: number; status: string }[]) || [];
  return (
    <>
      <div className="page-title"><div><h1>My school</h1><p>Homework and fees from the live student portal</p></div></div>
      <div className="grid two">
        <div className="card">
          <h3>Homework</h3>
          {!hw.length ? <Empty title="Nothing assigned" /> : <Table headers={["Title", "Due"]} rows={hw.map((h) => [h.title, h.dueDate])} />}
        </div>
        <div className="card">
          <h3>Fees</h3>
          {!fees.length ? <Empty title="No challans" /> : <Table headers={["Challan", "Amount", "Status"]} rows={fees.map((f) => [f.challanNumber, money(f.totalPayable), pretty(f.status)])} />}
        </div>
      </div>
    </>
  );
}
