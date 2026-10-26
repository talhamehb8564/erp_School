import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { attendanceApi } from "../api/services";
import { Badge, Btn, Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { today } from "../lib/format";
import type { AttendanceStatus } from "../lib/types";

export default function TeacherAttendance() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const day = useAsync(() => attendanceApi.teacherDay(today()));
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => void day.reload()} refreshing={day.loading}>
      <Title title="Staff attendance" subtitle={today()} colors={colors} />
      <QueryState loading={day.loading} error={day.error} data={day.data} onRetry={() => void day.reload()} colors={colors} empty={<Empty title="No staff rows" colors={colors} />}>
        {(day.data || []).map((r) => (
          <Card key={String(r.teacherUserId)} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(r.fullName || r.username)}</Text>
            <Badge value={String(r.status || "UNMARKED")} colors={colors} />
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
              {(["PRESENT", "ABSENT", "LATE", "LEAVE"] as AttendanceStatus[]).map((status) => (
                <Btn
                  key={status}
                  label={status}
                  kind={String(r.status) === status ? "solid" : "ghost"}
                  colors={colors}
                  onPress={async () => {
                    await attendanceApi.markTeacher({ teacherUserId: r.teacherUserId, date: today(), status });
                    await day.reload();
                  }}
                />
              ))}
            </View>
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
