import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { reportApi } from "../api/services";
import { Empty, Screen, Stat, Title, friendlyMessage, useAsync } from "../ui/kit";
import { num } from "../lib/format";

export default function Reports() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const d = useAsync(() => reportApi.schoolDashboard());
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL", "ACCOUNT_OFFICER"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => void d.reload()} refreshing={d.loading}>
      <Title title="Reports" subtitle="Live census" colors={colors} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
        <Stat label="Students" value={num(d.data?.students)} colors={colors} />
        <Stat label="Active" value={num(d.data?.activeStudents)} colors={colors} index={1} />
        <Stat label="Teachers" value={num(d.data?.teachers)} colors={colors} index={2} />
        <Stat label="Unpaid" value={num(d.data?.unpaidChallans)} colors={colors} index={3} />
      </View>
      {d.error ? <Text style={{ color: colors.bad, marginTop: 12 }}>{friendlyMessage(d.error)}</Text> : null}
    </Screen>
  );
}
