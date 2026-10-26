import { useState } from "react";
import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { userApi } from "../api/services";
import { Badge, Btn, Card, Empty, Field, QueryState, Screen, Title, useAsync, useDebounced } from "../ui/kit";
import { pretty, roleLabel } from "../lib/format";
import type { Role } from "../lib/types";

export default function Users() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const [q, setQ] = useState("");
  const dq = useDebounced(q, 350);
  const list = useAsync(() => userApi.list({ q: dq || undefined }), [dq]);
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Users" subtitle="Staff and family accounts. Phone is kept." colors={colors} />
      <Field label="Search" colors={colors} value={q} onChangeText={setQ} />
      <QueryState loading={list.loading} error={list.error} data={list.data?.content} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No users" colors={colors} />}>
        {(list.data?.content || []).map((u) => (
          <Card key={u.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{u.fullName || u.username}</Text>
            <Text style={{ color: colors.muted }}>{roleLabel(u.role as Role)} · {u.phone || "no phone"} · {u.username}</Text>
            <Badge value={u.status} colors={colors} />
            {user.role === "SCHOOL_ADMIN" ? (
              <Btn
                label={u.status === "ACTIVE" ? "Deactivate" : "Activate"}
                kind="ghost"
                colors={colors}
                onPress={async () => {
                  if (u.status === "ACTIVE") await userApi.deactivate(u.id);
                  else await userApi.activate(u.id);
                  await list.reload();
                }}
              />
            ) : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
