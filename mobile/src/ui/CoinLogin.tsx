/**
 * Native login shell: rounded-rectangle card. The whole form flips in.
 * Logo stays a rounded square (no colored ring). Authentication is unchanged.
 */
import { useEffect, type ReactNode } from "react";
import { useWindowDimensions, View } from "react-native";
import Animated, { Easing, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from "react-native-reanimated";
import type { Palette } from "../lib/theme";

export default function CoinLogin({
  colors,
  children,
}: {
  colors: Palette;
  children: ReactNode;
}) {
  const { width } = useWindowDimensions();
  const reduce = useReducedMotion();
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
          width: "100%",
          maxWidth: Math.min(width - 8, 460),
          borderRadius: 28,
          overflow: "hidden",
          alignSelf: "center",
          backgroundColor: colors.card,
          borderWidth: 1,
          borderColor: colors.line,
          paddingHorizontal: 20,
          paddingVertical: 22,
        },
        style,
      ]}
    >
      <View>{children}</View>
    </Animated.View>
  );
}
