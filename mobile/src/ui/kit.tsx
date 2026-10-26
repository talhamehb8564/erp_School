import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type TextInputProps,
  type ViewStyle,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import Animated, { FadeInDown, FadeIn, useReducedMotion } from "react-native-reanimated";
import * as Haptics from "expo-haptics";
import { ApiError } from "../api/client";
import { pretty, statusTone } from "../lib/format";
import { dark, light, type Palette } from "../lib/theme";

export function usePalette(scheme: "light" | "dark"): Palette {
  return scheme === "dark" ? dark : light;
}

export function friendlyMessage(error: unknown, fallback = "Something went wrong. Please try again.") {
  if (error instanceof ApiError) {
    if (error.status === 0) return error.message;
    if (error.status === 401) return error.message || "Your session expired. Please sign in again.";
    if (error.fields?.length) return error.fields.map((f) => f.message).filter(Boolean).join(" ");
    const raw = error.message || "";
    if (/cannot read propert|undefined is not|null is not/i.test(raw)) return fallback;
    if (raw && !/exception|sql|hibernate|stacktrace/i.test(raw)) {
      if (error.status === 403) return raw || "You do not have permission to do that.";
      return raw;
    }
    if (error.status === 403) return "You do not have permission to do that.";
    if (error.status === 404) return "The requested record was not found.";
    if (error.status === 409) return "This record already exists or conflicts with another.";
    if (error.status === 422) return "Please check the form and try again.";
    if (error.status >= 500) return "The server had a problem. Try again in a moment.";
    return fallback;
  }
  if (error instanceof Error && error.message) {
    if (/cannot read propert|undefined is not|null is not/i.test(error.message)) return fallback;
    return error.message;
  }
  return fallback;
}

export function Screen({
  children,
  colors,
  onRefresh,
  refreshing,
  padded = true,
}: {
  children: ReactNode;
  colors: Palette;
  onRefresh?: () => void;
  refreshing?: boolean;
  padded?: boolean;
}) {
  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: colors.bg }]} edges={["top", "left", "right"]}>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        contentContainerStyle={[styles.scroll, padded && { padding: 16, paddingBottom: 40 }]}
        refreshControl={
          onRefresh ? (
            <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} tintColor={colors.brand} />
          ) : undefined
        }
      >
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

export function Chip({
  label,
  selected,
  onPress,
  colors,
}: {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  colors: Palette;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: !!selected }}
      onPress={onPress}
      style={{
        paddingHorizontal: 12,
        paddingVertical: 8,
        borderRadius: 999,
        backgroundColor: selected ? colors.brand : colors.card,
        borderWidth: 1,
        borderColor: selected ? colors.brand : colors.line,
      }}
    >
      <Text style={{ color: selected ? "#fff" : colors.ink, fontWeight: "700", fontSize: 13 }}>{label}</Text>
    </Pressable>
  );
}

export function useDebounced<T>(value: T, ms = 350) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export function Title({
  kicker,
  title,
  subtitle,
  colors,
}: {
  kicker?: string;
  title: string;
  subtitle?: string;
  colors: Palette;
}) {
  const reduce = useReducedMotion();
  return (
    <Animated.View entering={reduce ? undefined : FadeInDown.duration(320)} style={{ marginBottom: 16 }}>
      {kicker ? <Text style={[styles.kicker, { color: colors.brand }]}>{kicker}</Text> : null}
      <Text style={[styles.h1, { color: colors.ink }]}>{title}</Text>
      {subtitle ? <Text style={[styles.sub, { color: colors.muted }]}>{subtitle}</Text> : null}
    </Animated.View>
  );
}

export function Card({ children, colors, style }: { children: ReactNode; colors: Palette; style?: ViewStyle }) {
  return <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.line }, style]}>{children}</View>;
}

export function Stat({
  label,
  value,
  colors,
  index = 0,
}: {
  label: string;
  value: ReactNode;
  colors: Palette;
  index?: number;
}) {
  const reduce = useReducedMotion();
  return (
    <Animated.View
      entering={reduce ? undefined : FadeInDown.delay(index * 60).duration(280)}
      style={[styles.stat, { backgroundColor: colors.card, borderColor: colors.line }]}
    >
      <Text style={[styles.statVal, { color: colors.ink }]}>{value ?? "—"}</Text>
      <Text style={[styles.statLbl, { color: colors.muted }]}>{label}</Text>
    </Animated.View>
  );
}

export function Badge({ value, colors }: { value?: string; colors: Palette }) {
  const tone = statusTone(value);
  const bg = tone === "ok" ? colors.ok : tone === "warn" ? colors.warn : tone === "bad" ? colors.bad : colors.muted;
  return (
    <View style={[styles.badge, { backgroundColor: bg + "22" }]}>
      <Text style={[styles.badgeTxt, { color: bg }]}>{pretty(value)}</Text>
    </View>
  );
}

export function Btn({
  label,
  onPress,
  colors,
  kind = "solid",
  disabled,
  loading,
}: {
  label: string;
  onPress?: () => void | Promise<void>;
  colors: Palette;
  kind?: "solid" | "ghost" | "brass" | "danger";
  disabled?: boolean;
  loading?: boolean;
}) {
  const [busy, setBusy] = useState(false);
  const bg =
    kind === "ghost" ? "transparent" : kind === "brass" ? colors.brass : kind === "danger" ? colors.bad : colors.brand;
  const fg = kind === "ghost" ? colors.brand : "#fff";
  const handle = async () => {
    if (!onPress || busy || disabled || loading) return;
    void Haptics.selectionAsync().catch(() => undefined);
    const result = onPress();
    if (result && typeof (result as Promise<void>).then === "function") {
      setBusy(true);
      try {
        await result;
      } finally {
        setBusy(false);
      }
    }
  };
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={() => void handle()}
      disabled={disabled || busy || loading}
      style={({ pressed }) => [
        styles.btn,
        { backgroundColor: bg, borderColor: kind === "ghost" ? colors.line : bg, opacity: disabled ? 0.5 : pressed ? 0.85 : 1 },
      ]}
    >
      {busy || loading ? <ActivityIndicator color={fg} /> : <Text style={[styles.btnTxt, { color: fg }]}>{label}</Text>}
    </Pressable>
  );
}

