import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { campusApi } from "../api/services";
import { Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";

export default function Campuses() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => campusApi.list());
  if (user?.role !== "SCHOOL_ADMIN") return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Campuses" subtitle="Branches. Logo and colours stay global." colors={colors} />
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No campuses" colors={colors} />}>
        {(list.data || []).map((c) => (
          <Card key={c.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{c.name}</Text>
            <Text style={{ color: colors.muted }}>{c.code} · {c.city || c.address || "—"} · {c.phone || ""}</Text>
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
