import { useMemo, useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, studentApi, userApi } from "../api/services";
import { Btn, Card, Chip, Empty, QueryState, Screen, Title, confirmAction, friendlyMessage, useAsync } from "../ui/kit";
import { TimeField } from "../ui/dates";
import { dayName, fmtTime, toHms } from "../lib/format";
import { can } from "../lib/permissions";
import { RequireAuth } from "../ui/gate";
import type { TimetableSlot } from "../lib/types";
import type { Palette } from "../lib/theme";

function parseWorkingDays(raw?: string) {
  if (!raw) return [1, 2, 3, 4, 5];
  return raw
    .split(/[,\s]+/)
    .map((n) => Number(n))
    .filter((n) => n >= 1 && n <= 7);
}

function SlotList({
  slots,
  colors,
  canEdit,
  onEdit,
  onDelete,
}: {
  slots: TimetableSlot[];
  colors: Palette;
  canEdit: boolean;
  onEdit?: (s: TimetableSlot) => void;
  onDelete?: (s: TimetableSlot) => void;
}) {
  const grouped = new Map<number, TimetableSlot[]>();
  for (const s of slots) {
    const day = s.dayOfWeek || 0;
    const rows = grouped.get(day) || [];
    rows.push(s);
    grouped.set(day, rows);
  }
  const days = [...grouped.keys()].sort((a, b) => a - b);
  if (!days.length) return <Empty title="No lectures" hint="Add a lecture or apply a day pattern." colors={colors} />;
  return (
    <>
      {days.map((d) => (
        <View key={d} style={{ marginBottom: 8 }}>
          <Text style={{ color: colors.brand, fontWeight: "800", marginBottom: 6 }}>{dayName(d)}</Text>
          {(grouped.get(d) || []).map((s) => (
            <Card key={s.id} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{s.subjectName || "Lesson"}</Text>
              <Text style={{ color: colors.muted }}>
                {fmtTime(s.startTime)}–{fmtTime(s.endTime)} · {s.className} {s.sectionName || ""} {s.teacherName ? `· ${s.teacherName}` : ""}
              </Text>
              {canEdit ? (
                <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
                  <Btn label="Edit" kind="ghost" colors={colors} onPress={() => onEdit?.(s)} />
                  <Btn label="Delete" kind="danger" colors={colors} onPress={() => onDelete?.(s)} />
                </View>
              ) : null}
            </Card>
          ))}
        </View>
      ))}
    </>
  );
}

