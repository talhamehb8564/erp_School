import { Redirect } from "expo-router";
import { ActivityIndicator, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import type { Role } from "../lib/types";
import { Empty, Screen } from "./kit";

export function RequireAuth({ children, roles }: { children: React.ReactNode; roles?: Role[] }) {
  const { user, ready, loading } = useSession();
  const { colors } = useAppearance();
  if (!ready || loading) {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: colors.bg }}>
        <ActivityIndicator color={colors.brand} />
      </View>
    );
  }
  if (!user) return <Redirect href="/login" />;
  if (roles && !roles.includes(user.role)) {
    return (
      <Screen colors={colors}>
        <Empty title="Not permitted" hint="This module is not assigned to your role." colors={colors} />
      </Screen>
    );
  }
  return <>{children}</>;
}
