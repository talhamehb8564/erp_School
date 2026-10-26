import { Text, View } from "react-native";
import { router } from "expo-router";
import { useSession } from "../../lib/session";
import { useAppearance } from "../../lib/appearance";
import { studentApi } from "../../api/services";
import { Btn, Card, Chip, Screen, useAsync } from "../../ui/kit";
import { BrandHeader } from "../../ui/logo";
import { portalTitle, roleLabel } from "../../lib/format";
import { apiOrigin } from "../../lib/config";

export default function Me() {
  const { user, brand, childId, setChildId, logout } = useSession();
  const { colors, toggle, scheme } = useAppearance();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  if (!user) return null;
  return (
    <Screen colors={colors}>
      <BrandHeader
        brand={brand}
        colors={colors}
        kicker={roleLabel(user.role)}
        subtitle={brand ? portalTitle(brand) : user.role === "ERP_OWNER" ? "Platform console" : "School ERP"}
      />
      <Card colors={colors}>
        <Text style={{ color: colors.ink, fontWeight: "800", fontSize: 18 }}>{user.fullName || user.username}</Text>
        <Text style={{ color: colors.muted, marginTop: 4 }}>{user.username}</Text>
        {user.phone ? <Text style={{ color: colors.muted, marginTop: 4 }}>{user.phone}</Text> : null}
        <Text style={{ color: colors.muted, marginTop: 10, fontSize: 12 }}>API {apiOrigin() || "unset — set EXPO_PUBLIC_API_URL"}</Text>
      </Card>
      {user.role === "PARENT" ? (
        <Card colors={colors}>
          <Text style={{ fontWeight: "800", color: colors.ink, marginBottom: 8 }}>Linked children</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
            {(kids.data || []).map((k) => (
              <Chip
                key={k.id}
                label={k.fullName || k.rollNumber || k.id.slice(0, 8)}
                selected={k.id === childId}
                onPress={() => setChildId(k.id)}
                colors={colors}
              />
            ))}
          </View>
        </Card>
      ) : null}
      <View style={{ gap: 10 }}>
        <Btn label={scheme === "dark" ? "Use light theme" : "Use dark theme"} onPress={toggle} colors={colors} kind="ghost" />
        <Btn label="Change password / username" onPress={() => router.push("/password")} colors={colors} kind="ghost" />
        <Btn
          label="Sign out"
          onPress={async () => {
            await logout();
            router.replace("/login");
          }}
          colors={colors}
          kind="danger"
        />
      </View>
    </Screen>
  );
}
