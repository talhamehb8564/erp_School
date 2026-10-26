import { useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, attendanceApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Chip, Empty, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { DateField } from "../ui/dates";
import { dayName, fmtTime, today } from "../lib/format";
import type { AttendanceStatus } from "../lib/types";
import { RequireAuth } from "../ui/gate";

export default function Attendance() {
  const { user } = useSession();
  if (user?.role === "STUDENT" || user?.role === "PARENT") return <FamilyAtt />;
  return <StaffAtt />;
}

function FamilyAtt() {
  const { user, childId } = useSession();
  const { colors } = useAppearance();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const sid = childId || kids.data?.[0]?.id;
  const history = useAsync(() => {
    const from = new Date();
    from.setDate(from.getDate() - 30);
    const fromStr = from.toISOString().slice(0, 10);
    if (user?.role === "STUDENT") return attendanceApi.mine(fromStr, today());
    return attendanceApi.studentHistory(sid!, fromStr, today());
  }, [user?.role, sid], user?.role === "STUDENT" || Boolean(sid));
  return (
    <RequireAuth roles={["STUDENT", "PARENT"]}>
    <Screen colors={colors} onRefresh={() => void history.reload()} refreshing={history.loading}>
      <Title title="Attendance" subtitle="Last 30 days from lecture rolls" colors={colors} />
      <QueryState loading={history.loading} error={history.error} data={history.data} onRetry={() => void history.reload()} colors={colors} empty={<Empty title="No marks" hint="Attendance appears after teachers save a lecture roll." colors={colors} />}>
        {(history.data || []).map((r) => (
          <Card key={r.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "700" }}>{r.attendanceDate}</Text>
            <Badge value={r.status} colors={colors} />
            {r.remarks ? <Text style={{ color: colors.muted }}>{r.remarks}</Text> : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
    </RequireAuth>
  );
}

function StaffAtt() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const teacher = user?.role === "TEACHER";
  const classes = useAsync(() => academicApi.classes(), [], !teacher);
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId) && !teacher);
  const teacherSlots = useAsync(() => (teacher ? academicApi.teacherTimetable() : Promise.resolve([])), [teacher], teacher);
  const classSlots = useAsync(
    () => (classId && sectionId ? academicApi.timetable(classId, sectionId) : Promise.resolve([])),
    [classId, sectionId],
    Boolean(classId && sectionId) && !teacher,
  );
  const slots = teacher ? teacherSlots.data || [] : classSlots.data || [];
  const [slotId, setSlotId] = useState("");
  const [date, setDate] = useState(today());
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const roster = useAsync(() => (slotId ? attendanceApi.roster(slotId) : Promise.resolve([])), [slotId], Boolean(slotId));
  const [marks, setMarks] = useState<Record<string, AttendanceStatus>>({});
  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER"]}>
    <Screen colors={colors} onRefresh={() => { void teacherSlots.reload(); void classSlots.reload(); }} refreshing={teacherSlots.loading || classSlots.loading}>
      <Title title="Lecture attendance" subtitle="Pick a timetable slot, mark the roster, then save. Persists on the server." colors={colors} />
      {!teacher ? (
        <>
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Class</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
            {(classes.data || []).map((c) => (
              <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); setSlotId(""); }} colors={colors} />
            ))}
          </View>
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Section</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
            {(sections.data || []).map((s) => (
              <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => { setSectionId(s.id); setSlotId(""); }} colors={colors} />
            ))}
          </View>
        </>
      ) : null}
      <DateField label="Lecture date" value={date} onChange={setDate} colors={colors} />
      {(slots || []).map((s) => (
        <Chip
          key={s.id}
          label={`${s.className} · ${s.subjectName} · ${dayName(s.dayOfWeek)} ${fmtTime(s.startTime)}`}
          selected={slotId === s.id}
          onPress={() => setSlotId(s.id)}
          colors={colors}
        />
      ))}
      <QueryState loading={roster.loading} error={roster.error} data={roster.data} onRetry={() => void roster.reload()} colors={colors} empty={slotId ? <Empty title="Empty roster" colors={colors} /> : <Empty title="Pick a lecture slot" colors={colors} />}>
        {(roster.data || []).map((st) => {
          const status = marks[st.id] || "PRESENT";
          return (
            <Card key={st.id} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "700" }}>{st.fullName || st.rollNumber}</Text>
              <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
                {(["PRESENT", "ABSENT", "LATE", "LEAVE"] as AttendanceStatus[]).map((s) => (
                  <Chip key={s} label={s} selected={status === s} onPress={() => setMarks((m) => ({ ...m, [st.id]: s }))} colors={colors} />
                ))}
              </View>
            </Card>
          );
        })}
      </QueryState>
      {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
      {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
      {slotId ? (
        <Btn
          label={`Save marks for ${date}`}
          colors={colors}
          onPress={async () => {
            setErr("");
            setMsg("");
            try {
              const body = (roster.data || []).map((st) => ({ studentId: st.id, status: marks[st.id] || "PRESENT" }));
              await attendanceApi.markLecture(slotId, { date, marks: body });
              setMsg("Attendance saved.");
              await roster.reload();
            } catch (e) {
              setErr(friendlyMessage(e));
            }
          }}
        />
      ) : null}
    </Screen>
    </RequireAuth>
  );
}
