import { useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import Animated, { FadeInDown, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { ROLE_LABEL, type Role } from "../lib/types";
import { Btn, Field, Screen, friendlyMessage } from "../ui/kit";
import { apiOrigin } from "../lib/config";

const ROLES: Role[] = ["STUDENT", "PARENT", "TEACHER", "ACCOUNT_OFFICER", "PRINCIPAL", "SCHOOL_ADMIN", "ERP_OWNER"];

export default function LoginScreen() {
  const { login } = useSession();
  const { colors } = useAppearance();
  const reduce = useReducedMotion();
  const [role, setRole] = useState<Role>("STUDENT");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const origin = apiOrigin();

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <Screen colors={colors}>
        <Animated.View entering={reduce ? undefined : FadeInDown.duration(360)}>
          <Text style={{ color: colors.brand, fontWeight: "800", letterSpacing: 1, fontSize: 12 }}>SIGN IN</Text>
          <Text style={{ color: colors.ink, fontSize: 32, fontWeight: "800", marginTop: 6 }}>School ERP</Text>
          <Text style={{ color: colors.muted, marginTop: 6, marginBottom: 18 }}>
            Use your generated username or email. Same accounts as the web ERP.
          </Text>
        </Animated.View>
        <Text style={{ color: colors.muted, fontSize: 12, fontWeight: "700", marginBottom: 8 }}>Portal</Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 16 }}>
          {ROLES.map((r) => {
            const on = r === role;
            return (
              <Pressable
                key={r}
                onPress={() => setRole(r)}
                style={{
                  paddingHorizontal: 10,
                  paddingVertical: 8,
                  borderRadius: 999,
                  backgroundColor: on ? colors.brand : colors.card,
                  borderWidth: 1,
                  borderColor: on ? colors.brand : colors.line,
                }}
              >
                <Text style={{ color: on ? "#fff" : colors.ink, fontSize: 12, fontWeight: "700" }}>{ROLE_LABEL[r]}</Text>
              </Pressable>
            );
          })}
        </View>
        <Field label="Username or email" colors={colors} autoCapitalize="none" autoCorrect={false} value={username} onChangeText={setUsername} />
        <Field label="Password" colors={colors} secureTextEntry value={password} onChangeText={setPassword} />
        {error ? <Text style={{ color: colors.bad, marginBottom: 12 }}>{error}</Text> : null}
        <Btn
          label={`Sign in as ${ROLE_LABEL[role]}`}
          colors={colors}
          kind="brass"
          onPress={async () => {
            setError("");
            try {
              await login(username, password, role);
              router.replace("/(tabs)");
            } catch (e) {
              setError(friendlyMessage(e, "Invalid username or password"));
            }
          }}
        />
        <Text style={{ color: colors.muted, fontSize: 12, marginTop: 16 }}>
          API {origin || "(set EXPO_PUBLIC_API_URL)"}
        </Text>
      </Screen>
    </KeyboardAvoidingView>
  );
}
