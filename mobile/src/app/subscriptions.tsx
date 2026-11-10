import { Text } from "react-native";
import { useAppearance } from "../lib/appearance";
import { subscriptionApi } from "../api/services";
import { Badge, Btn, Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { RequireAuth } from "../ui/gate";
import { money } from "../lib/format";

export default function Subscriptions() {
  const { colors } = useAppearance();
  const list = useAsync(() => subscriptionApi.list());
  return (
    <RequireAuth roles={["ERP_OWNER"]}>
      <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
        <Title title="Subscriptions" subtitle="Review payment proofs" colors={colors} />
        <QueryState loading={list.loading} error={list.error} data={list.data?.content} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No subscriptions" colors={colors} />}>
          {(list.data?.content || []).map((s) => (
            <Card key={s.id} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{s.tenantName || s.planName}</Text>
              <Text style={{ color: colors.muted }}>{money(s.amount)} · {s.periodStart} – {s.periodEnd}</Text>
              <Badge value={s.status} colors={colors} />
              {s.status === "PAYMENT_SUBMITTED" ? (
                <>
                  <Btn label="Approve" colors={colors} onPress={async () => { await subscriptionApi.review(s.id, { approve: true }); await list.reload(); }} />
                  <Btn label="Reject" kind="danger" colors={colors} onPress={async () => { await subscriptionApi.review(s.id, { approve: false, rejectionReason: "Rejected from mobile" }); await list.reload(); }} />
                </>
              ) : null}
            </Card>
          ))}
        </QueryState>
      </Screen>
    </RequireAuth>
  );
}
