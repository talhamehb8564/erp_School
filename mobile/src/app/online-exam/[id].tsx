import { useEffect, useState } from "react";
import { Text, View } from "react-native";
import { useLocalSearchParams, router } from "expo-router";
import { useAppearance } from "../../lib/appearance";
import { useSession } from "../../lib/session";
import { onlineExamApi } from "../../api/services";
import { Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, confirmAction, friendlyMessage, useAsync } from "../../ui/kit";
import { RequireAuth } from "../../ui/gate";
import { EXCLUDE_REASONS, pretty, statusReasonLabel } from "../../lib/format";

type Q = {
  id: string;
  questionType?: string;
  prompt?: string;
  marks?: number;
  optionA?: string;
  optionB?: string;
  optionC?: string;
  optionD?: string;
};

export default function OnlineExamDetail() {
  const { user } = useSession();
  if (user?.role === "STUDENT") return <StudentPaper />;
  return <StaffExam />;
}

function StudentPaper() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useAppearance();
  const paper = useAsync(async () => {
    try {
      return await onlineExamApi.start(id!);
    } catch (e) {
      try {
        return await onlineExamApi.result(id!);
      } catch {
        throw e;
      }
    }
  }, [id], Boolean(id));
  const [answers, setAnswers] = useState<Record<string, { optionChoice?: string; textAnswer?: string }>>({});
  const [left, setLeft] = useState(0);
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");

  useEffect(() => {
    const sec = Number(paper.data?.remainingSeconds || 0);
    setLeft(sec);
  }, [paper.data?.remainingSeconds]);

  useEffect(() => {
    if (left <= 0) return;
    const t = setInterval(() => setLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(t);
  }, [left]);

  useEffect(() => {
    if (left !== 0 || paper.data?.attemptStatus !== "IN_PROGRESS" || !id) return;
    if (!Number(paper.data?.remainingSeconds)) return;
    void onlineExamApi.autoSubmit(id).then(() => paper.reload()).catch(() => undefined);
  }, [left, id, paper.data?.attemptStatus, paper.data?.remainingSeconds]);

  const questions = ((paper.data?.questions as Q[]) || []);
  const excluded = Boolean(paper.data?.excluded) || paper.data?.attemptStatus === "EXCLUDED";
  const closed = paper.data?.attemptStatus && paper.data.attemptStatus !== "IN_PROGRESS";

  return (
    <RequireAuth roles={["STUDENT"]}>
      <Screen colors={colors} onRefresh={() => void paper.reload()} refreshing={paper.loading}>
        <Title
          title={String(paper.data?.title || "Online exam")}
          subtitle={excluded ? "Not assigned" : closed ? "This attempt is finished." : left ? `Time left ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}` : "Waiting"}
          colors={colors}
        />
        {paper.error ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{friendlyMessage(paper.error)}</Text> : null}
        {!paper.data && !paper.loading ? <Empty title="Cannot open exam" hint="It may be outside the scheduled window." colors={colors} /> : null}
        {excluded ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>Excluded</Text>
            <Text style={{ color: colors.muted, marginTop: 6 }}>
              {statusReasonLabel(String(paper.data?.excludeReason || ""), null) || "You are not assigned this exam."}
            </Text>
            <Btn label="Back" kind="ghost" colors={colors} onPress={() => router.back()} />
          </Card>
        ) : closed ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(paper.data?.grade || paper.data?.attemptStatus)}</Text>
            <Text style={{ color: colors.muted }}>{String(paper.data?.obtainedMarks ?? "—")} / {String(paper.data?.totalMarks ?? "—")}</Text>
            {paper.data?.unattempted ? <Text style={{ color: colors.muted, marginTop: 6 }}>No attempt — recorded as 0.</Text> : null}
            <Btn label="Back" kind="ghost" colors={colors} onPress={() => router.back()} />
          </Card>
        ) : (
          questions.map((q) => (
            <Card key={q.id} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "700" }}>{q.prompt}</Text>
              <Text style={{ color: colors.muted, marginBottom: 8 }}>{q.questionType} · {q.marks} marks</Text>
              {q.questionType === "MCQ" ? (
                <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
                  {(["A", "B", "C", "D"] as const).map((opt) => {
                    const label =
                      opt === "A" ? q.optionA : opt === "B" ? q.optionB : opt === "C" ? q.optionC : q.optionD;
                    if (!label) return null;
                    return (
                      <Chip
                        key={opt}
                        label={`${opt}. ${label}`}
                        selected={answers[q.id]?.optionChoice === opt}
                        onPress={() => setAnswers((a) => ({ ...a, [q.id]: { optionChoice: opt } }))}
                        colors={colors}
                      />
                    );
                  })}
                </View>
              ) : (
                <Field
                  label="Answer"
                  colors={colors}
                  value={answers[q.id]?.textAnswer || ""}
                  onChangeText={(t) => setAnswers((a) => ({ ...a, [q.id]: { textAnswer: t } }))}
                  multiline
                />
              )}
            </Card>
          ))
        )}
        {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
        {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
        {!excluded && !closed && questions.length ? (
          <>
            <Btn
              label="Save answers"
              kind="ghost"
              colors={colors}
              onPress={async () => {
                setErr("");
                try {
                  const body = Object.entries(answers).map(([questionId, v]) => ({ questionId, ...v }));
                  await onlineExamApi.saveAnswers(id!, body);
                  setMsg("Saved.");
                } catch (e) {
                  setErr(friendlyMessage(e));
                }
              }}
            />
            <Btn
              label="Submit exam"
              colors={colors}
              onPress={async () => {
                setErr("");
                try {
                  const body = Object.entries(answers).map(([questionId, v]) => ({ questionId, ...v }));
                  await onlineExamApi.saveAnswers(id!, body);
                  await onlineExamApi.submit(id!);
                  setMsg("Submitted.");
                  await paper.reload();
                } catch (e) {
                  setErr(friendlyMessage(e));
                }
              }}
            />
          </>
        ) : null}
      </Screen>
    </RequireAuth>
  );
}

