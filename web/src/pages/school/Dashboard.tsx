import { useMemo } from "react";
import { useSession } from "../../lib/session";
import { portalApi, reportApi } from "../../api/services";
import { Badge, Bars, Donut, Empty, QueryState, Stat, Table, useAsync } from "../../ui/kit";
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
          <p>Aggregates from /api/v1/dashboard/school — not a static template.</p>
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
            <p>Pending fee proofs {m.pendingFeeProofs ?? 0} · Active users {m.activeUsers ?? 0}</p>
            <p className="hint">Mark attendance, search results by roll number, or generate monthly challans. Every action writes to Neon.</p>
          </div>
        </div>
      </QueryState>
    </>
  );
}

function TeacherDash() {
  const d = useAsync(() => portalApi.teacher());
  const lectures = (d.data?.todayLectures as {
    id: string;
    startTime: string;
    endTime: string;
    className?: string;
    sectionName?: string;
    subjectName?: string;
  }[]) || [];
  const homework = (d.data?.homework as { id: string; title: string; dueDate: string }[]) || [];
  const progress = (d.data?.progress as { attendancePercent?: number; present?: number; absent?: number; late?: number; totalDays?: number }) || {};
  const att = Number(progress.attendancePercent || 0);
  return (
    <QueryState status={d} label="teacher desk">
      <div className="hero-strip card">
        <div>
          <p className="kicker">Your month</p>
          <h1>Teacher desk</h1>
          <p>Attendance {att}% · Present {progress.present ?? 0} · Absent {progress.absent ?? 0} · Late {progress.late ?? 0} of {progress.totalDays ?? 0} marked days.</p>
        </div>
        <Donut value={att} max={100} label="Attendance %" />
      </div>
        <div className="grid two">
          <div className="card">
            <h3>Today’s lectures</h3>
            {!lectures.length ? <Empty title="No lectures today" /> : (
              <Table
                headers={["Class", "Subject", "Start", "End"]}
                rows={lectures.map((l) => [
                  [l.className, l.sectionName].filter(Boolean).join(" · ") || "—",
                  l.subjectName || "—",
                  l.startTime,
                  l.endTime,
                ])}
              />
            )}
          </div>
          <div className="card">
            <h3>Homework</h3>
            {!homework.length ? <Empty title="No homework assigned" /> : (
              <Table headers={["Title", "Due"]} rows={homework.map((h) => [h.title, h.dueDate])} />
            )}
          </div>
        </div>
    </QueryState>
  );
}

function AccountDash() {
  const d = useAsync(() => portalApi.account());
  const pending = (d.data?.pendingProofs as { id: string; transactionRef?: string; status?: string }[]) || [];
  const unpaidRaw = d.data?.unpaidChallans;
  const unpaidCount = Array.isArray(unpaidRaw) ? unpaidRaw.length : Number(unpaidRaw || 0);
  return (
    <>
      <div className="page-title"><div><h1>Finance desk</h1><p>Challans and payment proofs</p></div></div>
      <QueryState status={d} label="finance desk">
        <div className="grid stats">
          <Stat label="Students" value={d.data?.students as number} loading={d.loading} />
          <Stat label="Unpaid" value={unpaidCount} loading={d.loading} />
          <Stat label="Pending proofs" value={pending.length} loading={d.loading} />
        </div>
        <div className="card" style={{ marginTop: 16 }}>
          <h3>Proofs to verify</h3>
          {!pending.length ? <Empty title="Queue is clear" /> : (
            <Table headers={["Proof", "Ref", "Status"]} rows={pending.map((p) => [p.id.slice(0, 8), p.transactionRef || "—", <Badge key={p.id} value={p.status} />])} />
          )}
        </div>
      </QueryState>
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
    <QueryState status={d} label="family dashboard">
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
        <ChildSwitch childrenList={kids} />
        <div className="card">
          <h3>Fee challans</h3>
          {!fees.length ? <Empty title="No challans" /> : (
            <Table headers={["Challan", "Amount", "Status"]} rows={fees.map((f) => [f.challanNumber, money(f.totalPayable), pretty(f.status)])} />
          )}
        </div>
    </QueryState>
  );
}

function StudentDash() {
  const d = useAsync(() => portalApi.student());
  const hw = (d.data?.homework as { title: string; dueDate: string }[]) || [];
  const fees = (d.data?.fees as { challanNumber: string; totalPayable: number; status: string }[]) || [];
  const progress = (d.data?.progress as {
    attendancePercent?: number; academicPercent?: number; present?: number; absent?: number; late?: number;
    totalLectures?: number; grade?: string; passStatus?: string; obtainedMarks?: number; totalMarks?: number;
  }) || {};
  const att = Number(progress.attendancePercent || 0);
  const aca = Number(progress.academicPercent || 0);
  return (
    <QueryState status={d} label="student dashboard">
      <div className="hero-strip card">
        <div>
          <p className="kicker">This month</p>
          <h1>My school</h1>
          <p>Attendance {att}% ({progress.present ?? 0} present, {progress.absent ?? 0} absent, {progress.late ?? 0} late of {progress.totalLectures ?? 0} lectures). Academic {aca}% · Grade {progress.grade || "—"} · {pretty(progress.passStatus)} · {progress.obtainedMarks ?? 0}/{progress.totalMarks ?? 0}.</p>
        </div>
        <div className="row">
          <Donut value={att} max={100} label="Attendance %" />
          <Donut value={aca} max={100} label="Academics %" />
        </div>
      </div>
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
    </QueryState>
  );
}
