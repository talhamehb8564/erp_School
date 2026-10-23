import { Text } from "react-native";
import { Link, Stack } from "expo-router";
import { useAppearance } from "../lib/appearance";
import { Screen } from "../ui/kit";

export default function NotFound() {
  const { colors } = useAppearance();
  return (
    <>
      <Stack.Screen options={{ title: "Not found" }} />
      <Screen colors={colors}>
        <Text style={{ color: colors.ink, fontSize: 22, fontWeight: "800" }}>This screen does not exist.</Text>
        <Link href="/" style={{ color: colors.brand, marginTop: 12 }}>
          Go home
        </Link>
      </Screen>
    </>
  );
}
