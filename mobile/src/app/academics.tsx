import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi } from "../api/services";
import { Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";

export default function Academics() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const classes = useAsync(() => academicApi.classes());
  const subjects = useAsync(() => academicApi.subjects());
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => { void classes.reload(); void subjects.reload(); }} refreshing={classes.loading}>
      <Title title="Academics" subtitle="Classes and subjects" colors={colors} />
      <QueryState loading={classes.loading} error={classes.error} data={classes.data} onRetry={() => void classes.reload()} colors={colors}>
        {(classes.data || []).map((c) => (
          <Card key={c.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{c.name}</Text>
            <Text style={{ color: colors.muted }}>{c.grade || "Class"}</Text>
          </Card>
        ))}
      </QueryState>
      <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Subjects</Text>
      {(subjects.data || []).map((s) => (
        <Text key={s.id} style={{ color: colors.muted, marginBottom: 6 }}>{s.name} ({s.code})</Text>
      ))}
    </Screen>
  );
}
