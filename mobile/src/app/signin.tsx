import { useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, Text, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import Animated, { FadeInDown, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../lib/session";
import { roleLabel } from "../lib/format";
import { useAppearance } from "../lib/appearance";
import { ROLE_LABEL, type Role } from "../lib/types";
import { Btn, Field, Screen, friendlyMessage } from "../ui/kit";
import { SchoolLogo } from "../ui/logo";
import CoinLogin from "../ui/CoinLogin";
import { bakedBrand } from "../lib/config";

const ROLES = Object.keys(ROLE_LABEL) as Role[];

export default function SignInScreen() {
  const { login, brand } = useSession();
  const { colors } = useAppearance();
  const reduce = useReducedMotion();
  const shown = brand || bakedBrand();
  const params = useLocalSearchParams<{ role?: string }>();
  const role: Role = ROLES.includes(params.role as Role) ? (params.role as Role) : "STUDENT";
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const enter = (delay: number) => (reduce ? undefined : FadeInDown.delay(delay).duration(280));

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <Screen colors={colors}>
        <Pressable onPress={() => router.replace("/login")} accessibilityRole="button" style={{ marginBottom: 12 }}>
          <Text style={{ color: colors.brand, fontWeight: "700" }}>← Change portal</Text>
        </Pressable>
        <CoinLogin colors={colors}>
          <View style={{ alignItems: "center", marginBottom: 8 }}>
            <SchoolLogo uri={shown?.logoUrl} name={shown?.name} color={colors.brand} size={52} variant="mark" />
          </View>
          <Animated.Text entering={enter(280)} style={{ color: colors.ink, fontWeight: "800", textAlign: "center", fontSize: 16 }}>
            {shown?.name || "School"}
          </Animated.Text>
          <Animated.Text entering={enter(360)} style={{ color: colors.brand, fontWeight: "800", textAlign: "center", fontSize: 22, marginBottom: 4 }}>
            {roleLabel(role)}
          </Animated.Text>
          <Animated.View entering={enter(440)}>
            <Field
              label="Username or email"
              colors={colors}
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="username"
              textContentType="username"
              value={username}
              onChangeText={setUsername}
              returnKeyType="next"
            />
          </Animated.View>
          <Animated.View entering={enter(520)}>
            <Field
              label="Password"
              colors={colors}
              secureTextEntry
              autoComplete="password"
              textContentType="password"
              value={password}
              onChangeText={setPassword}
              returnKeyType="done"
            />
          </Animated.View>
          {error ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{error}</Text> : null}
          <Animated.View entering={enter(600)}>
            <Btn
              label={busy ? "Signing in…" : `Sign in as ${ROLE_LABEL[role]}`}
              colors={colors}
              kind="brass"
              disabled={!username.trim() || !password || busy}
              loading={busy}
              onPress={async () => {
                setError("");
                setBusy(true);
                try {
                  await login(username, password, role);
                  router.replace("/(tabs)");
                } catch (e) {
                  setError(friendlyMessage(e, "Invalid username or password"));
                } finally {
                  setBusy(false);
                }
              }}
            />
          </Animated.View>
        </CoinLogin>
      </Screen>
    </KeyboardAvoidingView>
  );
}
