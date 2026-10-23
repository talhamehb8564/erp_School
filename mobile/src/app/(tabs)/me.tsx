import { Text, View } from "react-native";
import { router } from "expo-router";
import { useSession } from "../../lib/session";
import { useAppearance } from "../../lib/appearance";
import { studentApi } from "../../api/services";
import { Btn, Card, Screen, Title, useAsync } from "../../ui/kit";
import { roleLabel } from "../../lib/format";
import { apiOrigin } from "../../lib/config";

export default function Me() {
  const { user, tenant, childId, setChildId, logout } = useSession();
  const { colors, toggle, scheme } = useAppearance();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  if (!user) return null;
  return (
    <Screen colors={colors}>
      <Title kicker={roleLabel(user.role)} title={user.fullName || user.username} subtitle={tenant?.name || "Platform console"} colors={colors} />
      <Card colors={colors}>
        <Text style={{ color: colors.muted }}>Username</Text>
        <Text style={{ color: colors.ink, fontWeight: "700", marginBottom: 8 }}>{user.username}</Text>
        {user.phone ? (
          <>
            <Text style={{ color: colors.muted }}>Phone</Text>
            <Text style={{ color: colors.ink, fontWeight: "700", marginBottom: 8 }}>{user.phone}</Text>
          </>
        ) : null}
        <Text style={{ color: colors.muted }}>API</Text>
        <Text style={{ color: colors.ink }}>{apiOrigin() || "unset"}</Text>
      </Card>
      {user.role === "PARENT" ? (
        <Card colors={colors}>
          <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Linked children</Text>
          {(kids.data || []).map((k) => (
            <Text
              key={k.id}
              onPress={() => setChildId(k.id)}
              style={{ color: k.id === childId ? colors.brand : colors.ink, marginBottom: 8, fontWeight: k.id === childId ? "800" : "500" }}
            >
              {k.fullName || k.rollNumber} {k.id === childId ? "· selected" : ""}
            </Text>
          ))}
        </Card>
      ) : null}
      <View style={{ gap: 10 }}>
        <Btn label={scheme === "dark" ? "Use light theme" : "Use dark theme"} onPress={toggle} colors={colors} kind="ghost" />
        <Btn label="Change password / username" onPress={() => router.push("/password")} colors={colors} kind="ghost" />
        <Btn label="Sign out" onPress={async () => { await logout(); router.replace("/login"); }} colors={colors} kind="danger" />
      </View>
    </Screen>
  );
}
