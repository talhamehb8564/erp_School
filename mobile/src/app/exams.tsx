import { useEffect, useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, examApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Empty, Field, QueryState, Screen, Title, useAsync } from "../ui/kit";
import ResultCountdown, { isUpcomingExam, pickResultSession } from "../ui/countdown";
import { num } from "../lib/format";

export default function Exams() {
  const { user } = useSession();
  const { colors } = useAppearance();
  if (user?.role === "STUDENT" || user?.role === "PARENT") return <FamilyExams />;
  return <StaffExams />;
}

function FamilyExams() {
  const { user, childId } = useSession();
  const { colors } = useAppearance();
  const sessions = useAsync(() => examApi.sessions());
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const sid = childId || kids.data?.[0]?.id;
  const [sessionId, setSessionId] = useState("");
  const mine = useAsync(() => {
    if (user?.role === "STUDENT") return examApi.myResult(sessionId);
    return examApi.studentResult(sessionId, sid!);
  }, [sessionId, user?.role, sid], Boolean(sessionId) && (user?.role === "STUDENT" || Boolean(sid)));
  useEffect(() => {
    if (!sessionId && sessions.data?.length) setSessionId(pickResultSession(sessions.data));
  }, [sessions.data, sessionId]);
  const selected = (sessions.data || []).find((s) => s.id === sessionId);
  const pending = Boolean(mine.data?.pending);
  const sealed = mine.data?.announced === false;
  return (
    <Screen colors={colors} onRefresh={() => void mine.reload()} refreshing={mine.loading}>
      <Title title="Marks & results" subtitle="Mid and Final are independent. Marks stay sealed until announceAt." colors={colors} />
      {(sessions.data || []).map((s) => (
        <Text key={s.id} onPress={() => setSessionId(s.id)} style={{ color: s.id === sessionId ? colors.brand : colors.ink, marginBottom: 8, fontWeight: s.id === sessionId ? "800" : "500" }}>
          {s.name} {isUpcomingExam(s.announceAt) ? "(countdown)" : s.published ? "(published)" : ""}
        </Text>
      ))}
      {selected && isUpcomingExam(selected.announceAt) ? (
        <ResultCountdown announceAt={String(mine.data?.announceAt || selected.announceAt)} sessionName={selected.name} colors={colors} onDone={() => void mine.reload()} />
      ) : null}
      <QueryState loading={mine.loading} error={mine.error} data={mine.data} onRetry={() => void mine.reload()} colors={colors}>
        {sealed ? (
          <Empty title="Sealed until the release clock" hint="The marksheet appears when announceAt elapses and this student’s marks are complete." colors={colors} />
        ) : pending ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>Result is not available yet</Text>
            <Text style={{ color: colors.muted, marginTop: 6 }}>{String(mine.data?.message || "Some subject marks have not been entered.")}</Text>
          </Card>
        ) : mine.data ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(mine.data.studentName || user?.fullName)}</Text>
            <Text style={{ color: colors.muted }}>
              {num(mine.data.obtainedMarks)}/{num(mine.data.totalMarks)} · {String(mine.data.grade || "—")} · {num(mine.data.percentage)}%
            </Text>
            {((mine.data.subjects as { subjectName?: string; obtainedMarks?: number; totalMarks?: number }[]) || []).map((r, i) => (
              <Text key={i} style={{ color: colors.ink, marginTop: 6 }}>
                {r.subjectName} · {r.obtainedMarks}/{r.totalMarks}
              </Text>
            ))}
          </Card>
        ) : (
          <Empty title="Select a session" colors={colors} />
        )}
      </QueryState>
    </Screen>
  );
}

function StaffExams() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const sessions = useAsync(() => examApi.sessions());
  const classes = useAsync(() => academicApi.classes());
  const [sessionId, setSessionId] = useState("");
  const [name, setName] = useState("Mid Term");
  const selected = (sessions.data || []).find((s) => s.id === sessionId);
  return (
    <Screen colors={colors} onRefresh={() => void sessions.reload()} refreshing={sessions.loading}>
      <Title title="Exam sessions" subtitle="Set release time. Teachers can still fill missing marks after the clock." colors={colors} />
      {(user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL") ? (
        <Card colors={colors}>
          <Field label="New session name" colors={colors} value={name} onChangeText={setName} />
          <Btn
            label="Create session"
            colors={colors}
            onPress={async () => {
              await examApi.createSession({ name, classId: classes.data?.[0]?.id });
              await sessions.reload();
            }}
          />
        </Card>
      ) : null}
      {(sessions.data || []).map((s) => (
        <Card key={s.id} colors={colors}>
          <Text style={{ color: colors.ink, fontWeight: "800" }} onPress={() => setSessionId(s.id)}>{s.name}</Text>
          <Badge value={s.published ? "PUBLISHED" : "DRAFT"} colors={colors} />
          {s.announceAt ? <Text style={{ color: colors.muted }}>Release {s.announceAt}</Text> : null}
        </Card>
      ))}
      {selected && (user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL") ? (
        <View style={{ gap: 8 }}>
          <Btn label="Publish results" colors={colors} onPress={async () => { await examApi.publish(selected.id); await sessions.reload(); }} />
        </View>
      ) : null}
    </Screen>
  );
}
