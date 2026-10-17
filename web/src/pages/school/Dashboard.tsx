import { useMemo } from "react";
import { useSession } from "../../lib/session";
import { academicApi, feeApi, homeworkApi, portalApi, reportApi, studentApi } from "../../api/services";
import { Badge, Bars, Donut, Empty, ErrorBox, QueryState, Stat, Table, useAsync } from "../../ui/kit";
import { money, pretty } from "../../lib/format";
import type { StudentUser } from "../../lib/types";
import ChildSwitch from "./ChildSwitch";

export default function Dashboard() {
  const { user } = useSession();
  if (user?.role === "TEACHER") return <TeacherDash />;
  if (user?.role === "ACCOUNT_OFFICER") return <AccountDash />;
  if (user?.role === "PARENT") return <ParentDash />;
  if (user?.role === "STUDENT") return <StudentDash />;
  return <StaffDash />;
}

function asTime(value: unknown) {
  if (value == null) return "—";
  if (typeof value === "string") return value.length >= 5 ? value.slice(0, 5) : value;
  if (typeof value === "object") {
    const t = value as { hour?: number; minute?: number };
    if (typeof t.hour === "number") {
      return `${String(t.hour).padStart(2, "0")}:${String(t.minute ?? 0).padStart(2, "0")}`;
    }
  }
  return String(value);
}

function StaffDash() {
  const d = useAsync(() => reportApi.schoolDashboard());
  const m = d.data || {};
  const students = Number(m.students || 0);
  const active = Number(m.activeStudents || 0);
  const teachers = Number(m.teachers || 0);
  const unpaid = Number(m.unpaidChallans || 0);
  const parents = Number(m.parents || 0);
  return (
    <>
      <div className="hero-strip card">
        <div>
          <p className="kicker">Live school census</p>
          <h1>School dashboard</h1>
          <p>Students, staff and unpaid challans for this school.</p>
        </div>
        <Donut value={active} max={Math.max(students, 1)} label="Active students" />
      </div>
      <QueryState status={d} label="school dashboard">
        <div className="grid stats">
          <Stat label="Students" value={students} loading={d.loading} />
          <Stat label="Active students" value={active} loading={d.loading} />
          <Stat label="Teachers" value={teachers} loading={d.loading} />
          <Stat label="Unpaid challans" value={unpaid} loading={d.loading} />
        </div>
        <div className="grid two" style={{ marginTop: 16 }}>
          <div className="card chart-card">
            <h3>Census</h3>
            <Bars items={[
              { label: "Students", value: students },
              { label: "Teachers", value: teachers },
              { label: "Parents", value: parents },
              { label: "Unpaid", value: unpaid },
            ]} />
          </div>
          <div className="card">
            <h3>Operations</h3>
            <p>Classes {m.classes ?? 0} · Pending fee proofs {m.pendingFeeProofs ?? 0} · Active users {m.activeUsers ?? 0}</p>
            <p className="hint">Mark attendance, search results by roll number, or generate monthly challans.</p>
          </div>
        </div>
      </QueryState>
    </>
  );
}

function TeacherDash() {
  const lecturesQ = useAsync(() => academicApi.teacherToday());
  const homeworkQ = useAsync(() => homeworkApi.list(undefined, undefined, 8));
  const progressQ = useAsync(() => reportApi.teacherProgress());
  const lectures = (lecturesQ.data || []) as {
    id: string;
    startTime: string;
    endTime: string;
    className?: string;
    sectionName?: string;
    subjectName?: string;
  }[];
  const homework = homeworkQ.data || [];
  const progress = (progressQ.data || {}) as { attendancePercent?: number; present?: number; absent?: number; late?: number; totalDays?: number };
  const att = Number(progress.attendancePercent || 0);
  return (
    <>
      <div className="hero-strip card">
        <div>
          <p className="kicker">Your month</p>
          <h1>Teacher desk</h1>
          <p>Attendance {att}% · Present {progress.present ?? 0} · Absent {progress.absent ?? 0} · Late {progress.late ?? 0} of {progress.totalDays ?? 0} marked days.</p>
        </div>
        <Donut value={att} max={100} label="Attendance %" />
      </div>
      {progressQ.error && !progressQ.data ? <ErrorBox error={progressQ.error} onRetry={() => void progressQ.reload()} title="Unable to load teacher attendance" /> : null}
      <div className="grid two">
        <div className="card">
          <h3>Today’s lectures</h3>
          <QueryState status={lecturesQ} label="today’s lectures">
            {!lectures.length ? <Empty title="No lectures today" /> : (
              <Table
                headers={["Class", "Subject", "Start", "End"]}
                rows={lectures.map((l) => [
                  [l.className, l.sectionName].filter(Boolean).join(" · ") || "—",
                  l.subjectName || "—",
                  asTime(l.startTime),
                  asTime(l.endTime),
                ])}
              />
            )}
          </QueryState>
        </div>
        <div className="card">
          <h3>Homework</h3>
          <QueryState status={homeworkQ} label="homework">
            {!homework.length ? <Empty title="No homework assigned" /> : (
              <Table headers={["Title", "Due"]} rows={homework.map((h) => [h.title, h.dueDate])} />
            )}
          </QueryState>
        </div>
      </div>
    </>
  );
}

