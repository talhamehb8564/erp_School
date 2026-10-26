import { Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import Animated, { FadeInDown, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../../lib/session";
import { useAppearance } from "../../lib/appearance";
import { useFeatures } from "../../lib/features";
import { MODULES } from "../../lib/nav";
import { Screen } from "../../ui/kit";
import { BrandHeader } from "../../ui/logo";
import { portalTitle, roleLabel } from "../../lib/format";

export default function Hub() {
  const { user, brand } = useSession();
  const { colors } = useAppearance();
  const { on } = useFeatures();
  const reduce = useReducedMotion();
  if (!user) return null;
  const items = MODULES.filter((m) => m.roles.includes(user.role) && on(m.feature));
  return (
    <Screen colors={colors}>
      <BrandHeader
        brand={brand}
        colors={colors}
        kicker={roleLabel(user.role)}
        subtitle={brand ? portalTitle(brand) : "Modules for your role"}
      />
      <Text style={{ color: colors.muted, marginBottom: 14 }}>
        Only the modules your account is allowed to use. Same rules as the web ERP.
      </Text>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
        {items.map((m, i) => (
          <Animated.View
            key={m.href}
            entering={reduce ? undefined : FadeInDown.delay(Math.min(i, 8) * 40).duration(280)}
            style={{ flexGrow: 1, flexBasis: "46%", maxWidth: "48%" }}
          >
            <Pressable
              onPress={() => router.push(m.href as never)}
              accessibilityRole="button"
              accessibilityLabel={m.label}
              style={{
                backgroundColor: colors.card,
                borderColor: colors.line,
                borderWidth: 1,
                borderRadius: 18,
                padding: 14,
                minHeight: 104,
              }}
            >
              <View style={{ width: 36, height: 36, borderRadius: 12, backgroundColor: colors.brand + "18", alignItems: "center", justifyContent: "center" }}>
                <Ionicons name={m.icon as never} size={20} color={colors.brand} />
              </View>
              <Text style={{ color: colors.ink, fontWeight: "800", marginTop: 12 }}>{m.label}</Text>
            </Pressable>
          </Animated.View>
        ))}
      </View>
      {items.length === 0 ? (
        <Text style={{ color: colors.muted, marginTop: 8 }}>No modules are enabled for this account.</Text>
      ) : null}
    </Screen>
  );
}
