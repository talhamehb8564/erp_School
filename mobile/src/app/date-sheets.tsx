import { useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, examApi, onlineExamApi } from "../api/services";
import { Badge, Btn, Card, Chip, Empty, QueryState, Screen, Title, confirmAction, friendlyMessage, useAsync } from "../ui/kit";
import { DateField, TimeField } from "../ui/dates";
import { can } from "../lib/permissions";
import { RequireAuth } from "../ui/gate";

type Entry = {
  subjectId?: string;
  subjectName?: string;
  examDate?: string;
  startTime?: string;
  endTime?: string;
  examType?: string;
  sectionName?: string;
  notes?: string;
  onlineExamId?: string;
  onlineExamTitle?: string;
};

export default function DateSheets() {
  return (
    <RequireAuth>
      <DateSheetScreen />
    </RequireAuth>
  );
}

function DateSheetScreen() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const manage = can.manageDateSheets(user?.role);
  const sessions = useAsync(() => examApi.sessions());
  const classes = useAsync(() => academicApi.classes());
  const [sessionId, setSessionId] = useState("");
  const [classId, setClassId] = useState("");
  const list = useAsync(
    () => examApi.dateSheets({ sessionId: sessionId || undefined, classId: classId || undefined }),
    [sessionId, classId],
  );
  const [create, setCreate] = useState(false);
  const [examSessionId, setExamSessionId] = useState("");
  const [newClassId, setNewClassId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [examDate, setExamDate] = useState("");
  const [startTime, setStartTime] = useState("09:00:00");
  const [endTime, setEndTime] = useState("11:00:00");
  const [examType, setExamType] = useState<"THEORY" | "ONLINE">("THEORY");
  const [onlineExamId, setOnlineExamId] = useState("");
  const [msg, setMsg] = useState("");
  const [err, setErr] = useState("");
  const subjects = useAsync(
    () => (newClassId ? academicApi.classSubjects(newClassId) : Promise.resolve([])),
    [newClassId],
    Boolean(newClassId),
  );
  const online = useAsync(() => onlineExamApi.list().catch(() => []), []);
  const linked = (online.data || []).filter(
    (ex) => String(ex.classId || "") === newClassId && String(ex.subjectId || "") === subjectId,
  );

  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Date sheet" subtitle="Internal exam schedule. Theory is the default. Online rows link an existing exam and do not start it." colors={colors} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
        <Chip label="All exams" selected={!sessionId} onPress={() => setSessionId("")} colors={colors} />
        {(sessions.data || []).map((s) => (
          <Chip key={s.id} label={s.name} selected={s.id === sessionId} onPress={() => setSessionId(s.id)} colors={colors} />
        ))}
      </View>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
        <Chip label="All classes" selected={!classId} onPress={() => setClassId("")} colors={colors} />
        {(classes.data || []).map((c) => (
          <Chip key={c.id} label={c.name} selected={c.id === classId} onPress={() => setClassId(c.id)} colors={colors} />
        ))}
      </View>
      {manage ? (
        <Btn label={create ? "Close form" : "New date sheet"} colors={colors} kind="ghost" onPress={() => setCreate((v) => !v)} />
      ) : null}
      {create && manage ? (
        <Card colors={colors}>
          <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Save draft</Text>
          <Text style={{ color: colors.muted, marginBottom: 6 }}>Exam</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            {(sessions.data || []).map((s) => (
              <Chip key={s.id} label={s.name} selected={s.id === examSessionId} onPress={() => setExamSessionId(s.id)} colors={colors} />
            ))}
          </View>
          <Text style={{ color: colors.muted, marginBottom: 6 }}>Class</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            {(classes.data || []).map((c) => (
              <Chip key={c.id} label={c.name} selected={c.id === newClassId} onPress={() => { setNewClassId(c.id); setSubjectId(""); }} colors={colors} />
            ))}
          </View>
          <Text style={{ color: colors.muted, marginBottom: 6 }}>Subject</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            {(subjects.data || []).map((s) => (
              <Chip key={s.id} label={s.name} selected={s.id === subjectId} onPress={() => setSubjectId(s.id)} colors={colors} />
            ))}
          </View>
          <DateField label="Exam date" value={examDate} onChange={setExamDate} colors={colors} />
          <TimeField label="Start" value={startTime} onChange={setStartTime} colors={colors} />
          <TimeField label="End" value={endTime} onChange={setEndTime} colors={colors} />
          <View style={{ flexDirection: "row", gap: 8, marginBottom: 8 }}>
            <Chip label="Theory" selected={examType === "THEORY"} onPress={() => { setExamType("THEORY"); setOnlineExamId(""); }} colors={colors} />
            <Chip label="Online" selected={examType === "ONLINE"} onPress={() => setExamType("ONLINE")} colors={colors} />
          </View>
          {examType === "ONLINE" ? (
            <View style={{ marginBottom: 8 }}>
              <Text style={{ color: colors.muted, marginBottom: 6 }}>Link existing online exam</Text>
              {linked.length ? linked.map((ex) => (
                <Chip key={String(ex.id)} label={String(ex.title || "Online exam")} selected={onlineExamId === String(ex.id)} onPress={() => setOnlineExamId(String(ex.id))} colors={colors} />
              )) : <Text style={{ color: colors.muted }}>Create the online exam first. Saving a date sheet does not start it.</Text>}
            </View>
          ) : null}
          {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
          {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
          <Btn
            label="Save draft"
            colors={colors}
            kind="brass"
            disabled={!examSessionId || !newClassId || !subjectId || !examDate}
            onPress={async () => {
              setErr("");
              setMsg("");
              try {
                if (startTime && endTime && endTime <= startTime) {
                  setErr("End time must be later than start time.");
                  return;
                }
                if (examType === "ONLINE" && !onlineExamId) {
                  setErr("Link an existing online exam, or switch the row back to Theory.");
                  return;
                }
                await examApi.createDateSheet({
                  examSessionId,
                  classId: newClassId,
                  entries: [{
                    subjectId,
                    examDate,
                    startTime,
                    endTime,
                    examType,
                    onlineExamId: examType === "ONLINE" ? onlineExamId || null : null,
                  }],
                });
                setMsg("Date sheet saved as draft.");
                setCreate(false);
                await list.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        </Card>
      ) : null}
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No date sheets" hint="Published schedules for your class appear here." colors={colors} />}>
        {(list.data || []).map((sheet) => {
          const entries = (Array.isArray(sheet.entries) ? sheet.entries : []) as Entry[];
          const id = String(sheet.id || "");
          const published = Boolean(sheet.published);
          const historical = Boolean(sheet.historical);
          return (
            <Card key={id} colors={colors}>
              <View style={{ flexDirection: "row", justifyContent: "space-between", gap: 8 }}>
                <Text style={{ color: colors.ink, fontWeight: "800", flex: 1 }}>{String(sheet.examName || "Exam")} · {String(sheet.className || "")}</Text>
                <Badge value={published ? "Published" : "Draft"} colors={colors} />
              </View>
              {entries.map((row, i) => (
                <View key={i} style={{ paddingVertical: 8, borderTopWidth: i ? 1 : 0, borderTopColor: colors.line }}>
                  <Text style={{ color: colors.ink, fontWeight: "700" }}>{row.subjectName || "Subject"}</Text>
                  <Text style={{ color: colors.muted }}>{row.examDate} · {String(row.startTime || "").slice(0, 5)}–{String(row.endTime || "").slice(0, 5)} · {row.examType === "ONLINE" ? "Online" : "Theory"}</Text>
                  {row.sectionName ? <Text style={{ color: colors.muted }}>{row.sectionName}</Text> : null}
                </View>
              ))}
              {manage && !historical && !published ? (
                <Btn
                  label="Publish"
                  colors={colors}
                  kind="brass"
                  onPress={async () => {
                    if (!(await confirmAction("Publish date sheet", "Families and teachers will see this schedule."))) return;
                    try {
                      await examApi.publishDateSheet(id);
                      await list.reload();
                    } catch (e) {
                      setErr(friendlyMessage(e));
                    }
                  }}
                />
              ) : null}
              {manage && published && !historical ? (
                <Btn
                  label="Unpublish"
                  colors={colors}
                  kind="ghost"
                  onPress={async () => {
                    if (!(await confirmAction("Unpublish date sheet", "It will return to draft and hide from families."))) return;
                    try {
                      await examApi.unpublishDateSheet(id);
                      await list.reload();
                    } catch (e) {
                      setErr(friendlyMessage(e));
                    }
                  }}
                />
              ) : null}
            </Card>
          );
        })}
      </QueryState>
    </Screen>
  );
}
