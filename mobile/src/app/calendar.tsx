import { Text } from "react-native";
import { useAppearance } from "../lib/appearance";
import { calendarApi } from "../api/services";
import { Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { fmtDate } from "../lib/format";

export default function Calendar() {
  const { colors } = useAppearance();
  const list = useAsync(() => calendarApi.list());
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Calendar" subtitle="School events" colors={colors} />
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No events" colors={colors} />}>
        {(list.data || []).map((e) => (
          <Card key={String(e.id)} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(e.title)}</Text>
            <Text style={{ color: colors.muted }}>{fmtDate(String(e.startDate || e.eventDate || ""))}{e.endDate ? ` – ${fmtDate(String(e.endDate))}` : ""}</Text>
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