export function Field({
  label,
  colors,
  ...rest
}: { label: string; colors: Palette } & TextInputProps) {
  return (
    <View style={{ marginBottom: 12 }}>
      <Text style={[styles.fieldLbl, { color: colors.muted }]}>{label}</Text>
      <TextInput
        placeholderTextColor={colors.muted}
        style={[styles.input, { color: colors.ink, borderColor: colors.line, backgroundColor: colors.card }]}
        {...rest}
      />
    </View>
  );
}

export function Empty({ title, hint, colors }: { title: string; hint?: string; colors: Palette }) {
  return (
    <Card colors={colors}>
      <Ionicons name="file-tray-outline" size={28} color={colors.muted} />
      <Text style={[styles.h3, { color: colors.ink, marginTop: 8 }]}>{title}</Text>
      {hint ? <Text style={[styles.sub, { color: colors.muted, marginTop: 6 }]}>{hint}</Text> : null}
    </Card>
  );
}

export function ErrorBox({
  error,
  onRetry,
  colors,
  title = "Unable to load",
}: {
  error: unknown;
  onRetry?: () => void;
  colors: Palette;
  title?: string;
}) {
  return (
    <Card colors={colors} style={{ borderColor: colors.bad }}>
      <Text style={[styles.h3, { color: colors.bad }]}>{title}</Text>
      <Text style={[styles.sub, { color: colors.muted, marginTop: 6 }]}>{friendlyMessage(error)}</Text>
      {onRetry ? <View style={{ marginTop: 10 }}><Btn label="Retry" onPress={onRetry} colors={colors} kind="ghost" /></View> : null}
    </Card>
  );
}

export function Skeleton({ colors, lines = 3 }: { colors: Palette; lines?: number }) {
  return (
    <Card colors={colors}>
      {Array.from({ length: lines }).map((_, i) => (
        <View key={i} style={[styles.skel, { backgroundColor: colors.line, width: i === lines - 1 ? "60%" : "100%" }]} />
      ))}
    </Card>
  );
}

export function QueryState({
  loading,
  error,
  data,
  onRetry,
  colors,
  children,
  empty,
}: {
  loading: boolean;
  error: unknown;
  data: unknown;
  onRetry?: () => void;
  colors: Palette;
  children: ReactNode;
  empty?: ReactNode;
}) {
  const emptyList = Array.isArray(data) && data.length === 0;
  if (loading && (data == null || emptyList)) return <Skeleton colors={colors} />;
  if (error && data == null) return <ErrorBox error={error} onRetry={onRetry} colors={colors} />;
  if (emptyList && empty) return <>{empty}</>;
  return <>{children}</>;
}

export function Row({ children }: { children: ReactNode }) {
  return <View style={styles.row}>{children}</View>;
}

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = [], enabled = true) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const reload = useCallback(async () => {
    if (!enabled) return;
    setLoading(true);
    setError(null);
    try {
      setData(await fnRef.current());
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, [enabled]);
  useEffect(() => {
    if (!enabled) {
      if (data == null) setLoading(true);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    void (async () => {
      try {
        const next = await fnRef.current();
        if (!cancelled) setData(next);
      } catch (e) {
        if (!cancelled) setError(e);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, enabled]);
  return { data, error, loading, reload, setData };
}

export function Icon({ name, color, size = 22 }: { name: keyof typeof Ionicons.glyphMap; color: string; size?: number }) {
  return <Ionicons name={name} size={size} color={color} />;
}

export function Fade({ children }: { children: ReactNode }) {
  const reduce = useReducedMotion();
  return <Animated.View entering={reduce ? undefined : FadeIn.duration(240)}>{children}</Animated.View>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  scroll: { flexGrow: 1 },
  kicker: { fontSize: 12, fontWeight: "700", letterSpacing: 1, textTransform: "uppercase", marginBottom: 4 },
  h1: { fontSize: 26, fontWeight: "800", letterSpacing: -0.4 },
  h3: { fontSize: 16, fontWeight: "700" },
  sub: { fontSize: 14, marginTop: 4, lineHeight: 20 },
  card: { borderRadius: 16, padding: 14, borderWidth: 1, marginBottom: 12 },
  stat: { flexGrow: 1, flexBasis: "30%", minWidth: 104, borderRadius: 16, padding: 12, borderWidth: 1 },
  statVal: { fontSize: 22, fontWeight: "800" },
  statLbl: { fontSize: 12, marginTop: 4 },
  badge: { alignSelf: "flex-start", borderRadius: 999, paddingHorizontal: 8, paddingVertical: 3 },
  badgeTxt: { fontSize: 11, fontWeight: "700" },
  btn: { minHeight: 48, borderRadius: 14, alignItems: "center", justifyContent: "center", paddingHorizontal: 16, borderWidth: 1 },
  btnTxt: { fontSize: 16, fontWeight: "700" },
  fieldLbl: { fontSize: 12, fontWeight: "600", marginBottom: 6 },
  input: { minHeight: 48, borderRadius: 12, borderWidth: 1, paddingHorizontal: 12, fontSize: 16 },
  skel: { height: 12, borderRadius: 6, marginBottom: 8 },
  row: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
});
