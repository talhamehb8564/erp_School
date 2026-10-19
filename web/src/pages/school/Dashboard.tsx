import { useSession } from "../../lib/session";
import { useFeatures } from "../../lib/features";
import { academicApi, examApi, feeApi, homeworkApi, reportApi, studentApi } from "../../api/services";
import { Badge, Bars, Donut, Empty, ErrorBox, QueryState, Stat, Table, useAsync } from "../../ui/kit";
import ResultCountdown, { isUpcomingExam, pickResultSession } from "../../ui/ResultCountdown";
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

function UpcomingResultCard() {
  const { on, flags } = useFeatures();
  const sessions = useAsync(() => on("RESULTS") ? examApi.sessions() : Promise.resolve([]), [flags.RESULTS]);
  const rows = sessions.data || [];
  const id = pickResultSession(rows);
  const selected = rows.find((s) => s.id === id);
  if (!on("RESULTS") || !selected || !isUpcomingExam(selected.announceAt, undefined, selected.published)) return null;
  return <ResultCountdown announceAt={selected.announceAt} sessionName={selected.name} onDone={() => void sessions.reload()} />;
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
  const { on } = useFeatures();
  const d = useAsync(() => reportApi.schoolDashboard());
  const m = d.data || {};
  const students = Number(m.students || 0);
  const active = Number(m.activeStudents || 0);
  const teachers = Number(m.teachers || 0);
  const unpaid = Number(m.unpaidChallans || 0);
  const parents = Number(m.parents || 0);
  const feesOn = on("FEES");
  return (
    <>
      <div className="hero-strip card">
        <div>
          <p className="kicker">Live school census</p>
          <h1>School dashboard</h1>
          <p>Students, staff{feesOn ? " and unpaid challans" : ""} for this school.</p>
        </div>
        <div className="hero-wheels">
          <Donut value={active} max={Math.max(students, 1)} label="Active students" />
        </div>
      </div>
      <QueryState status={d} label="school dashboard">
        <div className="grid stats">
          <Stat label="Students" value={students} loading={d.loading} />
          <Stat label="Active students" value={active} loading={d.loading} />
          <Stat label="Teachers" value={teachers} loading={d.loading} />
          {feesOn ? <Stat label="Unpaid challans" value={unpaid} loading={d.loading} /> : null}
        </div>
        <div className="grid two" style={{ marginTop: 16 }}>
          <div className="card chart-card">
            <h3>Census</h3>
            <Bars items={[
              { label: "Students", value: students },
              { label: "Teachers", value: teachers },
              { label: "Parents", value: parents },
              ...(feesOn ? [{ label: "Unpaid", value: unpaid }] : []),
            ]} />
          </div>
          <div className="card">
            <h3>Operations</h3>
            <p>Classes {m.classes ?? 0}{feesOn ? ` · Pending fee proofs ${m.pendingFeeProofs ?? 0}` : ""} · Active users {m.activeUsers ?? 0}</p>
            <p className="hint">
              {[
                on("ATTENDANCE") ? "Mark attendance" : null,
                on("RESULTS") ? "search results by roll number" : null,
                feesOn ? "or generate monthly challans" : null,
              ].filter(Boolean).join(", ") || "Open a module from the sidebar."}.
            </p>
          </div>
        </div>
      </QueryState>
    </>
  );
}

function TeacherDash() {
  return (
    <>
      <UpcomingResultCard />
      <TeacherDashBody />
    </>
  );
}

