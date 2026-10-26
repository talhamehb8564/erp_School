import { Pressable, Text, View } from "react-native";
import { useSession } from "../../lib/session";
import { useAppearance } from "../../lib/appearance";
import { useFeatures } from "../../lib/features";
import { Card, QueryState, Screen, Stat, Title, useAsync } from "../../ui/kit";
import { academicApi, examApi, feeApi, homeworkApi, reportApi, studentApi, tenantApi } from "../../api/services";
import { money, num, pretty } from "../../lib/format";
import { ROLE_LABEL } from "../../lib/types";
import ResultCountdown, { isUpcomingExam, pickResultSession } from "../../ui/countdown";

export default function Home() {
  const { user, locked } = useSession();
  const { colors } = useAppearance();
  if (!user) return null;
  if (locked) {
    return (
      <Screen colors={colors}>
        <Title kicker="Subscription" title="ERP locked" subtitle="Complete payment proof from the web admin billing page, or open Subscription here if you are the school admin." colors={colors} />
      </Screen>
    );
  }
  if (user.role === "ERP_OWNER") return <OwnerHome />;
  if (user.role === "TEACHER") return <TeacherHome />;
  if (user.role === "ACCOUNT_OFFICER") return <AccountHome />;
  if (user.role === "PARENT") return <ParentHome />;
  if (user.role === "STUDENT") return <StudentHome />;
  return <StaffHome />;
}

function Upcoming() {
  const { on } = useFeatures();
  const { colors } = useAppearance();
  const sessions = useAsync(() => (on("RESULTS") ? examApi.sessions() : Promise.resolve([])), [], on("RESULTS"));
  const rows = sessions.data || [];
  const id = pickResultSession(rows);
  const selected = rows.find((s) => s.id === id);
  if (!on("RESULTS") || !selected || !isUpcomingExam(selected.announceAt)) return null;
  return <ResultCountdown announceAt={selected.announceAt} sessionName={selected.name} colors={colors} />;
}

function StaffHome() {
  const { user, tenant } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const d = useAsync(() => reportApi.schoolDashboard());
  return (
    <Screen colors={colors} onRefresh={() => void d.reload()} refreshing={d.loading}>
      <Title kicker={ROLE_LABEL[user!.role]} title={tenant?.name || "School dashboard"} subtitle="Live census from PostgreSQL." colors={colors} />
      <QueryState loading={d.loading} error={d.error} data={d.data} onRetry={() => void d.reload()} colors={colors}>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10, marginBottom: 12 }}>
          <Stat label="Students" value={num(d.data?.students)} colors={colors} index={0} />
          <Stat label="Active" value={num(d.data?.activeStudents)} colors={colors} index={1} />
          <Stat label="Teachers" value={num(d.data?.teachers)} colors={colors} index={2} />
          {on("FEES") ? <Stat label="Unpaid" value={num(d.data?.unpaidChallans)} colors={colors} index={3} /> : null}
        </View>
      </QueryState>
    </Screen>
  );
}

function TeacherHome() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const lectures = useAsync(() => (on("TIMETABLE") ? academicApi.teacherToday() : Promise.resolve([])), []);
  const progress = useAsync(() => reportApi.teacherProgress());
  const hw = useAsync(() => (on("HOMEWORK") ? homeworkApi.list(undefined, undefined, 8) : Promise.resolve([])), [], on("HOMEWORK"));
  return (
    <Screen colors={colors} onRefresh={() => { void lectures.reload(); void progress.reload(); void hw.reload(); }} refreshing={lectures.loading}>
      <Upcoming />
      <Title kicker="Teacher" title={user?.fullName || "Teacher desk"} subtitle={`Attendance ${num(progress.data?.attendancePercent)}% this month.`} colors={colors} />
      <View style={{ flexDirection: "row", gap: 10, marginBottom: 12 }}>
        <Stat label="Attendance %" value={num(progress.data?.attendancePercent)} colors={colors} />
        <Stat label="Days marked" value={num(progress.data?.totalDays)} colors={colors} index={1} />
      </View>
      <Card colors={colors}>
        <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Today’s lectures</Text>
        <QueryState loading={lectures.loading} error={lectures.error} data={lectures.data} onRetry={() => void lectures.reload()} colors={colors} empty={<Text style={{ color: colors.muted }}>No lectures today</Text>}>
          {(lectures.data || []).map((l) => (
            <Text key={l.id} style={{ color: colors.ink, marginBottom: 6 }}>
              {l.className} · {l.subjectName}
            </Text>
          ))}
        </QueryState>
      </Card>
      {on("HOMEWORK") ? (
        <Card colors={colors}>
          <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Homework</Text>
          {(hw.data || []).slice(0, 5).map((h) => (
            <Text key={h.id} style={{ color: colors.ink, marginBottom: 6 }}>{h.title}</Text>
          ))}
        </Card>
      ) : null}
    </Screen>
  );
}

function AccountHome() {
  const { colors } = useAppearance();
  const d = useAsync(() => reportApi.schoolDashboard());
  const proofs = useAsync(() => feeApi.pendingProofs(12));
  return (
    <Screen colors={colors} onRefresh={() => { void d.reload(); void proofs.reload(); }} refreshing={d.loading}>
      <Title kicker="Finance" title="Account desk" subtitle="Challans and payment proofs." colors={colors} />
      <View style={{ flexDirection: "row", gap: 10, marginBottom: 12 }}>
        <Stat label="Students" value={num(d.data?.students)} colors={colors} />
        <Stat label="Unpaid" value={num(d.data?.unpaidChallans)} colors={colors} index={1} />
        <Stat label="Proofs" value={num(d.data?.pendingFeeProofs)} colors={colors} index={2} />
      </View>
      <Card colors={colors}>
        <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Proofs to verify</Text>
        <QueryState loading={proofs.loading} error={proofs.error} data={proofs.data} onRetry={() => void proofs.reload()} colors={colors} empty={<Text style={{ color: colors.muted }}>Queue is clear</Text>}>
          {(proofs.data || []).slice(0, 8).map((p) => (
            <Text key={String(p.id)} style={{ color: colors.ink, marginBottom: 6 }}>
              {String(p.transactionRef || p.id).slice(0, 12)} · {pretty(String(p.status || ""))}
            </Text>
          ))}
        </QueryState>
      </Card>
    </Screen>
  );
}

