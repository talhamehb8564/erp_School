import { useState } from "react";
import { Text } from "react-native";
import { useSession } from "../lib/session";
import { pickImage } from "../lib/picker";
import { useAppearance } from "../lib/appearance";
import { fileApi, homeworkApi } from "../api/services";
import { Btn, Card, Empty, Field, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { fmtDate } from "../lib/format";

export default function Homework() {
  const { user, childId } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => homeworkApi.list(undefined, undefined, 40));
  const [notes, setNotes] = useState("");
  const [active, setActive] = useState<string | null>(null);
  const family = user?.role === "STUDENT" || user?.role === "PARENT";
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Homework" subtitle="Assignments from teachers" colors={colors} />
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No homework" colors={colors} />}>
        {(list.data || []).map((h) => (
          <Card key={h.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{h.title}</Text>
            <Text style={{ color: colors.muted }}>{h.className} · {h.subjectName} · due {fmtDate(h.dueDate)}</Text>
            {h.description ? <Text style={{ color: colors.muted, marginTop: 6 }}>{h.description}</Text> : null}
            {family ? (
              <Btn
                label={active === h.id ? "Submit this" : "Open submit"}
                kind="ghost"
                colors={colors}
                onPress={() => setActive(h.id)}
              />
            ) : null}
          </Card>
        ))}
      </QueryState>
      {family && active ? (
        <Card colors={colors}>
          <Field label="Notes" colors={colors} value={notes} onChangeText={setNotes} />
          <Btn
            label="Upload photo / slip and submit"
            colors={colors}
            onPress={async () => {
              const a = await pickImage();
              if (!a) return;
              const up = await fileApi.upload(a);
              await homeworkApi.submit(active, {
                studentId: user?.role === "PARENT" ? childId || undefined : undefined,
                fileUrl: up.url,
                notes,
              });
              setActive(null);
              setNotes("");
              await list.reload();
            }}
          />
        </Card>
      ) : null}
    </Screen>
  );
}
