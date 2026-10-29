import { useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, announcementApi } from "../api/services";
import { Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { fmtDate } from "../lib/format";

const AUDIENCE = ["ALL", "TEACHERS", "PARENTS", "STUDENTS"];

export default function Announcements() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => announcementApi.list());
  const classes = useAsync(() => academicApi.classes(), [], user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [audience, setAudience] = useState("ALL");
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [err, setErr] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId));
  const canPost = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Announcements" subtitle="Published notices for this school" colors={colors} />
      {canPost ? (
        <Card colors={colors}>
          <Field label="Title" colors={colors} value={title} onChangeText={setTitle} />
          <Field label="Body" colors={colors} value={body} onChangeText={setBody} multiline />
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Audience</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            {AUDIENCE.map((a) => (
              <Chip key={a} label={a} selected={audience === a} onPress={() => setAudience(a)} colors={colors} />
            ))}
          </View>
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Optional class</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            <Chip label="Whole school" selected={!classId} onPress={() => { setClassId(""); setSectionId(""); }} colors={colors} />
            {(classes.data || []).map((c) => (
              <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); }} colors={colors} />
            ))}
          </View>
          {classId ? (
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {(sections.data || []).map((s) => (
                <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => setSectionId(s.id)} colors={colors} />
              ))}
            </View>
          ) : null}
          {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
          <Btn
            label="Publish"
            colors={colors}
            disabled={!title.trim() || !body.trim()}
            onPress={async () => {
              setErr("");
              try {
                await announcementApi.create({
                  title,
                  body,
                  audience,
                  classId: classId || undefined,
                  sectionId: sectionId || undefined,
                });
                setTitle("");
                setBody("");
                await list.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        </Card>
      ) : null}
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No announcements" hint="Nothing has been published yet." colors={colors} />}>
        {(list.data || []).map((a) => (
          <Card key={a.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{a.title}</Text>
            <Text style={{ color: colors.muted }}>{fmtDate(a.createdAt)} · {a.audience || "ALL"}</Text>
            {a.body ? <Text style={{ color: colors.ink, marginTop: 6 }}>{a.body}</Text> : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
