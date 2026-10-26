import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, studentApi } from "../api/services";
import { Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { dayName, fmtTime } from "../lib/format";

export default function Timetable() {
  const { user, childId } = useSession();
  const { colors } = useAppearance();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const child = (kids.data || []).find((k) => k.id === (childId || kids.data?.[0]?.id));
  const slots = useAsync(() => {
    if (user?.role === "TEACHER") return academicApi.teacherTimetable();
    if (user?.role === "STUDENT") return academicApi.myTimetable();
    if (user?.role === "PARENT" && child?.classId && child?.sectionId) {
      return academicApi.timetable(child.classId, child.sectionId);
    }
    if (user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL") return academicApi.teacherTimetable().catch(() => []);
    return Promise.resolve([]);
  }, [user?.role, child?.classId, child?.sectionId]);
  const grouped = new Map<number, typeof slots.data>();
  for (const s of slots.data || []) {
    const day = s.dayOfWeek || 0;
    const rows = grouped.get(day) || [];
    rows.push(s);
    grouped.set(day, rows);
  }
  const days = [...grouped.keys()].sort((a, b) => a - b);
  return (
    <Screen colors={colors} onRefresh={() => void slots.reload()} refreshing={slots.loading}>
      <Title title="Timetable" subtitle="Lecture slots from the academic calendar." colors={colors} />
      <QueryState loading={slots.loading} error={slots.error} data={slots.data} onRetry={() => void slots.reload()} colors={colors} empty={<Empty title="No slots" hint="Ask admin to publish a timetable for this class." colors={colors} />}>
        {days.map((d) => (
          <View key={d} style={{ marginBottom: 8 }}>
            <Text style={{ color: colors.brand, fontWeight: "800", marginBottom: 6 }}>{dayName(d)}</Text>
            {(grouped.get(d) || []).map((s) => (
              <Card key={s.id} colors={colors}>
                <Text style={{ color: colors.ink, fontWeight: "800" }}>{s.subjectName || "Lesson"}</Text>
                <Text style={{ color: colors.muted }}>
                  {fmtTime(s.startTime)}–{fmtTime(s.endTime)} · {s.className} {s.sectionName || ""} {s.teacherName ? `· ${s.teacherName}` : ""}
                </Text>
              </Card>
            ))}
          </View>
        ))}
      </QueryState>
    </Screen>
  );
}
