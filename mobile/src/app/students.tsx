import { useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Empty, Field, QueryState, Screen, Title, useAsync, useDebounced } from "../ui/kit";


export default function Students() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const dq = useDebounced(q, 350);
  const classes = useAsync(() => academicApi.classes());
  const list = useAsync(() => studentApi.list(undefined, undefined, page, dq || undefined), [dq, page]);
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Students" subtitle={`${list.data?.totalElements ?? 0} records · paged`} colors={colors} />
      <Field label="Search" colors={colors} value={q} onChangeText={(t) => { setQ(t); setPage(0); }} placeholder="Name, roll, CNIC" />
      <QueryState loading={list.loading} error={list.error} data={list.data?.content} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No students" colors={colors} />}>
        {(list.data?.content || []).map((s) => (
          <Card key={s.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{s.fullName || `${s.firstName || ""} ${s.lastName || ""}`}</Text>
            <Text style={{ color: colors.muted }}>{s.rollNumber} · {s.className || classes.data?.find((c) => c.id === s.classId)?.name || "—"}</Text>
            <Badge value={s.status} colors={colors} />
          </Card>
        ))}
      </QueryState>
      <View style={{ flexDirection: "row", gap: 8 }}>
        {page > 0 ? <Btn label="Previous" kind="ghost" colors={colors} onPress={() => setPage((p) => Math.max(0, p - 1))} /> : null}
        {list.data && !list.data.last ? <Btn label="Next page" kind="ghost" colors={colors} onPress={() => setPage((p) => p + 1)} /> : null}
      </View>
      <Text style={{ color: colors.muted, fontSize: 12 }}>Inactivate / promote stay on the web console for bulk safety. Search is live against /api/v1/students.</Text>
    </Screen>
  );
}
