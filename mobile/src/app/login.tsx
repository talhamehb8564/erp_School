import { Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import Animated, { FadeInDown, useReducedMotion } from "react-native-reanimated";
import { useSession } from "../lib/session";
import { brandSubtitle } from "../lib/format";
import { useAppearance } from "../lib/appearance";
import { ROLE_LABEL, type Role } from "../lib/types";
import { Screen } from "../ui/kit";
import { BrandHeader } from "../ui/logo";
import { apiOrigin, bakedBrand } from "../lib/config";

export const PORTALS: { role: Role; icon: keyof typeof Ionicons.glyphMap; hint: string }[] = [
  { role: "STUDENT", icon: "school-outline", hint: "Marks, homework, timetable" },
  { role: "PARENT", icon: "people-outline", hint: "Children, fees, results" },
  { role: "TEACHER", icon: "easel-outline", hint: "Attendance, homework, exams" },
  { role: "ACCOUNT_OFFICER", icon: "card-outline", hint: "Challans and salaries" },
  { role: "PRINCIPAL", icon: "ribbon-outline", hint: "Campus academic overview" },
  { role: "SCHOOL_ADMIN", icon: "settings-outline", hint: "Users, academics, settings" },
  { role: "ERP_OWNER", icon: "globe-outline", hint: "Schools and subscriptions" },
];

export default function LoginScreen() {
  const { brand } = useSession();
  const { colors } = useAppearance();
  const reduce = useReducedMotion();
  const origin = apiOrigin();
  const shown = brand || bakedBrand();
  const schoolName = shown?.name;

  return (
    <Screen colors={colors}>
      <Animated.View entering={reduce ? undefined : FadeInDown.duration(280)}>
        <BrandHeader
          brand={shown}
          colors={colors}
          kicker="Choose portal"
          subtitle={shown ? brandSubtitle(shown, "Select your role to continue") : "Select your role to continue"}
          size={64}
          flip
        />
        <Text style={{ color: colors.muted, marginBottom: 16 }}>
          {schoolName
            ? `Sign in to ${schoolName} with the same username as the web portal.`
            : "Use your generated username. School name and logo load from this API, not from a previous school."}
        </Text>
      </Animated.View>
      <View style={{ gap: 8, marginBottom: 16 }}>
        {PORTALS.map((p, i) => (
          <Animated.View key={p.role} entering={reduce ? undefined : FadeInDown.delay(Math.min(i, 6) * 40).duration(260)}>
            <Pressable
              onPress={() => router.push(`/signin?role=${p.role}` as never)}
              accessibilityRole="button"
              accessibilityLabel={`Open ${ROLE_LABEL[p.role]} sign in`}
              style={{
                flexDirection: "row",
                alignItems: "center",
                gap: 12,
                padding: 12,
                borderRadius: 16,
                backgroundColor: colors.card,
                borderWidth: 1,
                borderColor: colors.line,
              }}
            >
              <View
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 12,
                  backgroundColor: colors.brand + "18",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <Ionicons name={p.icon} size={20} color={colors.brand} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={{ color: colors.ink, fontWeight: "800" }}>{ROLE_LABEL[p.role]}</Text>
                <Text style={{ color: colors.muted, fontSize: 12 }}>{p.hint}</Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.muted} />
            </Pressable>
          </Animated.View>
        ))}
      </View>
      <Text style={{ color: colors.muted, fontSize: 12 }}>
        {origin ? `API ${origin}` : "Set EXPO_PUBLIC_API_URL for this build (HTTPS in production)."}
      </Text>
    </Screen>
  );
}
