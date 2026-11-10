import { Redirect, Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useAppearance } from "../../lib/appearance";
import { useSession } from "../../lib/session";

export default function TabsLayout() {
  const { colors } = useAppearance();
  const { user, ready } = useSession();
  if (!ready) return null;
  if (!user) return <Redirect href="/login" />;
  if (user.mustChangePassword) return <Redirect href="/password" />;
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.brand,
        tabBarInactiveTintColor: colors.muted,
        tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.line },
        tabBarLabelStyle: { fontSize: 11, fontWeight: "700" },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: "Home",
          tabBarIcon: ({ color, size }) => <Ionicons name="home" color={typeof color === "string" ? color : colors.brand} size={size} />,
        }}
      />
      <Tabs.Screen
        name="hub"
        options={{
          title: "Portal",
          tabBarIcon: ({ color, size }) => <Ionicons name="grid" color={typeof color === "string" ? color : colors.brand} size={size} />,
        }}
      />
      <Tabs.Screen
        name="alerts"
        options={{
          title: "Alerts",
          tabBarIcon: ({ color, size }) => <Ionicons name="notifications" color={typeof color === "string" ? color : colors.brand} size={size} />,
        }}
      />
      <Tabs.Screen
        name="me"
        options={{
          title: "Me",
          tabBarIcon: ({ color, size }) => <Ionicons name="person" color={typeof color === "string" ? color : colors.brand} size={size} />,
        }}
      />
    </Tabs>
  );
}
