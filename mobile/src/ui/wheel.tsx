import { useEffect, useState } from "react";
import { Text, View } from "react-native";
import { useReducedMotion } from "react-native-reanimated";
import type { Palette } from "../lib/theme";

const TICKS = 32;

/**
 * Animated percentage ring. Values come from the backend.
 * `empty` means the API returned no sample yet (not a fake 0%).
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
  const target = has ? Math.max(0, Math.min(100, Number(value))) : 0;
  const [fill, setFill] = useState(reduce ? target : 0);

  useEffect(() => {
    if (!has) {
      setFill(0);
      return;
    }
    if (reduce) {
      setFill(target);
      return;
    }
    setFill(0);
    const start = Date.now();
    const dur = 900;
    let frame = 0;
    const tick = () => {
      const t = Math.min(1, (Date.now() - start) / dur);
      const eased = 1 - Math.pow(1 - t, 3);
      setFill(target * eased);
      if (t < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [has, target, reduce]);

  return (
    <View style={{ flexGrow: 1, flexBasis: "30%", minWidth: 104, alignItems: "center", marginBottom: 8 }}>
      <View
        style={{ width: 112, height: 112, alignItems: "center", justifyContent: "center" }}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${label} ${has ? Math.round(fill) : "no data"} percent`}
        accessibilityValue={{ min: 0, max: 100, now: has ? Math.round(fill) : 0 }}
      >
        {Array.from({ length: TICKS }, (_, i) => {
          const ang = (i / TICKS) * 2 * Math.PI - Math.PI / 2;
          const on = has && (i + 0.5) / TICKS <= fill / 100;
          const r = 48;
          return (
            <View
              key={i}
              style={{
                position: "absolute",
                left: 56 + r * Math.cos(ang) - 2,
                top: 56 + r * Math.sin(ang) - 6,
                width: 4,
                height: 12,
                borderRadius: 2,
                backgroundColor: loading ? colors.line : on ? colors.brand : colors.line,
                transform: [{ rotate: `${(i / TICKS) * 360}deg` }],
              }}
            />
          );
        })}
        <Text style={{ color: colors.ink, fontSize: 22, fontWeight: "800" }}>
          {loading ? "…" : has ? `${Math.round(fill)}%` : "—"}
        </Text>
      </View>
      <Text style={{ color: colors.ink, fontWeight: "800", marginTop: 8, textAlign: "center" }}>{label}</Text>
      <Text style={{ color: colors.muted, fontSize: 12, textAlign: "center", marginTop: 2, paddingHorizontal: 4 }}>
        {loading ? "Loading" : empty ? "No data yet" : hint || (has ? `${Math.round(target)} percent` : "Not available")}
      </Text>
    </View>
  );
}
