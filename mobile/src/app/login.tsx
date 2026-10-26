import { useState } from "react";
import { KeyboardAvoidingView, Platform, Text, View } from "react-native";
import { router } from "expo-router";
import Animated, { FadeInDown, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../lib/session";
import { portalTitle } from "../lib/format";
import { useAppearance } from "../lib/appearance";
import { ROLE_LABEL, type Role } from "../lib/types";
import { Btn, Chip, Field, Screen, friendlyMessage } from "../ui/kit";
import { BrandHeader } from "../ui/logo";
import { apiOrigin } from "../lib/config";

const ROLES: Role[] = ["STUDENT", "PARENT", "TEACHER", "ACCOUNT_OFFICER", "PRINCIPAL", "SCHOOL_ADMIN", "ERP_OWNER"];

export default function LoginScreen() {
  const { login, brand } = useSession();
  const { colors } = useAppearance();
  const reduce = useReducedMotion();
  const [role, setRole] = useState<Role>("STUDENT");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const origin = apiOrigin();
  const schoolName = brand?.name || "School ERP";

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <Screen colors={colors}>
        <Animated.View entering={reduce ? undefined : FadeInDown.duration(280)}>
          <BrandHeader
            brand={brand}
            colors={colors}
            kicker="Sign in"
            subtitle={brand ? portalTitle(brand) : "One app for every school on this ERP"}
            size={64}
          />
          <Text style={{ color: colors.muted, marginBottom: 18 }}>
            {brand
              ? `Use your ${schoolName} username or email. Same accounts as the web portal.`
              : "Use your generated username or email. After you sign in, this screen shows your school name, code, and logo."}
          </Text>
        </Animated.View>
        <Text style={{ color: colors.muted, fontSize: 12, fontWeight: "700", marginBottom: 8 }}>Portal</Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 16 }}>
          {ROLES.map((r) => (
            <Chip key={r} label={ROLE_LABEL[r]} selected={r === role} onPress={() => setRole(r)} colors={colors} />
          ))}
        </View>
        <Field label="Username or email" colors={colors} autoCapitalize="none" autoCorrect={false} value={username} onChangeText={setUsername} />
        <Field label="Password" colors={colors} secureTextEntry value={password} onChangeText={setPassword} />
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
        <Text style={{ color: colors.muted, fontSize: 12, marginTop: 16 }}>
          {origin ? `API ${origin}` : "Set EXPO_PUBLIC_API_URL for this build (HTTPS in production)."}
        </Text>
      </Screen>
    </KeyboardAvoidingView>
  );
}
