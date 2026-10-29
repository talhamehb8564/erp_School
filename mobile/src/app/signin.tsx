import { useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, Text, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { useSession } from "../lib/session";
import { brandSubtitle, roleLabel } from "../lib/format";
import { useAppearance } from "../lib/appearance";
import { ROLE_LABEL, type Role } from "../lib/types";
import { Btn, Field, Screen, friendlyMessage } from "../ui/kit";
import { BrandHeader } from "../ui/logo";
import { bakedBrand } from "../lib/config";

const ROLES = Object.keys(ROLE_LABEL) as Role[];

export default function SignInScreen() {
  const { login, brand } = useSession();
  const { colors } = useAppearance();
  const shown = brand || bakedBrand();
  const params = useLocalSearchParams<{ role?: string }>();
  const role: Role = ROLES.includes(params.role as Role) ? (params.role as Role) : "STUDENT";
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <Screen colors={colors}>
        <BrandHeader
          brand={shown}
          colors={colors}
          kicker={roleLabel(role)}
          subtitle={shown ? brandSubtitle(shown, "Enter your username and password") : "Enter your username and password"}
          size={64}
          flip
        />
        <Pressable onPress={() => router.replace("/login")} accessibilityRole="button" style={{ marginBottom: 16 }}>
          <Text style={{ color: colors.brand, fontWeight: "700" }}>← Change portal</Text>
        </Pressable>
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
        {error ? <Text style={{ color: colors.bad, marginBottom: 12 }}>{error}</Text> : null}
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
        <View style={{ height: 24 }} />
      </Screen>
    </KeyboardAvoidingView>
  );
}
