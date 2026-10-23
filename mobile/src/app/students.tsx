import { useState } from "react";
import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, studentApi } from "../api/services";
import { Badge, Card, Empty, Field, QueryState, Screen, Title, useAsync } from "../ui/kit";


export default function Students() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const [q, setQ] = useState("");
  const classes = useAsync(() => academicApi.classes());
  const list = useAsync(() => studentApi.list(undefined, undefined, 0, q || undefined), [q]);
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Students" subtitle={`${list.data?.totalElements ?? 0} records · paged`} colors={colors} />
      <Field label="Search" colors={colors} value={q} onChangeText={setQ} placeholder="Name, roll, CNIC" />
      <QueryState loading={list.loading} error={list.error} data={list.data?.content} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No students" colors={colors} />}>
        {(list.data?.content || []).map((s) => (
          <Card key={s.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{s.fullName || `${s.firstName || ""} ${s.lastName || ""}`}</Text>
            <Text style={{ color: colors.muted }}>{s.rollNumber} · {s.className || classes.data?.find((c) => c.id === s.classId)?.name || "—"}</Text>
            <Badge value={s.status} colors={colors} />
          </Card>
        ))}
      </QueryState>
      <Text style={{ color: colors.muted, fontSize: 12 }}>Inactivate / promote stay on the web console for bulk safety. Search is live against /api/v1/students.</Text>
    </Screen>
  );
}