function TeacherDashBody() {
  const { on, flags } = useFeatures();
  const lecturesQ = useAsync(() => on("TIMETABLE") ? academicApi.teacherToday() : Promise.resolve([]), [flags.TIMETABLE]);
  const homeworkQ = useAsync(() => on("HOMEWORK") ? homeworkApi.list(undefined, undefined, 8) : Promise.resolve([]), [flags.HOMEWORK]);
  const progressQ = useAsync(() => on("ATTENDANCE") || on("TEACHER_ATTENDANCE")
    ? reportApi.teacherProgress()
    : Promise.resolve({}), [flags.ATTENDANCE, flags.TEACHER_ATTENDANCE]);
  const lectures = (lecturesQ.data || []) as {
    id: string;
    startTime: string;
    endTime: string;
    className?: string;
    sectionName?: string;
    subjectName?: string;
  }[];
  const homework = homeworkQ.data || [];
  const progress = (progressQ.data || {}) as {
    attendancePercent?: number; present?: number; absent?: number; late?: number; totalDays?: number; markedPercent?: number;
  };
  const att = Number(progress.attendancePercent || 0);
  const marked = Number(progress.markedPercent || 0);
  return (
    <>
      <div className="hero-strip card">
        <div>
          <p className="kicker">Your month</p>
          <h1>Teacher desk</h1>
          <p>{on("ATTENDANCE") || on("TEACHER_ATTENDANCE")
            ? `Attendance ${att}% · Present ${progress.present ?? 0} · Absent ${progress.absent ?? 0} · Late ${progress.late ?? 0} of ${progress.totalDays ?? 0} marked days.`
            : "Today’s lectures and homework for your classes."}</p>
        </div>
        {on("ATTENDANCE") || on("TEACHER_ATTENDANCE") ? (
          <div className="hero-wheels">
            <Donut value={att} max={100} label="Attendance %" />
            <Donut value={marked} max={100} label="Days marked" />
          </div>
        ) : null}
      </div>
      {progressQ.error && !progressQ.data ? <ErrorBox error={progressQ.error} onRetry={() => void progressQ.reload()} title="Unable to load teacher attendance" /> : null}
      <div className="grid two">
        {on("TIMETABLE") ? (
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
        ) : null}
        {on("HOMEWORK") ? (
          <div className="card">
            <h3>Homework</h3>
            <QueryState status={homeworkQ} label="homework">
              {!homework.length ? <Empty title="No homework assigned" /> : (
                <Table headers={["Title", "Due"]} rows={homework.map((h) => [h.title, h.dueDate])} />
              )}
            </QueryState>
          </div>
        ) : null}
      </div>
    </>
  );
}

function AccountDash() {
  const { on, flags } = useFeatures();
  const d = useAsync(() => reportApi.schoolDashboard());
  const proofsQ = useAsync(() => on("FEES") ? feeApi.pendingProofs(12) : Promise.resolve([]), [flags.FEES]);
  const pending = proofsQ.data || [];
  const unpaidCount = Number(d.data?.unpaidChallans || 0);
  return (
    <>
      <div className="page-title"><div><h1>Finance desk</h1><p>Challans and payment proofs</p></div></div>
      <div className="grid stats">
        <Stat label="Students" value={d.data?.students as number} loading={d.loading} />
        <Stat label="Unpaid" value={unpaidCount} loading={d.loading} />
        <Stat label="Pending proofs" value={Number(d.data?.pendingFeeProofs || 0)} loading={d.loading} />
      </div>
      {d.error && !d.data ? <ErrorBox error={d.error} onRetry={() => void d.reload()} title="Unable to load finance desk" /> : null}
      {on("FEES") ? (
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Proofs to verify</h3>
        <QueryState status={proofsQ} label="pending proofs">
          {!pending.length ? <Empty title="Queue is clear" /> : (
            <Table headers={["Proof", "Ref", "Status"]} rows={pending.map((p) => [p.id.slice(0, 8), p.transactionRef || "—", <Badge key={p.id} value={p.status} />])} />
          )}
        </QueryState>
      </div>
      ) : null}
    </>
  );
}

function ParentDash() {
  return (
    <>
      <UpcomingResultCard />
      <ParentDashBody />
    </>
  );
}

