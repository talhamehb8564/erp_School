import { useState } from "react";
import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { admissionApi } from "../api/services";
import { Badge, Btn, Card, Empty, Field, QueryState, Screen, Title, friendlyMessage, useAsync, useDebounced } from "../ui/kit";
import { pretty } from "../lib/format";

export default function Admissions() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => admissionApi.applications(0));
  const [openId, setOpenId] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const dq = useDebounced(q, 300);
  const [err, setErr] = useState("");
  const detail = useAsync(
    () => (openId ? admissionApi.application(openId) : Promise.resolve(null as unknown as Record<string, unknown>)),
    [openId],
    Boolean(openId),
  );
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  const rows = (list.data?.content || []).filter((a) => {
    if (!dq) return true;
    const blob = `${a.studentName || ""} ${a.guardianName || ""} ${a.cnic || ""} ${a.status || ""}`.toLowerCase();
    return blob.includes(dq.toLowerCase());
  });
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Admissions" subtitle="Submitted applications. CNIC uniqueness is tenant-wide. Public apply is on the school website." colors={colors} />
      <Field label="Search name / CNIC" colors={colors} value={q} onChangeText={setQ} />
      <QueryState loading={list.loading} error={list.error} data={rows} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No submitted applications" hint="New applications arrive from the public admission form." colors={colors} />}>
        {rows.map((a) => (
          <Card key={String(a.id)} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(a.studentName || a.fullName || "Applicant")}</Text>
            <Text style={{ color: colors.muted }}>{String(a.guardianName || "")} · {String(a.cnicMasked || a.cnic || "")} · {String(a.phone || "")}</Text>
            <Badge value={String(a.status)} colors={colors} />
            <Btn label={openId === String(a.id) ? "Hide details" : "Details"} kind="ghost" colors={colors} onPress={() => setOpenId(openId === String(a.id) ? null : String(a.id))} />
            {openId === String(a.id) && detail.data ? (
              <Text style={{ color: colors.muted, marginTop: 8 }}>
                Campus {String(detail.data.campusName || "—")} · {pretty(String(detail.data.status))}
              </Text>
            ) : null}
            {err ? <Text style={{ color: colors.bad }}>{err}</Text> : null}
            {String(a.status) === "SUBMITTED" || String(a.status) === "PENDING" ? (
              <>
                <Btn
                  label="Accept"
                  colors={colors}
                  onPress={async () => {
                    setErr("");
                    try {
                      await admissionApi.setStatus(String(a.id), "ACCEPTED");
                      await list.reload();
                    } catch (e) {
                      setErr(friendlyMessage(e));
                    }
                  }}
                />
                <Btn
                  label="Reject"
                  kind="danger"
                  colors={colors}
                  onPress={async () => {
                    setErr("");
                    try {
                      await admissionApi.setStatus(String(a.id), "REJECTED");
                      await list.reload();
                    } catch (e) {
                      setErr(friendlyMessage(e));
                    }
                  }}
                />
              </>
            ) : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
