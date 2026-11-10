import { useMemo, useState } from "react";
import { Modal, Pressable, ScrollView, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import type { Palette } from "../lib/theme";
import { fmtTime } from "../lib/format";

const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
const DOW = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];
const HOURS = Array.from({ length: 24 }, (_, i) => i);
const MINUTES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55];

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function daysInMonth(y: number, m: number) {
  return new Date(y, m, 0).getDate();
}

function mondayOffset(y: number, m: number) {
  const js = new Date(y, m - 1, 1).getDay();
  return (js + 6) % 7;
}

export type DateMode = "date" | "datetime" | "month";

function parseParts(value: string, mode: DateMode) {
  const now = new Date();
  let y = now.getFullYear();
  let m = now.getMonth() + 1;
  let d = now.getDate();
  let h = 9;
  let min = 0;
  if (!value) return { y, m, d, h, min };
  if (mode === "datetime") {
    const iso = new Date(value.includes("T") || value.includes("Z") ? value : `${value}:00`);
    if (!Number.isNaN(iso.getTime())) {
      return { y: iso.getFullYear(), m: iso.getMonth() + 1, d: iso.getDate(), h: iso.getHours(), min: iso.getMinutes() };
    }
  }
  const match = value.match(/^(\d{4})-(\d{2})(?:-(\d{2}))?(?:[T ](\d{2}):(\d{2}))?/);
  if (match) {
    y = Number(match[1]);
    m = Number(match[2]);
    if (match[3]) d = Number(match[3]);
    if (match[4]) h = Number(match[4]);
    if (match[5]) min = Number(match[5]);
  }
  return { y, m, d, h, min };
}

function formatValue(p: { y: number; m: number; d: number; h: number; min: number }, mode: DateMode) {
  if (mode === "month") return `${p.y}-${pad(p.m)}-01`;
  if (mode === "date") return `${p.y}-${pad(p.m)}-${pad(p.d)}`;
  return `${p.y}-${pad(p.m)}-${pad(p.d)}T${pad(p.h)}:${pad(p.min)}`;
}

function prettyValue(value: string, mode: DateMode) {
  const p = parseParts(value, mode);
  if (mode === "month") return `${MONTHS[p.m - 1]} ${p.y}`;
  const day = `${p.d} ${MONTHS[p.m - 1].slice(0, 3)} ${p.y}`;
  if (mode === "date") return day;
  return `${day} · ${pad(p.h)}:${pad(p.min)}`;
}

