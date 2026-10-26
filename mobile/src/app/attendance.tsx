import { useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, attendanceApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { dayName, fmtTime, today } from "../lib/format";
import type { AttendanceStatus } from "../lib/types";

export default function Attendance() {
  const { user, childId } = useSession();
  const { colors } = useAppearance();
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
    <Screen colors={colors} onRefresh={() => void history.reload()} refreshing={history.loading}>
      <Title title="Attendance" subtitle="Last 30 days from lecture rolls" colors={colors} />
      <QueryState loading={history.loading} error={history.error} data={history.data} onRetry={() => void history.reload()} colors={colors} empty={<Empty title="No marks" colors={colors} />}>
        {(history.data || []).map((r) => (
          <Card key={r.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "700" }}>{r.attendanceDate}</Text>
            <Badge value={r.status} colors={colors} />
            {r.remarks ? <Text style={{ color: colors.muted }}>{r.remarks}</Text> : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}

function StaffAtt() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const teacher = user?.role === "TEACHER";
  const slots = useAsync(() => (teacher ? academicApi.teacherTimetable() : academicApi.teacherTimetable().catch(() => [])), [teacher]);
  const [slotId, setSlotId] = useState("");
  const [date, setDate] = useState(today());
  const roster = useAsync(() => (slotId ? attendanceApi.roster(slotId) : Promise.resolve([])), [slotId], Boolean(slotId));
  const [marks, setMarks] = useState<Record<string, AttendanceStatus>>({});
  return (
    <Screen colors={colors}>
      <Title title="Lecture attendance" subtitle="Mark the roster for a timetable slot" colors={colors} />
      {(slots.data || []).slice(0, 12).map((s) => (
        <Text key={s.id} onPress={() => setSlotId(s.id)} style={{ color: slotId === s.id ? colors.brand : colors.ink, marginBottom: 8, fontWeight: slotId === s.id ? "800" : "500" }}>
          {s.className} · {s.subjectName} · {dayName(s.dayOfWeek)} {fmtTime(s.startTime)}
        </Text>
      ))}
      <QueryState loading={roster.loading} error={roster.error} data={roster.data} onRetry={() => void roster.reload()} colors={colors} empty={slotId ? <Empty title="Empty roster" colors={colors} /> : <Empty title="Pick a slot" colors={colors} />}>
        {(roster.data || []).map((st) => {
          const status = marks[st.id] || "PRESENT";
          return (
            <Card key={st.id} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "700" }}>{st.fullName || st.rollNumber}</Text>
              <View style={{ flexDirection: "row", gap: 8, marginTop: 8 }}>
                {(["PRESENT", "ABSENT", "LATE", "LEAVE"] as AttendanceStatus[]).map((s) => (
                  <Text key={s} onPress={() => setMarks((m) => ({ ...m, [st.id]: s }))} style={{ color: status === s ? colors.brand : colors.muted, fontWeight: "700" }}>
                    {s}
                  </Text>
                ))}
              </View>
            </Card>
          );
        })}
      </QueryState>
      {slotId ? (
        <Btn
          label={`Save marks for ${date}`}
          colors={colors}
          onPress={async () => {
            const body = (roster.data || []).map((st) => ({ studentId: st.id, status: marks[st.id] || "PRESENT" }));
            await attendanceApi.markLecture(slotId, { date, marks: body });
            await roster.reload();
          }}
        />
      ) : null}
    </Screen>
  );
}
