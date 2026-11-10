import { useState } from "react";
import { Text } from "react-native";
import { useAppearance } from "../lib/appearance";
import { pickImage } from "../lib/picker";
import { fileApi, subscriptionApi } from "../api/services";
import { Badge, Btn, Card, Empty, Field, Screen, Title, useAsync } from "../ui/kit";
import { RequireAuth } from "../ui/gate";
import { money } from "../lib/format";

export default function Billing() {
  const { colors } = useAppearance();
  const sub = useAsync(() => subscriptionApi.current());
  const [ref, setRef] = useState("");
  return (
    <RequireAuth roles={["SCHOOL_ADMIN"]}>
      <Screen colors={colors} onRefresh={() => void sub.reload()} refreshing={sub.loading}>
        <Title title="Subscription" subtitle="Upload a payment slip. Owner reviews on the platform." colors={colors} />
        {!sub.data ? (
          <Empty title={sub.loading ? "Loading" : "No subscription"} colors={colors} />
        ) : (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{sub.data.planName || "Plan"}</Text>
            <Text style={{ color: colors.muted }}>{money(sub.data.amount)} · {sub.data.periodStart} – {sub.data.periodEnd}</Text>
            <Badge value={sub.data.status} colors={colors} />
            <Field label="Transaction ref" colors={colors} value={ref} onChangeText={setRef} />
            <Btn
              label="Upload payment slip"
              colors={colors}
              onPress={async () => {
                const a = await pickImage();
                if (!a) return;
                const up = await fileApi.upload(a);
                await subscriptionApi.submitPayment(sub.data!.id, { slipUrl: up.url, transactionRef: ref });
                await sub.reload();
              }}
            />
          </Card>
        )}
      </Screen>
    </RequireAuth>
  );
}
