import { Pressable, Text } from "react-native";
import { router } from "expo-router";
import { useAppearance } from "../lib/appearance";
import { tenantApi } from "../api/services";
import { Badge, Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { RequireAuth } from "../ui/gate";

export default function Schools() {
  const { colors } = useAppearance();
  const list = useAsync(() => tenantApi.list());
  return (
    <RequireAuth roles={["ERP_OWNER"]}>
      <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
        <Title title="Schools" subtitle="Tenants on this platform" colors={colors} />
        <QueryState loading={list.loading} error={list.error} data={list.data?.content} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No schools" colors={colors} />}>
          {(list.data?.content || []).map((t) => (
            <Pressable key={t.id} onPress={() => router.push(`/school/${t.id}` as never)}>
              <Card colors={colors}>
                <Text style={{ color: colors.ink, fontWeight: "800" }}>{t.name}</Text>
                <Text style={{ color: colors.muted }}>{t.code} · {t.city || ""}</Text>
                <Badge value={t.status} colors={colors} />
              </Card>
            </Pressable>
          ))}
        </QueryState>
      </Screen>
    </RequireAuth>
  );
}