export function DateField({
  label,
  value,
  onChange,
  colors,
  mode = "date",
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  colors: Palette;
  mode?: DateMode;
}) {
  const [open, setOpen] = useState(false);
  const initial = parseParts(value, mode);
  const [cursor, setCursor] = useState(initial);
  const cells = useMemo(() => {
    const lead = mondayOffset(cursor.y, cursor.m);
    const count = daysInMonth(cursor.y, cursor.m);
    const out: (number | null)[] = Array.from({ length: lead }, () => null);
    for (let i = 1; i <= count; i++) out.push(i);
    while (out.length % 7) out.push(null);
    return out;
  }, [cursor.y, cursor.m]);

  const openModal = () => {
    setCursor(parseParts(value, mode));
    setOpen(true);
  };

  const shiftMonth = (delta: number) => {
    setCursor((c) => {
      const dt = new Date(c.y, c.m - 1 + delta, 1);
      const max = daysInMonth(dt.getFullYear(), dt.getMonth() + 1);
      return { ...c, y: dt.getFullYear(), m: dt.getMonth() + 1, d: Math.min(c.d, max) };
    });
  };

  return (
    <View style={{ marginBottom: 12 }}>
      <Text style={{ fontSize: 12, fontWeight: "600", marginBottom: 6, color: colors.muted }}>{label}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={label}
        onPress={openModal}
        style={{
          minHeight: 48,
          borderRadius: 12,
          borderWidth: 1,
          borderColor: colors.line,
          backgroundColor: colors.card,
          paddingHorizontal: 12,
          flexDirection: "row",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <Text style={{ color: value ? colors.ink : colors.muted, fontSize: 16 }}>
          {value ? prettyValue(value, mode) : "Choose a date"}
        </Text>
        <Ionicons name="calendar-outline" size={18} color={colors.brand} />
      </Pressable>
      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable
          style={{ flex: 1, backgroundColor: colors.overlay, justifyContent: "flex-end" }}
          onPress={() => setOpen(false)}
        >
          <Pressable
            onPress={() => undefined}
            style={{ backgroundColor: colors.card, borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 16, maxHeight: "88%" }}
          >
            <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
              <Pressable onPress={() => shiftMonth(-1)} accessibilityLabel="Previous month" hitSlop={8}>
                <Ionicons name="chevron-back" size={22} color={colors.ink} />
              </Pressable>
              <Text style={{ color: colors.ink, fontWeight: "800", fontSize: 16 }}>
                {MONTHS[cursor.m - 1]} {cursor.y}
              </Text>
              <Pressable onPress={() => shiftMonth(1)} accessibilityLabel="Next month" hitSlop={8}>
                <Ionicons name="chevron-forward" size={22} color={colors.ink} />
              </Pressable>
            </View>
            {mode !== "month" ? (
              <>
                <View style={{ flexDirection: "row", marginBottom: 6 }}>
                  {DOW.map((d) => (
                    <Text key={d} style={{ flex: 1, textAlign: "center", color: colors.muted, fontSize: 12, fontWeight: "700" }}>
                      {d}
                    </Text>
                  ))}
                </View>
                <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                  {cells.map((day, i) => {
                    const on = day === cursor.d;
                    return (
                      <Pressable
                        key={i}
                        disabled={!day}
                        onPress={() => day && setCursor((c) => ({ ...c, d: day }))}
                        style={{
                          width: "14.28%",
                          aspectRatio: 1,
                          alignItems: "center",
                          justifyContent: "center",
                          borderRadius: 10,
                          backgroundColor: on ? colors.brand : "transparent",
                        }}
                      >
                        <Text style={{ color: on ? "#fff" : day ? colors.ink : "transparent", fontWeight: "700" }}>{day || ""}</Text>
                      </Pressable>
                    );
                  })}
                </View>
              </>
            ) : null}
            {mode === "datetime" ? (
              <>
                <Text style={{ color: colors.muted, fontSize: 12, fontWeight: "700", marginTop: 12, marginBottom: 6 }}>Hour</Text>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6 }}>
                  {HOURS.map((h) => (
                    <Pressable
                      key={h}
                      onPress={() => setCursor((c) => ({ ...c, h }))}
                      style={{
                        paddingHorizontal: 10,
                        paddingVertical: 8,
                        borderRadius: 10,
                        backgroundColor: cursor.h === h ? colors.brand : colors.bg,
                      }}
                    >
                      <Text style={{ color: cursor.h === h ? "#fff" : colors.ink, fontWeight: "700" }}>{pad(h)}</Text>
                    </Pressable>
                  ))}
                </ScrollView>
                <Text style={{ color: colors.muted, fontSize: 12, fontWeight: "700", marginTop: 12, marginBottom: 6 }}>Minute</Text>
                <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
                  {MINUTES.map((min) => (
                    <Pressable
                      key={min}
                      onPress={() => setCursor((c) => ({ ...c, min }))}
                      style={{
                        paddingHorizontal: 10,
                        paddingVertical: 8,
                        borderRadius: 10,
                        backgroundColor: cursor.min === min ? colors.brand : colors.bg,
                      }}
                    >
                      <Text style={{ color: cursor.min === min ? "#fff" : colors.ink, fontWeight: "700" }}>{pad(min)}</Text>
                    </Pressable>
                  ))}
                </View>
              </>
            ) : null}
            <View style={{ flexDirection: "row", gap: 8, marginTop: 16 }}>
              <Pressable
                onPress={() => setOpen(false)}
                style={{ flex: 1, minHeight: 48, borderRadius: 14, borderWidth: 1, borderColor: colors.line, alignItems: "center", justifyContent: "center" }}
              >
                <Text style={{ color: colors.brand, fontWeight: "700" }}>Cancel</Text>
              </Pressable>
              <Pressable
                onPress={() => {
                  onChange(formatValue(cursor, mode));
                  setOpen(false);
                }}
                style={{ flex: 1, minHeight: 48, borderRadius: 14, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" }}
              >
                <Text style={{ color: "#fff", fontWeight: "700" }}>Use date</Text>
              </Pressable>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

export function TimeField({
  label,
  value,
  onChange,
  colors,
  presets,
}: {
  label: string;
  value: string;
  onChange: (hhmmss: string) => void;
  colors: Palette;
  presets?: string[];
}) {
  const [open, setOpen] = useState(false);
  const shown = fmtTime(value);
  const hour = Number((shown || "08:00").slice(0, 2)) || 0;
  const minute = Number((shown || "08:00").slice(3, 5)) || 0;
  const [h, setH] = useState(hour);
  const [min, setMin] = useState(minute);
  return (
    <View style={{ marginBottom: 12 }}>
      <Text style={{ fontSize: 12, fontWeight: "600", marginBottom: 6, color: colors.muted }}>{label}</Text>
      {presets && presets.length ? (
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
          {presets.map((p) => (
            <Pressable
              key={p}
              onPress={() => onChange(p.length === 5 ? `${p}:00` : p)}
              style={{
                paddingHorizontal: 10,
                paddingVertical: 6,
                borderRadius: 999,
                borderWidth: 1,
                borderColor: fmtTime(value) === fmtTime(p) ? colors.brand : colors.line,
                backgroundColor: fmtTime(value) === fmtTime(p) ? colors.brand : colors.card,
              }}
            >
              <Text style={{ color: fmtTime(value) === fmtTime(p) ? "#fff" : colors.ink, fontWeight: "700", fontSize: 12 }}>{fmtTime(p)}</Text>
            </Pressable>
          ))}
        </View>
      ) : null}
      <Pressable
        accessibilityRole="button"
        onPress={() => {
          setH(hour);
          setMin(minute);
          setOpen(true);
        }}
        style={{
          minHeight: 48,
          borderRadius: 12,
          borderWidth: 1,
          borderColor: colors.line,
          backgroundColor: colors.card,
          paddingHorizontal: 12,
          flexDirection: "row",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <Text style={{ color: colors.ink, fontSize: 16 }}>{shown}</Text>
        <Ionicons name="time-outline" size={18} color={colors.brand} />
      </Pressable>
      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable style={{ flex: 1, backgroundColor: colors.overlay, justifyContent: "flex-end" }} onPress={() => setOpen(false)}>
          <Pressable onPress={() => undefined} style={{ backgroundColor: colors.card, borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 16 }}>
            <Text style={{ color: colors.ink, fontWeight: "800", fontSize: 16, marginBottom: 12 }}>{label}</Text>
            <Text style={{ color: colors.muted, fontSize: 12, fontWeight: "700", marginBottom: 6 }}>Hour</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6 }}>
              {HOURS.map((n) => (
                <Pressable
                  key={n}
                  onPress={() => setH(n)}
                  style={{ paddingHorizontal: 10, paddingVertical: 8, borderRadius: 10, backgroundColor: h === n ? colors.brand : colors.bg }}
                >
                  <Text style={{ color: h === n ? "#fff" : colors.ink, fontWeight: "700" }}>{pad(n)}</Text>
                </Pressable>
              ))}
            </ScrollView>
            <Text style={{ color: colors.muted, fontSize: 12, fontWeight: "700", marginTop: 12, marginBottom: 6 }}>Minute</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
              {MINUTES.map((n) => (
                <Pressable
                  key={n}
                  onPress={() => setMin(n)}
                  style={{ paddingHorizontal: 10, paddingVertical: 8, borderRadius: 10, backgroundColor: min === n ? colors.brand : colors.bg }}
                >
                  <Text style={{ color: min === n ? "#fff" : colors.ink, fontWeight: "700" }}>{pad(n)}</Text>
                </Pressable>
              ))}
            </View>
            <View style={{ flexDirection: "row", gap: 8, marginTop: 16 }}>
              <Pressable
                onPress={() => setOpen(false)}
                style={{ flex: 1, minHeight: 48, borderRadius: 14, borderWidth: 1, borderColor: colors.line, alignItems: "center", justifyContent: "center" }}
              >
                <Text style={{ color: colors.brand, fontWeight: "700" }}>Cancel</Text>
              </Pressable>
              <Pressable
                onPress={() => {
                  onChange(`${pad(h)}:${pad(min)}:00`);
                  setOpen(false);
                }}
                style={{ flex: 1, minHeight: 48, borderRadius: 14, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" }}
              >
                <Text style={{ color: "#fff", fontWeight: "700" }}>Use time</Text>
              </Pressable>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}