function AccountDash() {
  const d = useAsync(() => reportApi.schoolDashboard());
  const proofsQ = useAsync(() => feeApi.pendingProofs(12));
  const pending = proofsQ.data || [];
  const unpaidCount = Number(d.data?.unpaidChallans || 0);
  return (
    <>
      <div className="page-title"><div><h1>Finance desk</h1><p>Challans and payment proofs</p></div></div>
      <QueryState status={d} label="finance desk">
        <div className="grid stats">
          <Stat label="Students" value={d.data?.students as number} loading={d.loading} />
          <Stat label="Unpaid" value={unpaidCount} loading={d.loading} />
          <Stat label="Pending proofs" value={Number(d.data?.pendingFeeProofs || 0)} loading={d.loading} />
        </div>
      </QueryState>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Proofs to verify</h3>
        <QueryState status={proofsQ} label="pending proofs">
          {!pending.length ? <Empty title="Queue is clear" /> : (
            <Table headers={["Proof", "Ref", "Status"]} rows={pending.map((p) => [p.id.slice(0, 8), p.transactionRef || "—", <Badge key={p.id} value={p.status} />])} />
          )}
        </QueryState>
      </div>
    </>
  );
}

function ParentDash() {
  const { childId } = useSession();
  const d = useAsync(() => portalApi.parent());
  const kids = useMemo(() => (d.data?.children as StudentUser[]) || [], [d.data]);
  const all = useMemo(
    () => (d.data?.fees as { studentId?: string; challanNumber: string; totalPayable: number; status: string }[]) || [],
    [d.data],
  );
  const fees = childId ? all.filter((f) => f.studentId === childId) : all;
  const progressMap = useMemo(() => (d.data?.progressByChild as Record<string, {
    attendancePercent?: number; academicPercent?: number; present?: number; absent?: number; late?: number;
    totalLectures?: number; grade?: string; passStatus?: string;
  }>) || {}, [d.data]);
  const activeId = childId || kids[0]?.id;
  const progress = (activeId && progressMap[activeId]) || {};
  const att = Number(progress.attendancePercent || 0);
  const aca = Number(progress.academicPercent || 0);
  return (
    <>
      <div className="hero-strip card">
        <div>
          <p className="kicker">Selected child</p>
          <h1>Family</h1>
          <p>Attendance {att}% ({progress.present ?? 0} present, {progress.absent ?? 0} absent, {progress.late ?? 0} late). Academic {aca}% · Grade {progress.grade || "—"} · {pretty(progress.passStatus)}.</p>
        </div>
        <div className="row">
          <Donut value={att} max={100} label="Attendance %" />
          <Donut value={aca} max={100} label="Academics %" />
        </div>
      </div>
      <QueryState status={d} label="family dashboard">
        <ChildSwitch childrenList={kids} />
        <div className="card">
          <h3>Fee challans</h3>
          {!fees.length ? <Empty title="No challans" /> : (
            <Table headers={["Challan", "Amount", "Status"]} rows={fees.map((f) => [f.challanNumber, money(f.totalPayable), pretty(f.status)])} />
          )}
        </div>
      </QueryState>
    </>
  );
}

function StudentDash() {
  const me = useAsync(() => studentApi.me());
  const homeworkQ = useAsync(() => homeworkApi.list(undefined, undefined, 8));
  const feesQ = useAsync(() => feeApi.myChallanSummaries(20));
  const progressQ = useAsync(() => reportApi.myProgress());
  const hw = homeworkQ.data || [];
  const fees = feesQ.data || [];
  const progress = (progressQ.data || {}) as {
    attendancePercent?: number; academicPercent?: number; present?: number; absent?: number; late?: number;
    totalLectures?: number; grade?: string; passStatus?: string; obtainedMarks?: number; totalMarks?: number;
  };
  const att = Number(progress.attendancePercent || 0);
  const aca = Number(progress.academicPercent || 0);
  return (
    <>
      <div className="hero-strip card">
        <div>
          <p className="kicker">This month</p>
          <h1>{me.data?.user?.fullName || me.data?.fullName || "My school"}</h1>
          <p>Attendance {att}% ({progress.present ?? 0} present, {progress.absent ?? 0} absent, {progress.late ?? 0} late of {progress.totalLectures ?? 0} lectures). Academic {aca}% · Grade {progress.grade || "—"} · {pretty(progress.passStatus)} · {progress.obtainedMarks ?? 0}/{progress.totalMarks ?? 0}.</p>
        </div>
        <div className="row">
          <Donut value={att} max={100} label="Attendance %" />
          <Donut value={aca} max={100} label="Academics %" />
        </div>
      </div>
      {me.error && !me.data ? <ErrorBox error={me.error} onRetry={() => void me.reload()} title="Unable to load student profile" /> : null}
      {progressQ.error && !progressQ.data ? <ErrorBox error={progressQ.error} onRetry={() => void progressQ.reload()} title="Unable to load attendance and results" /> : null}
      <div className="grid two">
        <div className="card">
          <h3>Homework</h3>
          <QueryState status={homeworkQ} label="homework">
            {!hw.length ? <Empty title="Nothing assigned" /> : <Table headers={["Title", "Due"]} rows={hw.map((h) => [h.title, h.dueDate])} />}
          </QueryState>
        </div>
        <div className="card">
          <h3>Fees</h3>
          <QueryState status={feesQ} label="fees">
            {!fees.length ? <Empty title="No challans" /> : <Table headers={["Challan", "Amount", "Status"]} rows={fees.map((f) => [f.challanNumber, money(f.totalPayable), pretty(f.status)])} />}
          </QueryState>
        </div>
      </div>
    </>
  );
}