function ParentHome() {
  const { childId, setChildId } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const kids = useAsync(() => studentApi.children());
  const progress = useAsync(() => reportApi.childrenProgress());
  const fees = useAsync(() => (on("FEES") ? feeApi.parentChallanSummaries(20) : Promise.resolve([])), [], on("FEES"));
  const list = kids.data || [];
  const active = childId || list[0]?.id;
  const p = (active && progress.data?.[active]) || {};
  return (
    <Screen colors={colors} onRefresh={() => { void kids.reload(); void progress.reload(); void fees.reload(); }} refreshing={kids.loading}>
      <Upcoming />
      <Title kicker="Family" title="Selected child" subtitle={`Attendance ${num(p.attendancePercent)}% · Academic ${num(p.academicPercent)}%`} colors={colors} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
        {list.map((k) => {
          const onChip = k.id === active;
          return (
            <Pressable
              key={k.id}
              onPress={() => setChildId(k.id)}
              accessibilityRole="button"
              accessibilityState={{ selected: onChip }}
              style={{
                paddingHorizontal: 12,
                paddingVertical: 8,
                borderRadius: 999,
                backgroundColor: onChip ? colors.brand : colors.card,
                borderWidth: 1,
                borderColor: onChip ? colors.brand : colors.line,
              }}
            >
              <Text style={{ color: onChip ? "#fff" : colors.ink, fontWeight: "700", fontSize: 13 }}>
                {k.fullName || k.rollNumber}
              </Text>
            </Pressable>
          );
        })}
      </View>
      {on("FEES") ? (
        <Card colors={colors}>
          <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Fee challans</Text>
          {(fees.data || []).filter((f) => !active || f.studentId === active).slice(0, 6).map((f) => (
            <Text key={f.id} style={{ color: colors.ink, marginBottom: 6 }}>
              {f.challanNumber} · {money(f.totalPayable)} · {pretty(f.status)}
            </Text>
          ))}
        </Card>
      ) : null}
    </Screen>
  );
}

function StudentHome() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const progress = useAsync(() => reportApi.myProgress());
  const hw = useAsync(() => (on("HOMEWORK") ? homeworkApi.list(undefined, undefined, 8) : Promise.resolve([])), [], on("HOMEWORK"));
  const fees = useAsync(() => (on("FEES") ? feeApi.myChallanSummaries(20) : Promise.resolve([])), [], on("FEES"));
  const p = progress.data || {};
  return (
    <Screen colors={colors} onRefresh={() => { void progress.reload(); void hw.reload(); void fees.reload(); }} refreshing={progress.loading}>
      <Upcoming />
      <Title kicker="This month" title={user?.fullName || "My school"} subtitle={`Attendance ${num(p.attendancePercent)}% · Academic ${num(p.academicPercent)}%`} colors={colors} />
      <View style={{ flexDirection: "row", gap: 10, marginBottom: 12 }}>
        {on("ATTENDANCE") ? <Stat label="Attendance %" value={num(p.attendancePercent)} colors={colors} /> : null}
        {on("RESULTS") ? <Stat label="Academics %" value={num(p.academicPercent)} colors={colors} index={1} /> : null}
      </View>
      {on("HOMEWORK") ? (
        <Card colors={colors}>
          <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Homework</Text>
          <QueryState loading={hw.loading} error={hw.error} data={hw.data} onRetry={() => void hw.reload()} colors={colors} empty={<Text style={{ color: colors.muted }}>Nothing assigned</Text>}>
            {(hw.data || []).map((h) => (
              <Text key={h.id} style={{ color: colors.ink, marginBottom: 6 }}>{h.title} · {h.dueDate}</Text>
            ))}
          </QueryState>
        </Card>
      ) : null}
      {on("FEES") ? (
        <Card colors={colors}>
          <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Fees</Text>
          {(fees.data || []).map((f) => (
            <Text key={f.id} style={{ color: colors.ink, marginBottom: 6 }}>
              {f.challanNumber} · {money(f.totalPayable)} · {pretty(f.status)}
            </Text>
          ))}
        </Card>
      ) : null}
    </Screen>
  );
}

function OwnerHome() {
  const { colors } = useAppearance();
  const dash = useAsync(() => reportApi.platformDashboard());
  const schools = useAsync(() => tenantApi.list());
  return (
    <Screen colors={colors} onRefresh={() => void dash.reload()} refreshing={dash.loading}>
      <Title kicker="Platform" title="ERP Owner" subtitle="Schools and subscriptions on this SaaS." colors={colors} />
      <View style={{ flexDirection: "row", gap: 10, marginBottom: 12 }}>
        <Stat label="Schools" value={num(dash.data?.schools)} colors={colors} />
        <Stat label="Users" value={num(dash.data?.users)} colors={colors} index={1} />
        <Stat label="Listed" value={schools.data?.totalElements} colors={colors} index={2} />
      </View>
    </Screen>
  );
}
