import { useEffect, useState } from "react";
import { Text, View } from "react-native";
import type { Palette } from "../lib/theme";
import { Card } from "./kit";
import type { ExamSession } from "../lib/types";

function pad(n: number) {
  return String(n).padStart(2, "0");
}

export default function ResultCountdown({
  announceAt,
  sessionName,
  colors,
  onDone,
}: {
  announceAt?: string;
  sessionName?: string;
  colors: Palette;
  onDone?: () => void;
}) {
  const [left, setLeft] = useState(0);
  useEffect(() => {
    if (!announceAt) return;
    const tick = () => setLeft(Math.max(0, Math.floor((new Date(announceAt).getTime() - Date.now()) / 1000)));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [announceAt]);
  useEffect(() => {
    if (left === 0 && announceAt && new Date(announceAt).getTime() <= Date.now()) onDone?.();
  }, [left, announceAt, onDone]);
  if (left <= 0) return null;
  const d = Math.floor(left / 86400);
  const h = Math.floor((left % 86400) / 3600);
  const m = Math.floor((left % 3600) / 60);
  const s = left % 60;
  const label = sessionName?.trim() || "Result";
  return (
    <Card colors={colors}>
      <Text style={{ color: colors.brand, fontWeight: "800", fontSize: 12 }}>RESULTS SEALED</Text>
      <Text style={{ color: colors.ink, fontSize: 18, fontWeight: "800", marginTop: 4 }}>{label}</Text>
      <Text style={{ color: colors.muted, marginTop: 4 }}>
        {label} will be available in: {d} Days {h} Hours {m} Minutes
      </Text>
      <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
        {[
          [pad(d), "Days"],
          [pad(h), "Hours"],
          [pad(m), "Min"],
          [pad(s), "Sec"],
        ].map(([v, l]) => (
          <View key={l} style={{ flex: 1, backgroundColor: colors.bg, borderRadius: 12, padding: 8, alignItems: "center" }}>
            <Text style={{ color: colors.ink, fontWeight: "800", fontSize: 18 }}>{v}</Text>
            <Text style={{ color: colors.muted, fontSize: 10 }}>{l}</Text>
          </View>
        ))}
      </View>
    </Card>
  );
}

export function isUpcomingExam(announceAt?: string) {
  if (!announceAt) return false;
  const at = new Date(announceAt).getTime();
  return !Number.isNaN(at) && at > Date.now();
}

export function pickResultSession(rows: ExamSession[]) {
  if (!rows.length) return "";
  const upcomingFinal = rows.find((s) => /final/i.test(s.name) && isUpcomingExam(s.announceAt));
  if (upcomingFinal) return upcomingFinal.id;
  const anyUpcoming = rows.find((s) => isUpcomingExam(s.announceAt));
  if (anyUpcoming) return anyUpcoming.id;
  const publishedFinal = rows.find((s) => /final/i.test(s.name) && s.published);
  if (publishedFinal) return publishedFinal.id;
  return (rows.find((s) => s.published) || rows[0]).id;
}
