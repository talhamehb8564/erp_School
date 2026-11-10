import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { salaryApi } from "../api/services";
import { Badge, Btn, Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { money } from "../lib/format";

export default function Salaries() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const month = new Date().toISOString().slice(0, 7);
  const mine = useAsync(() => (user?.role === "TEACHER" ? salaryApi.mine() : Promise.resolve([])), [user?.role]);
  const list = useAsync(() => (user?.role !== "TEACHER" ? salaryApi.month(month) : Promise.resolve([])), [user?.role]);
  if (!user || !["SCHOOL_ADMIN", "ACCOUNT_OFFICER", "PRINCIPAL", "TEACHER"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  const rows = user.role === "TEACHER" ? mine.data || [] : list.data || [];
  return (
    <Screen colors={colors} onRefresh={() => void (user.role === "TEACHER" ? mine.reload() : list.reload())} refreshing={mine.loading || list.loading}>
      <Title title="Salaries" subtitle={month} colors={colors} />
      {user.role === "ACCOUNT_OFFICER" ? (
        <Btn label={`Generate ${month}`} colors={colors} onPress={async () => { await salaryApi.generate(month); await list.reload(); }} />
      ) : null}
      <QueryState loading={mine.loading || list.loading} error={mine.error || list.error} data={rows} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No salary rows" colors={colors} />}>
        {rows.map((s) => (
          <Card key={String(s.id)} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(s.fullName || s.staffName || "Staff")}</Text>
            <Text style={{ color: colors.muted }}>{money(s.netPay as number | string | null | undefined)} · {String(s.month || "")}</Text>
            <Badge value={String(s.status)} colors={colors} />
            {user.role === "ACCOUNT_OFFICER" && s.status !== "PAID" ? (
              <Btn label="Mark paid" kind="ghost" colors={colors} onPress={async () => { await salaryApi.pay(String(s.id)); await list.reload(); }} />
            ) : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
