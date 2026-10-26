import { Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { useSession } from "../../lib/session";
import { useAppearance } from "../../lib/appearance";
import { useFeatures } from "../../lib/features";
import { Btn, Card, Chip, QueryState, Screen, Stat, useAsync } from "../../ui/kit";
import { BrandHeader } from "../../ui/logo";
import { ProgressWheel } from "../../ui/wheel";
import { academicApi, announcementApi, examApi, feeApi, homeworkApi, notificationApi, reportApi, studentApi, tenantApi } from "../../api/services";
import { brandSubtitle, money, num, pretty, roleLabel } from "../../lib/format";
import ResultCountdown, { isUpcomingExam, pickResultSession } from "../../ui/countdown";

export default function Home() {
  const { user, locked, brand } = useSession();
  const { colors } = useAppearance();
  if (!user) return null;
  if (locked) {
    return (
      <Screen colors={colors}>
        <BrandHeader brand={brand} colors={colors} kicker="Subscription" subtitle="This school ERP is locked until billing is current." />
        <Text style={{ color: colors.ink, fontSize: 22, fontWeight: "800", marginBottom: 8 }}>ERP locked</Text>
        <Text style={{ color: colors.muted, marginBottom: 16 }}>
          Complete payment proof from Subscription if you are the school admin.
        </Text>
        {user.role === "SCHOOL_ADMIN" ? <Btn label="Open subscription" colors={colors} onPress={() => router.push("/billing")} /> : null}
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

function QuickActions({ items }: { items: { label: string; href: string }[] }) {
  const { colors } = useAppearance();
  return (
    <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
      {items.map((it) => (
        <Btn key={it.href} label={it.label} kind="ghost" colors={colors} onPress={() => router.push(it.href as never)} />
      ))}
    </View>
  );
}

function Notices() {
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const list = useAsync(() => (on("ANNOUNCEMENTS") ? announcementApi.list() : Promise.resolve([])), [], on("ANNOUNCEMENTS"));
  const unread = useAsync(() => notificationApi.unread());
  if (!on("ANNOUNCEMENTS") && !unread.data) return null;
  return (
    <Card colors={colors}>
      <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>
        Notices {unread.data?.unread ? `· ${unread.data.unread} unread` : ""}
      </Text>
      {(list.data || []).slice(0, 3).map((a) => (
        <Pressable key={a.id} onPress={() => router.push("/announcements")}>
          <Text style={{ color: colors.ink, marginBottom: 6 }}>{a.title}</Text>
        </Pressable>
      ))}
      {!(list.data || []).length ? <Text style={{ color: colors.muted }}>No announcements yet</Text> : null}
    </Card>
  );
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
  const { user, brand } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const d = useAsync(() => reportApi.schoolDashboard());
  const students = num(d.data?.students);
  const active = num(d.data?.activeStudents);
  const activePct = students > 0 ? Math.round((active * 10000) / students) / 100 : null;
  return (
    <Screen colors={colors} onRefresh={() => void d.reload()} refreshing={d.loading}>
      <BrandHeader brand={brand} colors={colors} kicker={roleLabel(user!.role)} subtitle={brand ? brandSubtitle(brand, "Live census") : "Live census"} />
      <QueryState loading={d.loading} error={d.error} data={d.data} onRetry={() => void d.reload()} colors={colors}>
        <View style={{ flexDirection: "row", flexWrap: "wrap", justifyContent: "space-around", marginBottom: 8 }}>
          <ProgressWheel
            value={activePct}
            label="Active students"
            hint={students ? `${active} of ${students} on roll` : "No students yet"}
            empty={students === 0}
            loading={d.loading}
            colors={colors}
          />
        </View>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10, marginBottom: 12 }}>
          <Stat label="Students" value={students} colors={colors} index={0} />
          <Stat label="Teachers" value={num(d.data?.teachers)} colors={colors} index={1} />
          {on("FEES") ? <Stat label="Unpaid" value={num(d.data?.unpaidChallans)} colors={colors} index={2} /> : null}
        </View>
      </QueryState>
      <QuickActions
        items={[
          { label: "Users", href: "/users" },
          { label: "Students", href: "/students" },
          { label: "Admissions", href: "/admissions" },
          { label: "Settings", href: "/settings" },
        ]}
      />
      <Notices />
    </Screen>
  );
}

function TeacherHome() {
  const { user, brand } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const lectures = useAsync(() => (on("TIMETABLE") ? academicApi.teacherToday() : Promise.resolve([])), []);
  const progress = useAsync(() => reportApi.teacherProgress());
  const hw = useAsync(() => (on("HOMEWORK") ? homeworkApi.list(undefined, undefined, 8) : Promise.resolve([])), [], on("HOMEWORK"));
  const att = num(progress.data?.attendancePercent);
  const days = num(progress.data?.totalDays);
  return (
    <Screen colors={colors} onRefresh={() => { void lectures.reload(); void progress.reload(); void hw.reload(); }} refreshing={lectures.loading}>
      <Upcoming />
      <BrandHeader brand={brand} colors={colors} kicker="Teacher" subtitle={user?.fullName || "Teacher desk"} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", justifyContent: "space-around", marginBottom: 8 }}>
        {on("TEACHER_ATTENDANCE") || on("ATTENDANCE") ? (
          <ProgressWheel
            value={att}
            label="Attendance"
            hint={days ? `${days} days marked this month` : "No staff marks this month"}
            empty={days === 0}
            loading={progress.loading}
            colors={colors}
          />
        ) : null}
        <ProgressWheel
          value={num(progress.data?.markedPercent)}
          label="Days marked"
          hint="Share of the last 30 days with a mark"
          loading={progress.loading}
          colors={colors}
        />
      </View>
      <Card colors={colors}>
        <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Today’s lectures</Text>
        <QueryState loading={lectures.loading} error={lectures.error} data={lectures.data} onRetry={() => void lectures.reload()} colors={colors} empty={<Text style={{ color: colors.muted }}>No lectures today</Text>}>
          {(lectures.data || []).map((l) => (
            <Pressable key={l.id} onPress={() => router.push("/attendance")} style={{ marginBottom: 8 }}>
              <Text style={{ color: colors.ink, fontWeight: "700" }}>{l.className} · {l.subjectName}</Text>
            </Pressable>
          ))}
        </QueryState>
      </Card>
      {on("HOMEWORK") ? (
        <Card colors={colors}>
          <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Homework</Text>
          {(hw.data || []).slice(0, 5).map((h) => (
            <Text key={h.id} style={{ color: colors.ink, marginBottom: 6 }}>{h.title}</Text>
          ))}
          <Btn label="Open homework" kind="ghost" colors={colors} onPress={() => router.push("/homework")} />
        </Card>
      ) : null}
      <QuickActions
        items={[
          { label: "Attendance", href: "/attendance" },
          { label: "Homework", href: "/homework" },
          { label: "Results", href: "/exams" },
          { label: "Online exams", href: "/online-exams" },
        ]}
      />
      <Notices />
    </Screen>
  );
}

function AccountHome() {
  const { brand } = useSession();
  const { colors } = useAppearance();
  const d = useAsync(() => reportApi.schoolDashboard());
  const proofs = useAsync(() => feeApi.pendingProofs(12));
  return (
    <Screen colors={colors} onRefresh={() => { void d.reload(); void proofs.reload(); }} refreshing={d.loading}>
      <BrandHeader brand={brand} colors={colors} kicker="Finance" subtitle="Challans and payment proofs" />
      <View style={{ flexDirection: "row", gap: 10, marginBottom: 12 }}>
        <Stat label="Students" value={num(d.data?.students)} colors={colors} />
        <Stat label="Unpaid" value={num(d.data?.unpaidChallans)} colors={colors} index={1} />
        <Stat label="Proofs" value={num(d.data?.pendingFeeProofs)} colors={colors} index={2} />
      </View>
      <Btn label="Create / send challans" colors={colors} onPress={() => router.push("/fees")} />
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
  const { childId, setChildId, brand } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const kids = useAsync(() => studentApi.children());
  const progress = useAsync(() => reportApi.childrenProgress());
  const fees = useAsync(() => (on("FEES") ? feeApi.parentChallanSummaries(20) : Promise.resolve([])), [], on("FEES"));
  const list = kids.data || [];
  const active = childId || list[0]?.id;
  const p = (active && progress.data?.[active]) || {};
  const lectures = num(p.totalLectures);
  const marks = num(p.totalMarks);
  return (
    <Screen colors={colors} onRefresh={() => { void kids.reload(); void progress.reload(); void fees.reload(); }} refreshing={kids.loading}>
      <Upcoming />
      <BrandHeader brand={brand} colors={colors} kicker="Family" subtitle="Select a child" />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
        {list.map((k) => (
          <Chip
            key={k.id}
            label={k.fullName || k.rollNumber || "Child"}
            selected={k.id === active}
            onPress={() => setChildId(k.id)}
            colors={colors}
          />
        ))}
      </View>
      <View style={{ flexDirection: "row", flexWrap: "wrap", justifyContent: "space-around", marginBottom: 8 }}>
        {on("ATTENDANCE") ? (
          <ProgressWheel
            value={num(p.attendancePercent)}
            label="Attendance"
            hint={lectures ? `${num(p.present)} present of ${lectures} lectures` : "No lectures marked yet"}
            empty={lectures === 0}
            loading={progress.loading}
            colors={colors}
          />
        ) : null}
        {on("RESULTS") ? (
          <ProgressWheel
            value={num(p.academicPercent)}
            label="Results"
            hint={marks ? `${pretty(String(p.grade || ""))} · ${num(p.obtainedMarks)}/${marks}` : "No published marks yet"}
            empty={marks === 0}
            loading={progress.loading}
            colors={colors}
          />
        ) : null}
      </View>
      {on("FEES") ? (
        <Card colors={colors}>
          <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Fee challans</Text>
          {(fees.data || []).filter((f) => !active || f.studentId === active).slice(0, 6).map((f) => (
            <Text key={f.id} style={{ color: colors.ink, marginBottom: 6 }}>
              {f.challanNumber} · {money(f.totalPayable)} · {pretty(f.status)}
            </Text>
          ))}
          <Btn label="Open fees" kind="ghost" colors={colors} onPress={() => router.push("/fees")} />
        </Card>
      ) : null}
      <QuickActions
        items={[
          { label: "Attendance", href: "/attendance" },
          { label: "Homework", href: "/homework" },
          { label: "Results", href: "/exams" },
          { label: "Fees", href: "/fees" },
        ]}
      />
      <Notices />
    </Screen>
  );
}

function StudentHome() {
  const { user, brand } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const progress = useAsync(() => reportApi.myProgress());
  const hw = useAsync(() => (on("HOMEWORK") ? homeworkApi.list(undefined, undefined, 8) : Promise.resolve([])), [], on("HOMEWORK"));
  const fees = useAsync(() => (on("FEES") ? feeApi.myChallanSummaries(20) : Promise.resolve([])), [], on("FEES"));
  const p = progress.data || {};
  const lectures = num(p.totalLectures);
  const marks = num(p.totalMarks);
  return (
    <Screen colors={colors} onRefresh={() => { void progress.reload(); void hw.reload(); void fees.reload(); }} refreshing={progress.loading}>
      <Upcoming />
      <BrandHeader brand={brand} colors={colors} kicker="This month" subtitle={user?.fullName || "My school"} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", justifyContent: "space-around", marginBottom: 8 }}>
        {on("ATTENDANCE") ? (
          <ProgressWheel
            value={num(p.attendancePercent)}
            label="Attendance"
            hint={lectures ? `${num(p.present)} present of ${lectures} lectures` : "No lectures marked yet"}
            empty={lectures === 0}
            loading={progress.loading}
            colors={colors}
          />
        ) : null}
        {on("RESULTS") ? (
          <ProgressWheel
            value={num(p.academicPercent)}
            label="Results"
            hint={marks ? `${pretty(String(p.grade || ""))} · ${num(p.obtainedMarks)}/${marks}` : "No published marks yet"}
            empty={marks === 0}
            loading={progress.loading}
            colors={colors}
          />
        ) : null}
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
      <QuickActions
        items={[
          { label: "Homework", href: "/homework" },
          { label: "Results", href: "/exams" },
          { label: "Online exams", href: "/online-exams" },
          { label: "Fees", href: "/fees" },
        ]}
      />
      <Notices />
    </Screen>
  );
}

function OwnerHome() {
  const { colors } = useAppearance();
  const dash = useAsync(() => reportApi.platformDashboard());
  const schools = useAsync(() => tenantApi.list());
  return (
    <Screen colors={colors} onRefresh={() => void dash.reload()} refreshing={dash.loading}>
      <BrandHeader brand={null} colors={colors} kicker="Platform" subtitle="Schools and subscriptions on this SaaS" />
      <View style={{ flexDirection: "row", gap: 10, marginBottom: 12 }}>
        <Stat label="Schools" value={num(dash.data?.schools)} colors={colors} />
        <Stat label="Users" value={num(dash.data?.users)} colors={colors} index={1} />
        <Stat label="Listed" value={schools.data?.totalElements} colors={colors} index={2} />
      </View>
      <Btn label="Manage schools" colors={colors} onPress={() => router.push("/schools")} />
    </Screen>
  );
}