function StaffExam() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useAppearance();
  const exam = useAsync(() => onlineExamApi.get(id!), [id], Boolean(id));
  const results = useAsync(() => onlineExamApi.results(id!), [id], Boolean(id));
  const recipients = useAsync(() => onlineExamApi.recipients(id!), [id], Boolean(id));
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const [picked, setPicked] = useState<Record<string, boolean>>({});
  const [exclusions, setExclusions] = useState<Record<string, string>>({});
  const students = ((recipients.data?.students as {
    studentId: string;
    studentName?: string;
    assigned?: boolean;
    excluded?: boolean;
    excludeReason?: string;
  }[]) || []);

  useEffect(() => {
    const next: Record<string, boolean> = {};
    const ex: Record<string, string> = {};
    students.forEach((s) => {
      next[s.studentId] = Boolean(s.assigned) && !s.excluded;
      if (s.excluded) ex[s.studentId] = s.excludeReason || "OTHER";
    });
    setPicked(next);
    setExclusions(ex);
  }, [recipients.data]);

  const status = String(exam.data?.status || "");

  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER"]}>
      <Screen colors={colors} onRefresh={() => { void exam.reload(); void results.reload(); void recipients.reload(); }} refreshing={exam.loading}>
        <Title title={String(exam.data?.title || "Online exam")} subtitle={pretty(status)} colors={colors} />
        {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
        {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
        {status === "DRAFT" ? (
          <Btn
            label="Schedule"
            colors={colors}
            onPress={async () => {
              if (!(await confirmAction("Schedule exam", "Assigned students and parents will be notified."))) return;
              try {
                await onlineExamApi.schedule(id!);
                setMsg("Scheduled");
                await exam.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        ) : null}
        {status === "CLOSED" ? (
          <Btn
            label="Publish results"
            colors={colors}
            onPress={async () => {
              if (!(await confirmAction("Publish results", "Eligible students who did not attempt receive 0. Excluded students stay excluded."))) return;
              try {
                await onlineExamApi.publishResults(id!);
                setMsg("Published");
                await exam.reload();
                await results.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        ) : null}
        {status && status !== "LIVE" ? (
          <Btn
            label="Delete exam"
            kind="danger"
            colors={colors}
            onPress={async () => {
              const attempts = Number(exam.data?.attemptCount || 0);
              const msg = status === "RESULTS_PUBLISHED" || attempts > 0
                ? `Delete “${exam.data?.title || "this exam"}”? This permanently removes the exam, student attempts, and published results.`
                : `Delete “${exam.data?.title || "this exam"}”? This cannot be undone.`;
              if (!(await confirmAction("Delete exam", msg))) return;
              try {
                await onlineExamApi.remove(id!);
                router.back();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        ) : null}
        <Card colors={colors}>
          <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Recipients</Text>
          {students.map((s) => (
            <View key={s.studentId} style={{ marginBottom: 10 }}>
              <Chip
                label={s.studentName || s.studentId.slice(0, 8)}
                selected={Boolean(picked[s.studentId]) && !exclusions[s.studentId]}
                onPress={() => setPicked((cur) => ({ ...cur, [s.studentId]: !cur[s.studentId] }))}
                colors={colors}
              />
              <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginTop: 6 }}>
                <Chip
                  label="Not excluded"
                  selected={!exclusions[s.studentId]}
                  onPress={() => setExclusions((cur) => { const n = { ...cur }; delete n[s.studentId]; return n; })}
                  colors={colors}
                />
                {EXCLUDE_REASONS.map((r) => (
                  <Chip
                    key={r.id}
                    label={r.label}
                    selected={exclusions[s.studentId] === r.id}
                    onPress={() => {
                      setExclusions((cur) => ({ ...cur, [s.studentId]: r.id }));
                      setPicked((cur) => ({ ...cur, [s.studentId]: false }));
                    }}
                    colors={colors}
                  />
                ))}
              </View>
            </View>
          ))}
          <Btn
            label="Save recipients"
            kind="ghost"
            colors={colors}
            onPress={async () => {
              try {
                const selected = Object.entries(picked).filter(([sid, v]) => v && !exclusions[sid]).map(([sid]) => sid);
                await onlineExamApi.saveRecipients(id!, {
                  restrictToSelected: true,
                  studentIds: selected,
                  exclusions: Object.entries(exclusions).map(([studentId, reason]) => ({ studentId, reason })),
                });
                setMsg("Recipients saved");
                await recipients.reload();
                await results.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        </Card>
        <QueryState loading={results.loading} error={results.error} data={results.data} onRetry={() => void results.reload()} colors={colors} empty={<Empty title="No students" colors={colors} />}>
          {(results.data || []).map((row) => (
            <Card key={String(row.studentId)} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(row.studentName || "Student")}</Text>
              <Text style={{ color: colors.muted }}>
                {row.excluded
                  ? `Excluded — ${statusReasonLabel(String(row.excludeReason || ""), null) || "not assigned"}`
                  : `${pretty(String(row.status || ""))} · ${row.obtainedMarks == null ? "—" : `${row.obtainedMarks} / ${row.totalMarks}`}`}
              </Text>
            </Card>
          ))}
        </QueryState>
      </Screen>
    </RequireAuth>
  );
}
