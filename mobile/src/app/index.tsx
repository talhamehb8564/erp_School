import { ActivityIndicator, Text, View } from "react-native";
import { Redirect } from "expo-router";
import Animated, { FadeIn, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { SchoolLogo } from "../ui/logo";

export default function Gate() {
  const { user, ready, brand } = useSession();
  const { colors } = useAppearance();
  const reduce = useReducedMotion();

  if (!ready) {
    const known = Boolean(brand?.name);
    return (
      <View style={{ flex: 1, backgroundColor: known ? colors.brand : colors.bg, alignItems: "center", justifyContent: "center", padding: 24 }}>
        <Animated.View entering={reduce ? undefined : FadeIn.duration(280)} style={{ alignItems: "center" }}>
          {known ? (
            <>
              <SchoolLogo uri={brand?.logoUrl} name={brand?.name} color="#fff" size={72} />
              <Text style={{ color: "#fff", fontSize: 24, fontWeight: "800", textAlign: "center", marginTop: 16 }}>{brand!.name}</Text>
              {brand!.tagline ? (
                <Text style={{ color: "rgba(255,255,255,0.85)", marginTop: 6, textAlign: "center" }}>{brand!.tagline}</Text>
              ) : null}
            </>
          ) : (
            <>
              <Text style={{ color: colors.ink, fontSize: 28, fontWeight: "800", textAlign: "center" }}>School ERP</Text>
              <Text style={{ color: colors.muted, marginTop: 8, textAlign: "center" }}>Loading your portal</Text>
            </>
          )}
        </Animated.View>
        <ActivityIndicator color={known ? "#fff" : colors.brand} style={{ marginTop: 24 }} />
      </View>
    );
  }
  if (!user) return <Redirect href="/login" />;
  return <Redirect href="/(tabs)" />;
}
