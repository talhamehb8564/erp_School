import { useState } from "react";
import { Text, View } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, confirmAction, friendlyMessage, useAsync, useDebounced } from "../ui/kit";
import { can } from "../lib/permissions";
import { RequireAuth } from "../ui/gate";
import type { StudentStatus } from "../lib/types";

const REASONS = [
  { id: "LEFT_SCHOOL", label: "Left school" },
  { id: "PARENT_REQUEST", label: "Parent request" },
  { id: "UNPAID_FEES", label: "Unpaid fees" },
  { id: "GRADUATED", label: "Graduated" },
  { id: "OTHER", label: "Other" },
];

export default function Students() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const manage = can.inactivateStudents(user?.role);
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [status, setStatus] = useState<StudentStatus | "">("ACTIVE");
  const [reason, setReason] = useState("LEFT_SCHOOL");
  const [note, setNote] = useState("");
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");
  const dq = useDebounced(q, 350);
  const classes = useAsync(() => academicApi.classes());
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId));
  const roster = useAsync(
    () => (classId ? studentApi.roster(classId, sectionId || undefined) : Promise.resolve([])),
    [classId, sectionId],
    Boolean(classId) && manage,
  );
  const list = useAsync(
    () => studentApi.list(classId || undefined, sectionId || undefined, page, dq || undefined, status || undefined),
    [dq, page, classId, sectionId, status],
  );

  const runInactivate = async (id: string, name: string) => {
    if (!(await confirmAction("Inactivate student?", `${name} stays in the database as ${reason.replaceAll("_", " ").toLowerCase()}. Records are not deleted.`, "Inactivate"))) return;
    setErr("");
    setMsg("");
    try {
      await studentApi.inactivate(id, reason, note || undefined);
      setMsg("Student inactivated.");
      await list.reload();
    } catch (e) {
      setErr(friendlyMessage(e));
    }
  };

  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "PRINCIPAL", "TEACHER", "ACCOUNT_OFFICER"]}>
      <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
        <Title title="Students" subtitle={`${list.data?.totalElements ?? 0} records · not deleted on inactivate`} colors={colors} />
        <Field label="Search" colors={colors} value={q} onChangeText={(t) => { setQ(t); setPage(0); }} placeholder="Name, roll, CNIC" />
        <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Class</Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
          <Chip label="All" selected={!classId} onPress={() => { setClassId(""); setSectionId(""); setPage(0); }} colors={colors} />
          {(classes.data || []).map((c) => (
            <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); setPage(0); }} colors={colors} />
          ))}
        </View>
        {classId ? (
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
            <Chip label="All sections" selected={!sectionId} onPress={() => { setSectionId(""); setPage(0); }} colors={colors} />
            {(sections.data || []).map((s) => (
              <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => { setSectionId(s.id); setPage(0); }} colors={colors} />
            ))}
          </View>
        ) : null}
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
          {(["", "ACTIVE", "INACTIVE", "GRADUATED", "LEFT_SCHOOL"] as const).map((s) => (
            <Chip key={s || "all"} label={s || "All statuses"} selected={status === s} onPress={() => { setStatus(s); setPage(0); }} colors={colors} />
          ))}
        </View>
        {manage ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>Inactivate reason</Text>
            <Text style={{ color: colors.muted, marginBottom: 8 }}>Required by the API. Bulk uses class/section, not an ID list.</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {REASONS.map((r) => (
                <Chip key={r.id} label={r.label} selected={reason === r.id} onPress={() => setReason(r.id)} colors={colors} />
              ))}
            </View>
            <Field label="Note (required for Other)" colors={colors} value={note} onChangeText={setNote} />
            {classId ? (
              <Btn
                label={`Inactivate ${roster.data?.length ?? 0} active student(s) in this class${sectionId ? " / section" : ""}`}
                kind="danger"
                colors={colors}
                disabled={!roster.data?.length}
                onPress={async () => {
                  const n = roster.data?.length ?? 0;
                  if (!(await confirmAction(
                    "Bulk inactivate?",
                    `${n} active student(s) will be set inactive / left / graduated. Accounts are disabled. Nothing is deleted.`,
                    "Inactivate all",
                  ))) return;
                  setErr("");
                  setMsg("");
                  try {
                    const out = await studentApi.inactivateBulk({
                      classId,
                      sectionId: sectionId || undefined,
                      reason,
                      note: note || undefined,
                    });
                    setMsg(`Inactivated ${out.inactivated ?? n} student(s).`);
                    await list.reload();
                    await roster.reload();
                  } catch (e) {
                    setErr(friendlyMessage(e));
                  }
                }}
              />
            ) : (
              <Text style={{ color: colors.muted, fontSize: 12 }}>Pick a class to bulk-inactivate its active roster.</Text>
            )}
          </Card>
        ) : null}
        {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
        {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
        <QueryState loading={list.loading} error={list.error} data={list.data?.content} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No students found" colors={colors} />}>
          {(list.data?.content || []).map((s) => (
            <Card key={s.id} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{s.fullName || `${s.firstName || ""} ${s.lastName || ""}`}</Text>
              <Text style={{ color: colors.muted }}>{s.rollNumber} · {s.className || classes.data?.find((c) => c.id === s.classId)?.name || "—"}</Text>
              <Badge value={s.status} colors={colors} />
              {manage ? (
                <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
                  {s.status === "ACTIVE" ? (
                    <Btn label="Inactivate" kind="danger" colors={colors} onPress={() => runInactivate(s.id, s.fullName || s.rollNumber || "this student")} />
                  ) : (
                    <Btn
                      label="Reactivate"
                      kind="ghost"
                      colors={colors}
                      onPress={async () => {
                        if (!(await confirmAction("Reactivate student?", `${s.fullName || "This student"} will be set ACTIVE again.`, "Reactivate"))) return;
                        setErr("");
                        try {
                          await studentApi.reactivate(s.id, note || undefined);
                          setMsg("Student reactivated.");
                          await list.reload();
                        } catch (e) {
                          setErr(friendlyMessage(e));
                        }
                      }}
                    />
                  )}
                </View>
              ) : null}
            </Card>
          ))}
        </QueryState>
        <View style={{ flexDirection: "row", gap: 8 }}>
          {page > 0 ? <Btn label="Previous" kind="ghost" colors={colors} onPress={() => setPage((p) => Math.max(0, p - 1))} /> : null}
          {list.data && !list.data.last ? <Btn label="Next page" kind="ghost" colors={colors} onPress={() => setPage((p) => p + 1)} /> : null}
        </View>
      </Screen>
    </RequireAuth>
  );
}
