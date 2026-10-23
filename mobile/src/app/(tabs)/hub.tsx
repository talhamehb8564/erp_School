import { Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useSession } from "../../lib/session";
import { useAppearance } from "../../lib/appearance";
import { useFeatures } from "../../lib/features";
import { MODULES } from "../../lib/nav";
import { Screen, Title } from "../../ui/kit";

export default function Hub() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  if (!user) return null;
  const items = MODULES.filter((m) => m.roles.includes(user.role) && on(m.feature));
  return (
    <Screen colors={colors}>
      <Title kicker="Navigate" title="Modules" subtitle="Same permissions as the web ERP." colors={colors} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
        {items.map((m) => (
          <Pressable
            key={m.href}
            onPress={() => router.push(m.href as never)}
            style={{
              width: "47%",
              backgroundColor: colors.card,
              borderColor: colors.line,
              borderWidth: 1,
              borderRadius: 16,
              padding: 14,
              minHeight: 96,
            }}
          >
            <Ionicons name={m.icon as never} size={22} color={colors.brand} />
            <Text style={{ color: colors.ink, fontWeight: "800", marginTop: 10 }}>{m.label}</Text>
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}
