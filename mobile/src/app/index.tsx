import { ActivityIndicator, Text, View } from "react-native";
import { Redirect } from "expo-router";
import Animated, { FadeIn, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { bakedBrand, portalAppName } from "../lib/config";
import { SchoolLogo } from "../ui/logo";

export default function Gate() {
  const { user, ready, brand } = useSession();
  const { colors } = useAppearance();
  const reduce = useReducedMotion();
  const shown = brand || bakedBrand();

  if (!ready) {
    const name = shown?.name || portalAppName();
    return (
      <View style={{ flex: 1, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center", padding: 24 }}>
        <Animated.View entering={reduce ? undefined : FadeIn.duration(200)} style={{ alignItems: "center" }}>
          <SchoolLogo uri={shown?.logoUrl} name={name} color="#fff" size={72} />
          <Text style={{ color: "#fff", fontSize: 24, fontWeight: "800", textAlign: "center", marginTop: 16 }}>{name}</Text>
          <Text style={{ color: "rgba(255,255,255,0.85)", marginTop: 6, textAlign: "center" }}>
            {shown?.tagline || "Portal"}
          </Text>
        </Animated.View>
        <ActivityIndicator color="#fff" style={{ marginTop: 24 }} />
      </View>
    );
  }
  if (!user) return <Redirect href="/login" />;
  return <Redirect href="/(tabs)" />;
}
