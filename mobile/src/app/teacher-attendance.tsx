import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { attendanceApi } from "../api/services";
import { Badge, Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { today } from "../lib/format";

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
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
