import { useMemo, useState } from "react";
import { Image, Pressable, Text, View } from "react-native";
import { Image as ExpoImage } from "expo-image";
import * as Linking from "expo-linking";
import { useSession } from "../lib/session";
import { pickImage, pickImages, type PickedFile } from "../lib/picker";
import { useAppearance } from "../lib/appearance";
import { academicApi, fileApi, homeworkApi } from "../api/services";
import { Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { DateField } from "../ui/dates";
import { fmtDate } from "../lib/format";
import { mediaUrl } from "../lib/media";
import { RequireAuth } from "../ui/gate";

type LocalPic = PickedFile & { key: string };

export default function Homework() {
  const { user } = useSession();
  if (!user) return null;
  const family = user.role === "STUDENT" || user.role === "PARENT";
  return <RequireAuth>{family ? <FamilyHomework /> : <StaffHomework />}</RequireAuth>;
}

function AttachmentRow({ urls, colors }: { urls: { fileUrl: string; fileName?: string }[]; colors: { brand: string; muted: string; ink: string; line: string; card: string } }) {
  if (!urls.length) return null;
  return (
    <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
      {urls.map((a, i) => {
        const uri = mediaUrl(a.fileUrl);
        return (
          <Pressable key={`${a.fileUrl}-${i}`} onPress={() => uri && void Linking.openURL(uri)}>
            <ExpoImage source={{ uri }} style={{ width: 72, height: 72, borderRadius: 10, backgroundColor: colors.line }} contentFit="cover" cachePolicy="memory-disk" />
          </Pressable>
        );
      })}
    </View>
  );
}

function FamilyHomework() {
  const { user, childId } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => homeworkApi.list(undefined, undefined, 40));
  const [notes, setNotes] = useState("");
  const [active, setActive] = useState<string | null>(null);
  const [pic, setPic] = useState<PickedFile | null>(null);
  const [progress, setProgress] = useState("");
  const [err, setErr] = useState("");
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Homework" subtitle="Assignments from teachers" colors={colors} />
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No homework" colors={colors} />}>
        {(list.data || []).map((h) => (
          <Card key={h.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{h.title}</Text>
            <Text style={{ color: colors.muted }}>{h.className} · {h.subjectName} · due {fmtDate(h.dueDate)}</Text>
            {h.description ? <Text style={{ color: colors.muted, marginTop: 6 }}>{h.description}</Text> : null}
            <AttachmentRow urls={(h.attachments || []).filter((a) => a.fileUrl)} colors={colors} />
            <Btn label={active === h.id ? "Cancel submit" : "Submit work"} kind="ghost" colors={colors} onPress={() => { setActive(active === h.id ? null : h.id); setErr(""); }} />
            {active === h.id ? (
              <View style={{ marginTop: 8 }}>
                <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 8 }}>
                  The server stores one file per submission. Teachers can attach several images when they assign work.
                </Text>
                <Field label="Notes" colors={colors} value={notes} onChangeText={setNotes} />
                {pic ? (
                  <View style={{ marginBottom: 8 }}>
                    <Image source={{ uri: pic.uri }} style={{ width: 96, height: 96, borderRadius: 10 }} />
                    <Btn label="Remove photo" kind="ghost" colors={colors} onPress={() => setPic(null)} />
                  </View>
                ) : (
                  <Btn
                    label="Add photo"
                    kind="ghost"
                    colors={colors}
                    onPress={async () => {
                      const a = await pickImage();
                      if (a) setPic(a);
                    }}
                  />
                )}
                {progress ? <Text style={{ color: colors.muted, marginBottom: 8 }}>{progress}</Text> : null}
                {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
                <Btn
                  label="Submit"
                  colors={colors}
                  onPress={async () => {
                    setErr("");
                    try {
                      let fileUrl: string | undefined;
                      if (pic) {
                        setProgress("Uploading…");
                        const up = await fileApi.upload(pic);
                        fileUrl = up.url;
                      }
                      setProgress("Submitting…");
                      await homeworkApi.submit(h.id, {
                        studentId: user?.role === "PARENT" ? childId || undefined : undefined,
                        fileUrl,
                        notes,
                      });
                      setActive(null);
                      setNotes("");
                      setPic(null);
                      setProgress("");
                      await list.reload();
                    } catch (e) {
                      setErr(friendlyMessage(e));
                      setProgress("");
                    }
                  }}
                />
              </View>
            ) : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}

function StaffHomework() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => homeworkApi.list(undefined, undefined, 40));
  const classes = useAsync(() => academicApi.classes());
  const subjects = useAsync(() => academicApi.subjects());
  const assignments = useAsync(
    () => (user?.role === "TEACHER" ? academicApi.teacherAssignments() : Promise.resolve([])),
    [user?.role],
  );
  const canCreate = user?.role === "TEACHER" || user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [dueDate, setDueDate] = useState(new Date().toISOString().slice(0, 10));
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId));
  const [pics, setPics] = useState<LocalPic[]>([]);
  const [progress, setProgress] = useState("");
  const [err, setErr] = useState("");
  const [openId, setOpenId] = useState<string | null>(null);
  const subs = useAsync(
    () => (openId ? homeworkApi.submissions(openId) : Promise.resolve([])),
    [openId],
    Boolean(openId) && (user?.role === "TEACHER" || user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL"),
  );

  const classOptions = useMemo(() => {
    if (user?.role === "TEACHER" && (assignments.data || []).length) {
      const ids = new Set((assignments.data || []).map((a) => a.classId));
      return (classes.data || []).filter((c) => ids.has(c.id));
    }
    return classes.data || [];
  }, [assignments.data, classes.data, user?.role]);

  const subjectOptions = useMemo(() => {
    if (user?.role === "TEACHER" && (assignments.data || []).length) {
      const ids = new Set(
        (assignments.data || []).filter((a) => (!classId || a.classId === classId) && (!sectionId || a.sectionId === sectionId)).map((a) => a.subjectId),
      );
      return (subjects.data || []).filter((s) => ids.has(s.id));
    }
    return subjects.data || [];
  }, [assignments.data, subjects.data, classId, sectionId, user?.role]);

  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Homework" subtitle="Assign work and review submissions" colors={colors} />
      {canCreate ? (
        <Card colors={colors}>
          <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>New assignment</Text>
          <Field label="Title" colors={colors} value={title} onChangeText={setTitle} />
          <Field label="Description" colors={colors} value={description} onChangeText={setDescription} multiline />
          <DateField label="Due date" value={dueDate} onChange={setDueDate} colors={colors} />
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Class</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
            {classOptions.map((c) => (
              <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); }} colors={colors} />
            ))}
          </View>
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Section</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
            {(sections.data || []).map((s) => (
              <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => setSectionId(s.id)} colors={colors} />
            ))}
          </View>
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Subject</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
            {subjectOptions.map((s) => (
              <Chip key={s.id} label={s.name} selected={subjectId === s.id} onPress={() => setSubjectId(s.id)} colors={colors} />
            ))}
          </View>
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Images</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
            {pics.map((p) => (
              <Pressable key={p.key} onPress={() => setPics((rows) => rows.filter((r) => r.key !== p.key))}>
                <Image source={{ uri: p.uri }} style={{ width: 72, height: 72, borderRadius: 10 }} />
              </Pressable>
            ))}
          </View>
          <Btn
            label="Add images"
            kind="ghost"
            colors={colors}
            onPress={async () => {
              const extra = await pickImages(6);
              setPics((rows) => [...rows, ...extra.map((f) => ({ ...f, key: `${f.uri}-${Math.random()}` }))].slice(0, 8));
            }}
          />
          {progress ? <Text style={{ color: colors.muted, marginVertical: 8 }}>{progress}</Text> : null}
          {err ? <Text style={{ color: colors.bad, marginVertical: 8 }}>{err}</Text> : null}
          <Btn
            label="Assign homework"
            colors={colors}
            disabled={!title.trim() || !classId || !sectionId || !subjectId}
            onPress={async () => {
              setErr("");
              try {
                const attachments: { fileName: string; fileUrl: string; contentType: string }[] = [];
                for (let i = 0; i < pics.length; i += 1) {
                  setProgress(`Uploading ${i + 1}/${pics.length}…`);
                  const up = await fileApi.upload(pics[i]);
                  attachments.push({ fileName: pics[i].name, fileUrl: up.url, contentType: pics[i].type });
                }
                setProgress("Saving…");
                await homeworkApi.create({
                  title: title.trim(),
                  description,
                  dueDate,
                  classId,
                  sectionId,
                  subjectId,
                  attachments,
                });
                setTitle("");
                setDescription("");
                setPics([]);
                setProgress("");
                await list.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
                setProgress("");
              }
            }}
          />
        </Card>
      ) : null}
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No homework" colors={colors} />}>
        {(list.data || []).map((h) => (
          <Card key={h.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{h.title}</Text>
            <Text style={{ color: colors.muted }}>{h.className} · {h.subjectName} · due {fmtDate(h.dueDate)}</Text>
            {h.description ? <Text style={{ color: colors.muted, marginTop: 6 }}>{h.description}</Text> : null}
            <AttachmentRow urls={(h.attachments || []).filter((a) => a.fileUrl)} colors={colors} />
            <Btn
              label={openId === h.id ? "Hide submissions" : "View submissions"}
              kind="ghost"
              colors={colors}
              onPress={() => setOpenId(openId === h.id ? null : h.id)}
            />
            {openId === h.id ? (
              <QueryState loading={subs.loading} error={subs.error} data={subs.data} onRetry={() => void subs.reload()} colors={colors} empty={<Text style={{ color: colors.muted }}>No submissions yet</Text>}>
                {(subs.data || []).map((s) => (
                  <View key={s.id} style={{ marginTop: 8 }}>
                    <Text style={{ color: colors.ink }}>{s.notes || "Submission"} · {s.status}</Text>
                    {s.fileUrl ? (
                      <Pressable onPress={() => void Linking.openURL(mediaUrl(s.fileUrl))}>
                        <ExpoImage source={{ uri: mediaUrl(s.fileUrl) }} style={{ width: 96, height: 96, borderRadius: 10, marginTop: 6 }} contentFit="cover" />
                      </Pressable>
                    ) : null}
                  </View>
                ))}
              </QueryState>
            ) : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