function ParentDashBody() {
  const { on, flags } = useFeatures();
  const { childId } = useSession();
  const kidsQ = useAsync(() => studentApi.children());
  const feesQ = useAsync(() => on("FEES") ? feeApi.parentChallanSummaries(20) : Promise.resolve([]), [flags.FEES]);
  const progressQ = useAsync(() => reportApi.childrenProgress());
  const kids = kidsQ.data || [];
  const all = feesQ.data || [];
  const fees = childId ? all.filter((f) => f.studentId === childId) : all;
  const progressMap = progressQ.data || {};
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
          <p>
            {on("ATTENDANCE") ? `Attendance ${att}% (${Number(progress.present || 0)} present, ${Number(progress.absent || 0)} absent, ${Number(progress.late || 0)} late). ` : ""}
            {on("RESULTS") ? `Academic ${aca}% · Grade ${String(progress.grade || "—")} · ${pretty(String(progress.passStatus || ""))}.` : "Family overview for the selected child."}
          </p>
        </div>
        <div className="hero-wheels">
          {on("ATTENDANCE") ? <Donut value={att} max={100} label="Attendance %" /> : null}
          {on("RESULTS") ? <Donut value={aca} max={100} label="Academics %" /> : null}
        </div>
      </div>
      {progressQ.error && !progressQ.data ? <ErrorBox error={progressQ.error} onRetry={() => void progressQ.reload()} title="Unable to load children progress" /> : null}
      <QueryState status={kidsQ} label="children">
        <ChildSwitch childrenList={kids} />
      </QueryState>
      {on("FEES") ? (
      <div className="card">
        <h3>Fee challans</h3>
        <QueryState status={feesQ} label="family fees">
          {!fees.length ? <Empty title="No challans" /> : (
            <Table headers={["Challan", "Amount", "Status"]} rows={fees.map((f) => [f.challanNumber, money(f.totalPayable), pretty(f.status)])} />
          )}
        </QueryState>
      </div>
      ) : null}
    </>
  );
}

function StudentDash() {
  return (
    <>
      <UpcomingResultCard />
      <StudentDashBody />
    </>
  );
}

function StudentDashBody() {
  const { on, flags } = useFeatures();
  const { user } = useSession();
  const homeworkQ = useAsync(() => on("HOMEWORK") ? homeworkApi.list(undefined, undefined, 8) : Promise.resolve([]), [flags.HOMEWORK]);
  const feesQ = useAsync(() => on("FEES") ? feeApi.myChallanSummaries(20) : Promise.resolve([]), [flags.FEES]);
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
          <h1>{user?.fullName || "My school"}</h1>
          <p>
            {on("ATTENDANCE") ? `Attendance ${att}% (${progress.present ?? 0} present, ${progress.absent ?? 0} absent, ${progress.late ?? 0} late of ${progress.totalLectures ?? 0} lectures). ` : ""}
            {on("RESULTS") ? `Academic ${aca}% · Grade ${progress.grade || "—"} · ${pretty(progress.passStatus)} · ${progress.obtainedMarks ?? 0}/${progress.totalMarks ?? 0}.` : ""}
          </p>
        </div>
        <div className="hero-wheels">
          {on("ATTENDANCE") ? <Donut value={att} max={100} label="Attendance %" /> : null}
          {on("RESULTS") ? <Donut value={aca} max={100} label="Academics %" /> : null}
        </div>
      </div>
      {progressQ.error && !progressQ.data ? <ErrorBox error={progressQ.error} onRetry={() => void progressQ.reload()} title="Unable to load attendance and results" /> : null}
      <div className="grid two">
        {on("HOMEWORK") ? (
        <div className="card">
          <h3>Homework</h3>
          <QueryState status={homeworkQ} label="homework">
            {!hw.length ? <Empty title="Nothing assigned" /> : <Table headers={["Title", "Due"]} rows={hw.map((h) => [h.title, h.dueDate])} />}
          </QueryState>
        </div>
        ) : null}
        {on("FEES") ? (
        <div className="card">
          <h3>Fees</h3>
          <QueryState status={feesQ} label="fees">
            {!fees.length ? <Empty title="No challans" /> : <Table headers={["Challan", "Amount", "Status"]} rows={fees.map((f) => [f.challanNumber, money(f.totalPayable), pretty(f.status)])} />}
          </QueryState>
        </div>
        ) : null}
      </div>
    </>
  );
}
