import { useEffect } from "react";
import { Text, View } from "react-native";
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";
import type { Palette } from "../lib/theme";

const TICKS = 28;

/**
 * Real percentage ring. `null` / undefined = no data (not a fake 0).
 * `empty` is used when the backend returned 0 because there is no sample yet.
 */
export function ProgressWheel({
  value,
  label,
  hint,
  colors,
  loading,
  empty,
}: {
  value?: number | null;
  label: string;
  hint?: string;
  colors: Palette;
  loading?: boolean;
  empty?: boolean;
}) {
  const reduce = useReducedMotion();
  const has = !loading && !empty && value != null && Number.isFinite(Number(value));
  const pct = has ? Math.max(0, Math.min(100, Number(value))) : 0;
  const shown = useSharedValue(reduce ? pct : 0);

  useEffect(() => {
    shown.value = withTiming(pct, { duration: reduce ? 0 : 700, easing: Easing.out(Easing.cubic) });
  }, [pct, reduce, shown]);

  const countStyle = useAnimatedStyle(() => ({
    opacity: 1,
  }));

  return (
    <View style={{ flexGrow: 1, flexBasis: "30%", minWidth: 104, alignItems: "center", marginBottom: 8 }}>
      <View style={{ width: 108, height: 108, alignItems: "center", justifyContent: "center" }} accessible accessibilityRole="progressbar" accessibilityLabel={`${label} ${has ? Math.round(pct) : "no data"} percent`} accessibilityValue={{ min: 0, max: 100, now: has ? Math.round(pct) : 0 }}>
        {Array.from({ length: TICKS }, (_, i) => {
          const ang = (i / TICKS) * 2 * Math.PI - Math.PI / 2;
          const on = has && (i + 0.5) / TICKS <= pct / 100;
          const r = 46;
          return (
            <View
              key={i}
              style={{
                position: "absolute",
                left: 54 + r * Math.cos(ang) - 2,
                top: 54 + r * Math.sin(ang) - 5,
                width: 4,
                height: 11,
                borderRadius: 2,
                backgroundColor: loading ? colors.line : on ? colors.brand : colors.line,
                transform: [{ rotate: `${(i / TICKS) * 360}deg` }],
              }}
            />
          );
        })}
        <Animated.Text style={[{ color: colors.ink, fontSize: 22, fontWeight: "800" }, countStyle]}>
          {loading ? "…" : has ? `${Math.round(pct)}%` : "—"}
        </Animated.Text>
      </View>
      <Text style={{ color: colors.ink, fontWeight: "800", marginTop: 8, textAlign: "center" }}>{label}</Text>
      <Text style={{ color: colors.muted, fontSize: 12, textAlign: "center", marginTop: 2, paddingHorizontal: 4 }}>
        {loading ? "Loading" : empty ? "No data yet" : hint || (has ? `${Math.round(pct)} percent` : "Not available")}
      </Text>
    </View>
  );
}
