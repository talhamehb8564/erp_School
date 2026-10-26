import { useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import Animated, { FadeInDown, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../lib/session";
import { portalTitle } from "../lib/format";
import { useAppearance } from "../lib/appearance";
import { ROLE_LABEL, type Role } from "../lib/types";
import { Btn, Field, Screen, friendlyMessage } from "../ui/kit";
import { BrandHeader } from "../ui/logo";
import { apiOrigin } from "../lib/config";

const PORTALS: { role: Role; icon: keyof typeof Ionicons.glyphMap; hint: string }[] = [
  { role: "STUDENT", icon: "school-outline", hint: "Marks, homework, timetable" },
  { role: "PARENT", icon: "people-outline", hint: "Children, fees, results" },
  { role: "TEACHER", icon: "easel-outline", hint: "Attendance, homework, exams" },
  { role: "ACCOUNT_OFFICER", icon: "card-outline", hint: "Challans and salaries" },
  { role: "PRINCIPAL", icon: "ribbon-outline", hint: "Campus academic overview" },
  { role: "SCHOOL_ADMIN", icon: "settings-outline", hint: "Users, academics, settings" },
  { role: "ERP_OWNER", icon: "globe-outline", hint: "Schools and subscriptions" },
];

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
  const schoolName = brand?.name;

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <Screen colors={colors}>
        <Animated.View entering={reduce ? undefined : FadeInDown.duration(280)}>
          <BrandHeader
            brand={brand}
            colors={colors}
            kicker="Sign in"
            subtitle={brand ? portalTitle(brand) : "Sign in to your school portal"}
            size={64}
          />
          <Text style={{ color: colors.muted, marginBottom: 16 }}>
            {schoolName
              ? `Use your ${schoolName} username or email. Same accounts as the web portal.`
              : "Use your generated username or email. School name and logo load from this API, not from a previous school."}
          </Text>
        </Animated.View>
        <Text style={{ color: colors.muted, fontSize: 12, fontWeight: "700", marginBottom: 8 }}>Portal</Text>
        <View style={{ gap: 8, marginBottom: 16 }}>
          {PORTALS.map((p, i) => {
            const on = p.role === role;
            return (
              <Animated.View key={p.role} entering={reduce ? undefined : FadeInDown.delay(Math.min(i, 6) * 40).duration(260)}>
                <Pressable
                  onPress={() => setRole(p.role)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: on }}
                  style={{
                    flexDirection: "row",
                    alignItems: "center",
                    gap: 12,
                    padding: 12,
                    borderRadius: 16,
                    backgroundColor: on ? colors.brand : colors.card,
                    borderWidth: 1,
                    borderColor: on ? colors.brand : colors.line,
                  }}
                >
                  <View style={{ width: 40, height: 40, borderRadius: 12, backgroundColor: on ? "rgba(255,255,255,0.18)" : colors.brand + "18", alignItems: "center", justifyContent: "center" }}>
                    <Ionicons name={p.icon} size={20} color={on ? "#fff" : colors.brand} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={{ color: on ? "#fff" : colors.ink, fontWeight: "800" }}>{ROLE_LABEL[p.role]}</Text>
                    <Text style={{ color: on ? "rgba(255,255,255,0.8)" : colors.muted, fontSize: 12 }}>{p.hint}</Text>
                  </View>
                </Pressable>
              </Animated.View>
            );
          })}
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