export default function Timetable() {
  const { user, childId } = useSession();
  const { colors } = useAppearance();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const child = (kids.data || []).find((k) => k.id === (childId || kids.data?.[0]?.id));
  const editor = can.manageTimetable(user?.role);
  const picker = can.viewClassTimetable(user?.role);
  const classes = useAsync(() => academicApi.classes(), [], picker);
  const settings = useAsync(() => academicApi.timetableSettings(), [], picker || editor);
  const teachers = useAsync(() => userApi.list({ role: "TEACHER", status: "ACTIVE" }), [], editor);
  const subjects = useAsync(() => academicApi.subjects(), [], editor);
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId) && picker);
  const working = parseWorkingDays(settings.data?.workingDays);
  const timePresets = (settings.data?.suggestedSlots || []).map((s) => toHms(s.startTime));

  const viewQuery = useAsync(() => {
    if (user?.role === "TEACHER") return academicApi.teacherTimetable();
    if (user?.role === "STUDENT") return academicApi.myTimetable();
    if (user?.role === "PARENT" && child?.classId && child?.sectionId) {
      return academicApi.timetable(child.classId, child.sectionId);
    }
    if (picker && classId && sectionId) return academicApi.timetable(classId, sectionId);
    return Promise.resolve([]);
  }, [user?.role, child?.classId, child?.sectionId, classId, sectionId, picker], !picker || Boolean(classId && sectionId) || user?.role === "TEACHER" || user?.role === "STUDENT" || user?.role === "PARENT");

  const [form, setForm] = useState({
    id: "",
    subjectId: "",
    teacherUserId: "",
    dayOfWeek: 1,
    startTime: "08:00:00",
    endTime: "08:45:00",
  });
  const [applyFrom, setApplyFrom] = useState(1);
  const [applyDays, setApplyDays] = useState<number[]>([]);
  const [replace, setReplace] = useState(true);
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");

  const teacherOptions = teachers.data?.content || [];
  const slots = viewQuery.data || [];
  const applySource = useMemo(() => slots.filter((s) => s.dayOfWeek === applyFrom), [slots, applyFrom]);

  const saveSlot = async () => {
    setErr("");
    setMsg("");
    if (!classId || !sectionId || !form.subjectId || !form.teacherUserId) {
      setErr("Pick class, section, subject, and teacher.");
      return;
    }
    const body = {
      classId,
      sectionId,
      subjectId: form.subjectId,
      teacherUserId: form.teacherUserId,
      dayOfWeek: form.dayOfWeek,
      startTime: toHms(form.startTime),
      endTime: toHms(form.endTime),
    };
    try {
      if (form.id) await academicApi.updateSlot(form.id, body);
      else await academicApi.createSlot(body);
      setMsg(form.id ? "Lecture updated." : "Lecture added.");
      setForm((f) => ({ ...f, id: "" }));
      await viewQuery.reload();
    } catch (e) {
      setErr(friendlyMessage(e));
    }
  };

  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "STUDENT", "PARENT"]}>
      <Screen colors={colors} onRefresh={() => void viewQuery.reload()} refreshing={viewQuery.loading}>
        <Title
          title="Timetable"
          subtitle={editor ? "Create, edit, delete, or apply lectures for a class." : "Lecture slots from the academic calendar."}
          colors={colors}
        />
        {picker ? (
          <>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Class</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              {(classes.data || []).map((c) => (
                <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); }} colors={colors} />
              ))}
            </View>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Section</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              {(sections.data || []).map((s) => (
                <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => setSectionId(s.id)} colors={colors} />
              ))}
            </View>
          </>
        ) : null}
        {picker && !(classId && sectionId) ? (
          <Empty title="Pick a class and section" hint="Lectures load from GET /api/v1/timetable." colors={colors} />
        ) : (
        <QueryState
          loading={viewQuery.loading}
          error={viewQuery.error}
          data={slots}
          onRetry={() => void viewQuery.reload()}
          colors={colors}
          empty={
            <Empty
              title="No lectures"
              hint={editor ? "Use the form below to add the first lecture." : "Ask admin to publish a timetable for this class."}
              colors={colors}
            />
          }
        >
          <SlotList
            slots={slots}
            colors={colors}
            canEdit={editor && Boolean(classId && sectionId)}
            onEdit={(s) =>
              setForm({
                id: s.id,
                subjectId: s.subjectId || "",
                teacherUserId: s.teacherUserId || "",
                dayOfWeek: s.dayOfWeek || 1,
                startTime: toHms(s.startTime),
                endTime: toHms(s.endTime),
              })
            }
            onDelete={async (s) => {
              if (!(await confirmAction("Delete lecture?", `${s.subjectName || "This lecture"} will be removed from the timetable.`, "Delete"))) return;
              setErr("");
              try {
                await academicApi.deleteSlot(s.id);
                await viewQuery.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        </QueryState>
        )}
        {editor && classId && sectionId ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>{form.id ? "Edit lecture" : "Add lecture"}</Text>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Day</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              {working.map((d) => (
                <Chip key={d} label={dayName(d).slice(0, 3)} selected={form.dayOfWeek === d} onPress={() => setForm((f) => ({ ...f, dayOfWeek: d }))} colors={colors} />
              ))}
            </View>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Subject</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              {(subjects.data || []).map((s) => (
                <Chip key={s.id} label={s.name} selected={form.subjectId === s.id} onPress={() => setForm((f) => ({ ...f, subjectId: s.id }))} colors={colors} />
              ))}
            </View>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Teacher</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              {teacherOptions.map((t) => (
                <Chip
                  key={t.id}
                  label={t.fullName || t.username}
                  selected={form.teacherUserId === t.id}
                  onPress={() => setForm((f) => ({ ...f, teacherUserId: t.id }))}
                  colors={colors}
                />
              ))}
            </View>
            <TimeField label="Starts" value={form.startTime} onChange={(v) => setForm((f) => ({ ...f, startTime: v }))} colors={colors} presets={timePresets} />
            <TimeField label="Ends" value={form.endTime} onChange={(v) => setForm((f) => ({ ...f, endTime: v }))} colors={colors} presets={(settings.data?.suggestedSlots || []).map((s) => toHms(s.endTime))} />
            {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
            {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
            <Btn label={form.id ? "Save lecture" : "Add lecture"} colors={colors} onPress={saveSlot} />
            {form.id ? <Btn label="Cancel edit" kind="ghost" colors={colors} onPress={() => setForm((f) => ({ ...f, id: "" }))} /> : null}
          </Card>
        ) : null}
        {editor && classId && sectionId ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Apply a day to other days</Text>
            <Text style={{ color: colors.muted, marginBottom: 8 }}>Copies every lecture from the source day onto the days you tick. The server rejects teacher or class clashes.</Text>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Copy from</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              {working.map((d) => (
                <Chip key={d} label={dayName(d).slice(0, 3)} selected={applyFrom === d} onPress={() => setApplyFrom(d)} colors={colors} />
              ))}
            </View>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Apply to</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              {working.filter((d) => d !== applyFrom).map((d) => (
                <Chip
                  key={d}
                  label={dayName(d).slice(0, 3)}
                  selected={applyDays.includes(d)}
                  onPress={() => setApplyDays((cur) => (cur.includes(d) ? cur.filter((x) => x !== d) : [...cur, d]))}
                  colors={colors}
                />
              ))}
            </View>
            <Chip label={replace ? "Replace existing lectures on those days" : "Keep existing, add on top"} selected={replace} onPress={() => setReplace((v) => !v)} colors={colors} />
            <View style={{ height: 10 }} />
            <Btn
              label={`Apply ${applySource.length} lecture(s)`}
              colors={colors}
              disabled={!applySource.length || !applyDays.length}
              onPress={async () => {
                if (!(await confirmAction("Apply timetable pattern?", `Copy ${applySource.length} lecture(s) from ${dayName(applyFrom)} onto ${applyDays.map(dayName).join(", ")}.`, "Apply"))) return;
                setErr("");
                setMsg("");
                try {
                  await academicApi.applyPattern({
                    classId,
                    sectionId,
                    days: applyDays,
                    replace,
                    lectures: applySource.map((s) => ({
                      subjectId: s.subjectId,
                      teacherUserId: s.teacherUserId,
                      startTime: toHms(s.startTime),
                      endTime: toHms(s.endTime),
                    })),
                  });
                  setMsg("Pattern applied.");
                  await viewQuery.reload();
                } catch (e) {
                  setErr(friendlyMessage(e));
                }
              }}
            />
          </Card>
        ) : null}
      </Screen>
    </RequireAuth>
  );
}
