import { useMemo, useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, remarkApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, confirmAction, friendlyMessage, useAsync } from "../ui/kit";
import { DateField } from "../ui/dates";
import { REMARK_CATEGORIES, pretty, today } from "../lib/format";
import { RequireAuth } from "../ui/gate";

export default function Remarks() {
  const { user } = useSession();
  const staff = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL" || user?.role === "TEACHER";
  return <RequireAuth>{staff ? <StaffRemarks /> : <FamilyRemarks />}</RequireAuth>;
}

function FamilyRemarks() {
  const { user, childId, setChildId } = useSession();
  const { colors } = useAppearance();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const sid = user?.role === "PARENT" ? childId || kids.data?.[0]?.id : undefined;
  const list = useAsync(
    () => remarkApi.list(user?.role === "STUDENT" ? {} : { studentId: sid }),
    [user?.role, sid],
    user?.role === "STUDENT" || Boolean(sid),
  );
  const rows = list.data?.content || [];
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Remarks" subtitle="Notes from teachers and school staff." colors={colors} />
      {user?.role === "PARENT" ? (
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
          {(kids.data || []).map((k) => (
            <Chip
              key={k.id}
              label={`${k.fullName || k.rollNumber || "Child"}${k.status && k.status !== "ACTIVE" ? " · inactive" : ""}`}
              selected={k.id === sid}
              onPress={() => setChildId(k.id)}
              colors={colors}
            />
          ))}
        </View>
      ) : null}
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No remarks" colors={colors} />}>
        {rows.map((r) => (
          <Card key={String(r.id)} colors={colors}>
            <Badge value={pretty(String(r.category || ""))} colors={colors} />
            <Text style={{ color: colors.ink, marginTop: 6 }}>{String(r.body || "")}</Text>
            <Text style={{ color: colors.muted, marginTop: 6 }}>{String(r.remarkDate || "")}</Text>
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}

function StaffRemarks() {
  const { colors } = useAppearance();
  const classes = useAsync(() => academicApi.classes());
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [category, setCategory] = useState("GENERAL");
  const [body, setBody] = useState("");
  const [date, setDate] = useState(today());
  const [picked, setPicked] = useState<Record<string, boolean>>({});
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId));
  const roster = useAsync(
    () => (classId && sectionId ? studentApi.roster(classId, sectionId) : Promise.resolve([])),
    [classId, sectionId],
    Boolean(classId && sectionId),
  );
  const history = useAsync(
    () => (classId && sectionId ? remarkApi.list({ classId, sectionId }) : Promise.resolve({ content: [] as Record<string, unknown>[] })),
    [classId, sectionId],
    Boolean(classId && sectionId),
  );
  const selected = useMemo(() => Object.entries(picked).filter(([, v]) => v).map(([id]) => id), [picked]);
  return (
    <Screen colors={colors} onRefresh={() => { void history.reload(); void roster.reload(); }} refreshing={history.loading}>
      <Title title="Remarks" subtitle="One student or the whole section. Parents are notified." colors={colors} />
      <Card colors={colors}>
        <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Class</Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
          {(classes.data || []).map((c) => (
            <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); setPicked({}); }} colors={colors} />
          ))}
        </View>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
          {(sections.data || []).map((s) => (
            <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => { setSectionId(s.id); setPicked({}); }} colors={colors} />
          ))}
        </View>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
          {REMARK_CATEGORIES.map((c) => (
            <Chip key={c.id} label={c.label} selected={category === c.id} onPress={() => setCategory(c.id)} colors={colors} />
          ))}
        </View>
        <DateField label="Date" value={date} onChange={setDate} colors={colors} />
        <Field label="Remark" colors={colors} value={body} onChangeText={setBody} multiline />
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
          {(roster.data || []).map((s) => (
            <Chip
              key={s.id}
              label={s.fullName || s.rollNumber || s.id.slice(0, 6)}
              selected={Boolean(picked[s.id])}
              onPress={() => setPicked((cur) => ({ ...cur, [s.id]: !cur[s.id] }))}
              colors={colors}
            />
          ))}
        </View>
        <Text style={{ color: colors.muted, marginBottom: 8 }}>
          {selected.length ? `${selected.length} selected` : "None selected — saves for the whole section"}
        </Text>
        {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
        {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
        <Btn
          label="Save remark"
          colors={colors}
          disabled={!body.trim() || !classId || !sectionId}
          onPress={async () => {
            setErr("");
            setMsg("");
            try {
              await remarkApi.create({
                studentIds: selected.length ? selected : undefined,
                classId: selected.length ? undefined : classId,
                sectionId: selected.length ? undefined : sectionId,
                category,
                body: body.trim(),
                remarkDate: date,
              });
              setMsg("Saved");
              setBody("");
              setPicked({});
              await history.reload();
            } catch (e) {
              setErr(friendlyMessage(e));
            }
          }}
        />
      </Card>
      <QueryState loading={history.loading} error={history.error} data={history.data} onRetry={() => void history.reload()} colors={colors} empty={<Empty title="No remarks yet" colors={colors} />}>
        {(history.data?.content || []).map((r) => (
          <Card key={String(r.id)} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(r.studentName || "Student")}</Text>
            <Badge value={pretty(String(r.category || ""))} colors={colors} />
            <Text style={{ color: colors.ink, marginTop: 6 }}>{String(r.body || "")}</Text>
            <Text style={{ color: colors.muted, marginTop: 6 }}>{String(r.remarkDate || "")}</Text>
            {r.mine ? (
              <Btn
                label="Delete"
                kind="ghost"
                colors={colors}
                onPress={async () => {
                  if (!(await confirmAction("Delete remark", "Remove this remark?"))) return;
                  await remarkApi.remove(String(r.id));
                  await history.reload();
                }}
              />
            ) : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
