import { Text } from "react-native";
import { useLocalSearchParams } from "expo-router";
import { useAppearance } from "../../lib/appearance";
import { subscriptionApi, tenantApi } from "../../api/services";
import { Badge, Btn, Card, Screen, Title, useAsync } from "../../ui/kit";
import { RequireAuth } from "../../ui/gate";

export default function SchoolDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useAppearance();
  const school = useAsync(() => tenantApi.get(id!), [id], Boolean(id));
  const sub = useAsync(() => subscriptionApi.current(id), [id], Boolean(id));
  const t = school.data;
  return (
    <RequireAuth roles={["ERP_OWNER"]}>
      <Screen colors={colors} onRefresh={() => { void school.reload(); void sub.reload(); }} refreshing={school.loading}>
        <Title title={t?.name || "School"} subtitle={t?.code} colors={colors} />
        {t ? (
          <Card colors={colors}>
            <Badge value={t.status} colors={colors} />
            <Text style={{ color: colors.muted, marginTop: 8 }}>{t.city} · {t.phone}</Text>
            <Btn label="Suspend" kind="danger" colors={colors} onPress={async () => { await tenantApi.changeStatus(t.id, "SUSPENDED"); await school.reload(); }} />
            <Btn label="Activate" colors={colors} onPress={async () => { await tenantApi.changeStatus(t.id, "ACTIVE"); await school.reload(); }} />
          </Card>
        ) : null}
        {sub.data ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{sub.data.planName || "Subscription"}</Text>
            <Badge value={sub.data.status} colors={colors} />
            {sub.data.status === "PAYMENT_SUBMITTED" ? (
              <>
                <Btn label="Approve payment" colors={colors} onPress={async () => { await subscriptionApi.review(sub.data!.id, { approve: true }); await sub.reload(); }} />
                <Btn label="Reject payment" kind="danger" colors={colors} onPress={async () => { await subscriptionApi.review(sub.data!.id, { approve: false, rejectionReason: "Rejected from mobile" }); await sub.reload(); }} />
              </>
            ) : null}
          </Card>
        ) : null}
      </Screen>
    </RequireAuth>
  );
}
