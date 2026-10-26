import { useEffect, useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, examApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { ProgressWheel } from "../ui/wheel";
import ResultCountdown, { isUpcomingExam, pickResultSession } from "../ui/countdown";
import { num } from "../lib/format";
import { RequireAuth } from "../ui/gate";

export default function Exams() {
  const { user } = useSession();
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
  const subjects = ((mine.data?.subjects as { subjectName?: string; obtainedMarks?: number; totalMarks?: number; grade?: string }[]) || []);
  const pct = num(mine.data?.percentage);
  const total = num(mine.data?.totalMarks);
  return (
    <RequireAuth>
      <Screen colors={colors} onRefresh={() => void mine.reload()} refreshing={mine.loading}>
        <Title title="Marks & results" subtitle="Mid and Final are independent. Marks stay sealed until announceAt." colors={colors} />
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
          {(sessions.data || []).map((s) => (
            <Chip
              key={s.id}
              label={`${s.name}${isUpcomingExam(s.announceAt) ? " · soon" : s.published ? " · out" : ""}`}
              selected={s.id === sessionId}
              onPress={() => setSessionId(s.id)}
              colors={colors}
            />
          ))}
        </View>
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
          ) : mine.data && !isUpcomingExam(selected?.announceAt) ? (
            <Card colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(mine.data.studentName || user?.fullName)}</Text>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginTop: 8 }}>
                <ProgressWheel
                  value={pct}
                  label="Overall"
                  hint={total ? `${num(mine.data.obtainedMarks)} / ${total} · ${String(mine.data.grade || "—")}` : "No totals"}
                  empty={total === 0}
                  colors={colors}
                />
              </View>
              {subjects.map((r, i) => (
                <View key={i} style={{ paddingVertical: 8, borderTopWidth: 1, borderTopColor: colors.line }}>
                  <Text style={{ color: colors.ink, fontWeight: "700" }}>{r.subjectName}</Text>
                  <Text style={{ color: colors.muted }}>{r.obtainedMarks}/{r.totalMarks}{r.grade ? ` · ${r.grade}` : ""}</Text>
                </View>
              ))}
            </Card>
          ) : (
            <Empty title="Select a session" colors={colors} />
          )}
        </QueryState>
      </Screen>
    </RequireAuth>
  );
}

function StaffExams() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const sessions = useAsync(() => examApi.sessions());
  const classes = useAsync(() => academicApi.classes());
  const subjects = useAsync(() => academicApi.subjects());
  const [sessionId, setSessionId] = useState("");
  const [name, setName] = useState("Mid Term");
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [studentId, setStudentId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [total, setTotal] = useState("100");
  const [obtained, setObtained] = useState("");
  const [roll, setRoll] = useState("");
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId));
  const students = useAsync(
    () => (classId ? studentApi.list(classId, sectionId || undefined, 0) : Promise.resolve({ content: [], last: true, totalElements: 0, totalPages: 0, page: 0, size: 20, first: true })),
    [classId, sectionId],
    Boolean(classId),
  );
  const selected = (sessions.data || []).find((s) => s.id === sessionId);
  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER"]}>
      <Screen colors={colors} onRefresh={() => void sessions.reload()} refreshing={sessions.loading}>
        <Title title="Exam sessions" subtitle="Teachers enter marks only for assigned subjects. Backend rejects the rest." colors={colors} />
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
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
          {(sessions.data || []).map((s) => (
            <Chip key={s.id} label={s.name} selected={s.id === sessionId} onPress={() => setSessionId(s.id)} colors={colors} />
          ))}
        </View>
        {selected ? <Badge value={selected.published ? "PUBLISHED" : "DRAFT"} colors={colors} /> : null}
        {selected ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Enter marks</Text>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Class</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {(classes.data || []).map((c) => (
                <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); setStudentId(""); }} colors={colors} />
              ))}
            </View>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {(sections.data || []).map((s) => (
                <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => setSectionId(s.id)} colors={colors} />
              ))}
            </View>
            <Field label="Search by roll (optional)" colors={colors} value={roll} onChangeText={setRoll} />
            {(students.data?.content || []).slice(0, 12).map((st) => (
              <Chip
                key={st.id}
                label={`${st.fullName || st.rollNumber} · ${st.rollNumber || ""}`}
                selected={studentId === st.id}
                onPress={() => setStudentId(st.id)}
                colors={colors}
              />
            ))}
            <Text style={{ color: colors.muted, fontSize: 12, marginTop: 8, marginBottom: 6 }}>Subject</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {(subjects.data || []).map((s) => (
                <Chip key={s.id} label={s.name} selected={subjectId === s.id} onPress={() => setSubjectId(s.id)} colors={colors} />
              ))}
            </View>
            <Field label="Total marks" colors={colors} value={total} onChangeText={setTotal} keyboardType="numeric" />
            <Field label="Obtained marks" colors={colors} value={obtained} onChangeText={setObtained} keyboardType="numeric" />
            {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
            {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
            <Btn
              label="Save result"
              colors={colors}
              disabled={!sessionId || !studentId || !subjectId}
              onPress={async () => {
                setErr("");
                setMsg("");
                try {
                  await examApi.upsertResult(sessionId, {
                    studentId,
                    subjectId,
                    totalMarks: Number(total),
                    obtainedMarks: Number(obtained),
                  });
                  setMsg("Marks saved.");
                } catch (e) {
                  setErr(friendlyMessage(e));
                }
              }}
            />
            {roll.trim() && sessionId ? (
              <Btn
                label="Find by roll"
                kind="ghost"
                colors={colors}
                onPress={async () => {
                  setErr("");
                  try {
                    const row = await examApi.byRoll(sessionId, roll.trim());
                    setMsg(`Found ${String(row.studentName || row.rollNumber || "student")}`);
                    if (typeof row.studentId === "string") setStudentId(row.studentId);
                  } catch (e) {
                    setErr(friendlyMessage(e));
                  }
                }}
              />
            ) : null}
          </Card>
        ) : null}
        {selected && (user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL") ? (
          <View style={{ gap: 8 }}>
            <Btn label="Publish results" colors={colors} onPress={async () => { await examApi.publish(selected.id); await sessions.reload(); }} />
          </View>
        ) : null}
      </Screen>
    </RequireAuth>
  );
}
