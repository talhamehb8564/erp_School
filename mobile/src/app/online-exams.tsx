import { useState } from "react";
import { Text, View } from "react-native";
import { router } from "expo-router";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, onlineExamApi } from "../api/services";
import { Badge, Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { RequireAuth } from "../ui/gate";
import { pretty } from "../lib/format";

export default function OnlineExams() {
  const { user } = useSession();
  if (user?.role === "STUDENT") return <StudentExams />;
  return <StaffOnline />;
}

function StudentExams() {
  const { colors } = useAppearance();
  const list = useAsync(() => onlineExamApi.mine());
  return (
    <RequireAuth roles={["STUDENT"]}>
      <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
        <Title title="Online exams" subtitle="Start only during the scheduled window. Answers persist on the server." colors={colors} />
        <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No online exams" hint="When a teacher schedules one for your class, it appears here." colors={colors} />}>
          {(list.data || []).map((e) => (
            <Card key={String(e.id)} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(e.title)}</Text>
              <Text style={{ color: colors.muted }}>{String(e.startAt || "")} · {String(e.durationMinutes || "—")} min</Text>
              <Badge value={String(e.status || "")} colors={colors} />
              <Btn label="Open" colors={colors} onPress={() => router.push(`/online-exam/${e.id}` as never)} />
            </Card>
          ))}
        </QueryState>
      </Screen>
    </RequireAuth>
  );
}

function StaffOnline() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => onlineExamApi.list());
  const classes = useAsync(() => academicApi.classes());
  const subjects = useAsync(() => academicApi.subjects());
  const [title, setTitle] = useState("");
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [startAt, setStartAt] = useState("");
  const [duration, setDuration] = useState("40");
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId));
  const canWrite = user?.role === "TEACHER" || user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";
  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER"]}>
      <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
        <Title title="Online exams" subtitle="Create a draft, then schedule. Questions can be added on web for long papers." colors={colors} />
        {canWrite ? (
          <Card colors={colors}>
            <Field label="Title" colors={colors} value={title} onChangeText={setTitle} />
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Class</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {(classes.data || []).map((c) => (
                <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); }} colors={colors} />
              ))}
            </View>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {(sections.data || []).map((s) => (
                <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => setSectionId(s.id)} colors={colors} />
              ))}
            </View>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {(subjects.data || []).map((s) => (
                <Chip key={s.id} label={s.name} selected={subjectId === s.id} onPress={() => setSubjectId(s.id)} colors={colors} />
              ))}
            </View>
            <Field label="Start (ISO, e.g. 2026-10-26T09:00:00+05:00)" colors={colors} value={startAt} onChangeText={setStartAt} autoCapitalize="none" />
            <Field label="Duration minutes" colors={colors} value={duration} onChangeText={setDuration} keyboardType="numeric" />
            {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
            {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
            <Btn
              label="Create draft"
              colors={colors}
              disabled={!title.trim() || !classId || !sectionId || !subjectId || !startAt.trim()}
              onPress={async () => {
                setErr("");
                setMsg("");
                try {
                  const created = await onlineExamApi.create({
                    title: title.trim(),
                    classId,
                    sectionId,
                    subjectId,
                    startAt: startAt.trim(),
                    durationMinutes: Number(duration),
                  });
                  setMsg("Draft created. Add questions on web, then schedule.");
                  setTitle("");
                  if (typeof created.id === "string") {
                    /* keep list */
                  }
                  await list.reload();
                } catch (e) {
                  setErr(friendlyMessage(e));
                }
              }}
            />
          </Card>
        ) : null}
        <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No online exams" colors={colors} />}>
          {(list.data || []).map((e) => (
            <Card key={String(e.id)} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(e.title)}</Text>
              <Text style={{ color: colors.muted }}>{String(e.startAt || "")} · {pretty(String(e.status || ""))}</Text>
              {canWrite && String(e.status) === "DRAFT" ? (
                <Btn
                  label="Schedule"
                  kind="ghost"
                  colors={colors}
                  onPress={async () => {
                    try {
                      await onlineExamApi.schedule(String(e.id));
                      await list.reload();
                    } catch (err) {
                      setErr(friendlyMessage(err));
                    }
                  }}
                />
              ) : null}
            </Card>
          ))}
        </QueryState>
      </Screen>
    </RequireAuth>
  );
}
