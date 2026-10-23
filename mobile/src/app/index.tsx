import { useEffect } from "react";
import { ActivityIndicator, Text, View } from "react-native";
import { Redirect } from "expo-router";
import Animated, { FadeIn, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";

export default function Gate() {
  const { user, ready, loading } = useSession();
  const { colors } = useAppearance();
  const reduce = useReducedMotion();

  useEffect(() => {
    /* splash holds until session hydrates */
  }, []);

  if (!ready || loading) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center", padding: 24 }}>
        <Animated.View entering={reduce ? undefined : FadeIn.duration(400)}>
          <Text style={{ color: "#fff", fontSize: 28, fontWeight: "800", textAlign: "center" }}>School ERP</Text>
          <Text style={{ color: "rgba(255,255,255,0.8)", marginTop: 8, textAlign: "center" }}>DevZard</Text>
        </Animated.View>
        <ActivityIndicator color="#fff" style={{ marginTop: 24 }} />
      </View>
    );
  }
  if (!user) return <Redirect href="/login" />;
  return <Redirect href="/(tabs)" />;
}
