import { useEffect, useState } from "react";
import { Text, View } from "react-native";
import { useLocalSearchParams, router } from "expo-router";
import { useAppearance } from "../../lib/appearance";
import { onlineExamApi } from "../../api/services";
import { Btn, Card, Chip, Empty, Field, Screen, Title, friendlyMessage, useAsync } from "../../ui/kit";
import { RequireAuth } from "../../ui/gate";

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

export default function TakeOnlineExam() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useAppearance();
  const paper = useAsync(() => onlineExamApi.start(id!), [id], Boolean(id));
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
  const closed = paper.data?.attemptStatus && paper.data.attemptStatus !== "IN_PROGRESS";

  return (
    <RequireAuth roles={["STUDENT"]}>
      <Screen colors={colors} onRefresh={() => void paper.reload()} refreshing={paper.loading}>
        <Title
          title={String(paper.data?.title || "Online exam")}
          subtitle={closed ? "This attempt is finished." : left ? `Time left ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}` : "Waiting"}
          colors={colors}
        />
        {paper.error ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{friendlyMessage(paper.error)}</Text> : null}
        {!paper.data && !paper.loading ? <Empty title="Cannot open exam" hint="It may be outside the scheduled window." colors={colors} /> : null}
        {closed ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(paper.data?.grade || paper.data?.attemptStatus)}</Text>
            <Text style={{ color: colors.muted }}>{String(paper.data?.obtainedMarks ?? "—")} / {String(paper.data?.totalMarks ?? "—")}</Text>
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
        {!closed && questions.length ? (
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
