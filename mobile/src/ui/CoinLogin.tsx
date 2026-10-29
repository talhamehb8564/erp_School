/**
 * EXPERIMENTAL coin-shaped login (mobile).
 *
 * The username, password and submit controls sit inside a circular coin that
 * flips into view. Logo is a rounded square inside the coin — no colored ring.
 *
 * To revert: restore BrandHeader + stacked Fields in signin.tsx.
 */
import { useEffect, type ReactNode } from "react";
import { ScrollView, useWindowDimensions, View } from "react-native";
import Animated, { Easing, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from "react-native-reanimated";
import type { Palette } from "../lib/theme";

export default function CoinLogin({
  colors,
  children,
}: {
  colors: Palette;
  children: ReactNode;
}) {
  const { width, height } = useWindowDimensions();
  const reduce = useReducedMotion();
  const size = Math.max(280, Math.min(width - 24, height - 120, 440));
  const spin = useSharedValue(reduce ? 1 : 0);

  useEffect(() => {
    if (reduce) return;
    spin.value = withTiming(1, { duration: 720, easing: Easing.out(Easing.cubic) });
  }, [reduce, spin]);

  const style = useAnimatedStyle(() => ({
    opacity: 0.15 + spin.value * 0.85,
    transform: [
      { perspective: 1100 },
      { rotateY: `${(1 - spin.value) * 180}deg` },
      { scale: 0.86 + spin.value * 0.14 },
    ],
  }));

  return (
    <Animated.View
      style={[
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          overflow: "hidden",
          alignSelf: "center",
          backgroundColor: colors.card,
          borderWidth: 1,
          borderColor: colors.line,
        },
        style,
      ]}
    >
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          paddingHorizontal: Math.round(size * 0.14),
          paddingVertical: Math.round(size * 0.1),
          minHeight: size,
          justifyContent: "center",
        }}
      >
        <View>{children}</View>
      </ScrollView>
    </Animated.View>
  );
}
