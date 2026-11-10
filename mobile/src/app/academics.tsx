import { useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi } from "../api/services";
import { Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";

export default function Academics() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const classes = useAsync(() => academicApi.classes());
  const subjects = useAsync(() => academicApi.subjects());
  const [className, setClassName] = useState("");
  const [grade, setGrade] = useState("");
  const [classId, setClassId] = useState("");
  const [sectionName, setSectionName] = useState("");
  const [subjectName, setSubjectName] = useState("");
  const [subjectCode, setSubjectCode] = useState("");
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId));
  const admin = user?.role === "SCHOOL_ADMIN";
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => { void classes.reload(); void subjects.reload(); }} refreshing={classes.loading}>
      <Title title="Academics" subtitle="Classes, sections and subjects" colors={colors} />
      {admin ? (
        <Card colors={colors}>
          <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Add class</Text>
          <Field label="Name" colors={colors} value={className} onChangeText={setClassName} />
          <Field label="Grade (optional)" colors={colors} value={grade} onChangeText={setGrade} />
          <Btn
            label="Create class"
            colors={colors}
            disabled={!className.trim()}
            onPress={async () => {
              setErr("");
              setMsg("");
              try {
                await academicApi.createClass({ name: className.trim(), grade: grade.trim() || undefined });
                setClassName("");
                setGrade("");
                setMsg("Class created.");
                await classes.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        </Card>
      ) : null}
      <QueryState loading={classes.loading} error={classes.error} data={classes.data} onRetry={() => void classes.reload()} colors={colors} empty={<Empty title="No classes" colors={colors} />}>
        {(classes.data || []).map((c) => (
          <Card key={c.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }} onPress={() => setClassId(c.id)}>{c.name}</Text>
            <Text style={{ color: colors.muted }}>{c.grade || "Class"}</Text>
          </Card>
        ))}
      </QueryState>
      {classId ? (
        <Card colors={colors}>
          <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Sections</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            {(sections.data || []).map((s) => (
              <Chip key={s.id} label={s.name} colors={colors} />
            ))}
          </View>
          {admin ? (
            <>
              <Field label="New section name" colors={colors} value={sectionName} onChangeText={setSectionName} />
              <Btn
                label="Add section"
                colors={colors}
                disabled={!sectionName.trim()}
                onPress={async () => {
                  setErr("");
                  try {
                    await academicApi.createSection(classId, sectionName.trim());
                    setSectionName("");
                    await sections.reload();
                  } catch (e) {
                    setErr(friendlyMessage(e));
                  }
                }}
              />
            </>
          ) : null}
        </Card>
      ) : (
        <Text style={{ color: colors.muted, marginBottom: 12 }}>Tap a class to see its sections.</Text>
      )}
      <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Subjects</Text>
      {(subjects.data || []).map((s) => (
        <Text key={s.id} style={{ color: colors.muted, marginBottom: 6 }}>{s.name} ({s.code})</Text>
      ))}
      {admin ? (
        <Card colors={colors}>
          <Field label="Subject name" colors={colors} value={subjectName} onChangeText={setSubjectName} />
          <Field label="Code" colors={colors} value={subjectCode} onChangeText={setSubjectCode} autoCapitalize="characters" />
          <Btn
            label="Create subject"
            colors={colors}
            disabled={!subjectName.trim() || !subjectCode.trim()}
            onPress={async () => {
              setErr("");
              try {
                await academicApi.createSubject({ name: subjectName.trim(), code: subjectCode.trim() });
                setSubjectName("");
                setSubjectCode("");
                await subjects.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        </Card>
      ) : null}
      {msg ? <Text style={{ color: colors.ok }}>{msg}</Text> : null}
      {err ? <Text style={{ color: colors.bad }}>{err}</Text> : null}
    </Screen>
  );
}
