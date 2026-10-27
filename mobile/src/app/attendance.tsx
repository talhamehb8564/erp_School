import { useEffect, useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, attendanceApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Chip, Empty, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { DateField } from "../ui/dates";
import { dayName, fmtTime, isoWeekday, today } from "../lib/format";
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
    const fromStr = `${from.getFullYear()}-${String(from.getMonth() + 1).padStart(2, "0")}-${String(from.getDate()).padStart(2, "0")}`;
    if (user?.role === "STUDENT") return attendanceApi.mine(fromStr, today());
    return attendanceApi.studentHistory(sid!, fromStr, today());
  }, [user?.role, sid], user?.role === "STUDENT" || Boolean(sid));
  return (
    <RequireAuth roles={["STUDENT", "PARENT"]}>
      <Screen colors={colors} onRefresh={() => void history.reload()} refreshing={history.loading}>
        <Title title="Attendance" subtitle="Last 30 days from lecture rolls" colors={colors} />
        {user?.role === "PARENT" && !sid ? (
          <Empty title="Select a child" hint="Choose a linked child on the Me tab to view attendance." colors={colors} />
        ) : (
          <QueryState
            loading={history.loading}
            error={history.error}
            data={history.data}
            onRetry={() => void history.reload()}
            colors={colors}
            empty={<Empty title="No attendance records found" hint="Attendance appears after a teacher saves a lecture roll." colors={colors} />}
          >
            {(history.data || []).map((r) => (
              <Card key={r.id} colors={colors}>
                <Text style={{ color: colors.ink, fontWeight: "700" }}>{r.attendanceDate}</Text>
                <Badge value={r.status} colors={colors} />
                {r.remarks ? <Text style={{ color: colors.muted }}>{r.remarks}</Text> : null}
              </Card>
            ))}
          </QueryState>
        )}
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
  const sections = useAsync(() => academicApi.sections(classId), [classId], Boolean(classId) && !teacher);
  const teacherSlots = useAsync(() => academicApi.teacherTimetable(), [], teacher);
  const classSlots = useAsync(
    () => academicApi.timetable(classId, sectionId),
    [classId, sectionId],
    Boolean(classId && sectionId) && !teacher,
  );
  const slots = teacher ? teacherSlots.data || [] : classSlots.data || [];
  const slotsLoading = teacher ? teacherSlots.loading : classSlots.loading;
  const slotsError = teacher ? teacherSlots.error : classSlots.error;
  const [slotId, setSlotId] = useState("");
  const [date, setDate] = useState(today());
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const weekday = isoWeekday(date);
  const daySlots = slots.filter((s) => !s.dayOfWeek || s.dayOfWeek === weekday);
  const roster = useAsync(() => attendanceApi.roster(slotId), [slotId], Boolean(slotId));
  const sheet = useAsync(() => attendanceApi.lectureSheet(slotId, date), [slotId, date], Boolean(slotId));
  const [marks, setMarks] = useState<Record<string, AttendanceStatus>>({});

  useEffect(() => {
    const next: Record<string, AttendanceStatus> = {};
    for (const row of sheet.data || []) {
      if (row.studentId && row.status) next[row.studentId] = row.status;
    }
    setMarks(next);
  }, [sheet.data, slotId, date]);

  const selectionMissing = !teacher && !(classId && sectionId);
  const canRefresh = () => {
    if (teacher) void teacherSlots.reload();
    else if (classId && sectionId) void classSlots.reload();
    if (slotId) {
      void roster.reload();
      void sheet.reload();
    }
  };

  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER"]}>
      <Screen colors={colors} onRefresh={canRefresh} refreshing={slotsLoading || roster.loading}>
        <Title
          title="Lecture attendance"
          subtitle={teacher ? "Your assigned lectures only. Pick a date and slot, then save." : "Select class, section, date and lecture. Records persist on the server."}
          colors={colors}
        />
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
        <DateField label="Lecture date" value={date} onChange={(next) => { setDate(next); setSlotId(""); }} colors={colors} />
        {selectionMissing ? (
          <Empty title="Select class and section to view attendance" hint="Attendance is loaded only after class and section are chosen." colors={colors} />
        ) : (
          <QueryState
            loading={slotsLoading}
            error={slotsError}
            data={daySlots}
            onRetry={() => (teacher ? void teacherSlots.reload() : void classSlots.reload())}
            colors={colors}
            empty={<Empty title="No timetable has been configured" hint={`${dayName(weekday)} has no lectures for this class. Add slots on Timetable, or pick another date.`} colors={colors} />}
          >
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>{dayName(weekday)} lectures</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
              {daySlots.map((s) => (
                <Chip
                  key={s.id}
                  label={`${s.className || ""} · ${s.subjectName || "Lesson"} · ${fmtTime(s.startTime)}`}
                  selected={slotId === s.id}
                  onPress={() => setSlotId(s.id)}
                  colors={colors}
                />
              ))}
            </View>
          </QueryState>
        )}
        {!slotId && !selectionMissing && daySlots.length ? (
          <Empty title="Pick a lecture slot" hint="Then mark each student. Previous saves for this date are loaded from the server." colors={colors} />
        ) : null}
        {slotId ? (
          <QueryState
            loading={roster.loading}
            error={roster.error}
            data={roster.data}
            onRetry={() => void roster.reload()}
            colors={colors}
            empty={<Empty title="No students found" hint="This lecture’s class/section has no active students." colors={colors} />}
          >
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
        ) : null}
        {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
        {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
        {slotId ? (
          <Btn
            label={`Save marks for ${date}`}
            colors={colors}
            disabled={roster.loading || !(roster.data || []).length}
            onPress={async () => {
              setErr("");
              setMsg("");
              try {
                const body = (roster.data || []).map((st) => ({ studentId: st.id, status: marks[st.id] || "PRESENT" }));
                await attendanceApi.markLecture(slotId, { date, marks: body });
                setMsg("Attendance saved.");
                await sheet.reload();
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
