import { useState } from "react";
import { Text, View } from "react-native";
import { Image } from "expo-image";
import { useSession } from "../lib/session";
import { pickImage } from "../lib/picker";
import { useAppearance } from "../lib/appearance";
import { academicApi, feeApi, fileApi, settingsApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, friendlyMessage, useAsync, useDebounced } from "../ui/kit";
import { money, pretty } from "../lib/format";
import { mediaUrl } from "../lib/media";
import { RequireAuth } from "../ui/gate";

export default function Fees() {
  const { user } = useSession();
  if (user?.role === "STUDENT" || user?.role === "PARENT") return <FamilyFees />;
  return <StaffFees />;
}

function FamilyFees() {
  const { user, childId } = useSession();
  const { colors } = useAppearance();
  const kids = useAsync(() => (user?.role === "PARENT" ? studentApi.children() : Promise.resolve([])), [user?.role]);
  const sid = childId || kids.data?.[0]?.id;
  const mine = useAsync(() => {
    if (user?.role === "STUDENT") return feeApi.myChallanSummaries(24);
    return feeApi.childChallanSummaries(sid!, 24);
  }, [user?.role, sid], user?.role === "STUDENT" || Boolean(sid));
  const pay = useAsync(() => settingsApi.get());
  const [proofFor, setProofFor] = useState<string | null>(null);
  const [ref, setRef] = useState("");
  const [err, setErr] = useState("");
  return (
    <RequireAuth roles={["PARENT", "STUDENT"]}>
      <Screen colors={colors} onRefresh={() => void mine.reload()} refreshing={mine.loading}>
        <Title title="Fee challans" subtitle="Pay by uploading a bank slip — no online gateway." colors={colors} />
        {pay.data?.bankName || pay.data?.accountNumber ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>How to pay</Text>
            <Text style={{ color: colors.muted }}>{pay.data.bankName} · {pay.data.accountTitle} {pay.data.accountNumber}</Text>
            {pay.data.paymentInstructions ? <Text style={{ color: colors.muted }}>{pay.data.paymentInstructions}</Text> : null}
          </Card>
        ) : null}
        <QueryState loading={mine.loading} error={mine.error} data={mine.data} onRetry={() => void mine.reload()} colors={colors} empty={<Empty title="No challans" colors={colors} />}>
          {(mine.data || []).map((c) => (
            <Card key={c.id} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{c.challanNumber}</Text>
              <Text style={{ color: colors.muted }}>{c.studentName} · {money(c.totalPayable)}</Text>
              <Badge value={c.status} colors={colors} />
              {c.status !== "PAID" ? <Btn label="Upload proof" kind="ghost" colors={colors} onPress={() => { setProofFor(c.id); setErr(""); }} /> : null}
            </Card>
          ))}
        </QueryState>
        {proofFor ? (
          <Card colors={colors}>
            <Field label="Transaction ref" colors={colors} value={ref} onChangeText={setRef} />
            {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
            <Btn
              label="Choose slip image"
              colors={colors}
              onPress={async () => {
                setErr("");
                try {
                  const a = await pickImage();
                  if (!a) return;
                  const up = await fileApi.upload(a);
                  await feeApi.submitProof(proofFor, { slipUrl: up.url, transactionRef: ref });
                  setProofFor(null);
                  setRef("");
                  await mine.reload();
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

function StaffFees() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const account = user?.role === "ACCOUNT_OFFICER";
  const proofs = useAsync(() => (account ? feeApi.pendingProofs(20) : Promise.resolve([])), [account], account);
  const unpaid = useAsync(() => feeApi.byStatus("UNPAID"), [], user?.role === "PRINCIPAL" || account);
  const classes = useAsync(() => academicApi.classes(), [], account);
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [scope, setScope] = useState<"class" | "section" | "student">("class");
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7) + "-01");
  const [q, setQ] = useState("");
  const dq = useDebounced(q, 350);
  const [studentId, setStudentId] = useState("");
  const [msg, setMsg] = useState("");
  const [err, setErr] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId) && account);
  const students = useAsync(
    () => studentApi.list(classId || undefined, sectionId || undefined, 0, dq || undefined),
    [classId, sectionId, dq],
    account && scope === "student",
  );

  if (user?.role === "SCHOOL_ADMIN") {
    return (
      <RequireAuth>
        <Screen colors={colors}>
          <Empty title="School admin cannot create or send fee challans" hint="Account Officer handles fees, challans, proofs, discounts and custom charges." colors={colors} />
        </Screen>
      </RequireAuth>
    );
  }
  if (user?.role === "TEACHER") {
    return (
      <RequireAuth>
        <Screen colors={colors}>
          <Empty title="Teachers do not operate fees" colors={colors} />
        </Screen>
      </RequireAuth>
    );
  }

  return (
    <RequireAuth roles={["ACCOUNT_OFFICER", "PRINCIPAL"]}>
      <Screen colors={colors} onRefresh={() => { void proofs.reload(); void unpaid.reload(); }} refreshing={proofs.loading || unpaid.loading}>
        <Title title="Fees" subtitle={account ? "Generate challans and verify slips." : "View unpaid challans. Account Officer verifies proofs."} colors={colors} />
        {account ? (
          <Card colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Generate challans</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              <Chip label="Whole class" selected={scope === "class"} onPress={() => setScope("class")} colors={colors} />
              <Chip label="Section" selected={scope === "section"} onPress={() => setScope("section")} colors={colors} />
              <Chip label="One student" selected={scope === "student"} onPress={() => setScope("student")} colors={colors} />
            </View>
            <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Class</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
              {(classes.data || []).map((c) => (
                <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); }} colors={colors} />
              ))}
            </View>
            {(scope === "section" || scope === "student") && classId ? (
              <>
                <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Section</Text>
                <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
                  {(sections.data || []).map((s) => (
                    <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => setSectionId(s.id)} colors={colors} />
                  ))}
                </View>
              </>
            ) : null}
            {scope === "student" ? (
              <>
                <Field label="Search student" colors={colors} value={q} onChangeText={setQ} placeholder="Name, roll, CNIC" />
                {(students.data?.content || []).slice(0, 8).map((s) => (
                  <Chip
                    key={s.id}
                    label={s.fullName || s.rollNumber || s.id.slice(0, 8)}
                    selected={studentId === s.id}
                    onPress={() => setStudentId(s.id)}
                    colors={colors}
                  />
                ))}
              </>
            ) : null}
            <Field label="Month (YYYY-MM-01)" colors={colors} value={month} onChangeText={setMonth} />
            {msg ? <Text style={{ color: colors.ok, marginBottom: 8 }}>{msg}</Text> : null}
            {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
            <Btn
              label="Generate"
              colors={colors}
              onPress={async () => {
                setErr("");
                setMsg("");
                try {
                  if (scope === "student") {
                    if (!studentId) throw new Error("Select a student.");
                    const rows = await feeApi.generate({ studentIds: [studentId], month });
                    setMsg(`Created ${Array.isArray(rows) ? rows.length : 1} challan(s).`);
                  } else if (scope === "section") {
                    if (!classId || !sectionId) throw new Error("Select class and section.");
                    const rows = await feeApi.generate({ classId, sectionId, month });
                    setMsg(`Created ${Array.isArray(rows) ? rows.length : 1} challan(s).`);
                  } else {
                    if (!classId) throw new Error("Select a class.");
                    const rows = await feeApi.generate({ classId, month });
                    setMsg(`Created ${Array.isArray(rows) ? rows.length : 1} challan(s).`);
                  }
                  await unpaid.reload();
                } catch (e) {
                  setErr(friendlyMessage(e));
                }
              }}
            />
          </Card>
        ) : null}
        {account ? (
          <QueryState loading={proofs.loading} error={proofs.error} data={proofs.data} onRetry={() => void proofs.reload()} colors={colors} empty={<Empty title="No pending proofs" colors={colors} />}>
            {(proofs.data || []).map((p) => (
              <Card key={String(p.id)} colors={colors}>
                <Text style={{ color: colors.ink, fontWeight: "700" }}>{String(p.studentName || p.transactionRef || p.id)}</Text>
                <Text style={{ color: colors.muted }}>{String(p.challanNumber || "")} · {money(p.amount as number | string | null | undefined)}</Text>
                <Badge value={String(p.status)} colors={colors} />
                {typeof p.slipUrl === "string" && p.slipUrl ? (
                  <Image source={{ uri: mediaUrl(String(p.slipUrl)) }} style={{ width: "100%", height: 160, borderRadius: 12, marginTop: 8 }} contentFit="contain" />
                ) : null}
                <Btn label="Approve" colors={colors} onPress={async () => { await feeApi.reviewProof(String(p.id), true); await proofs.reload(); }} />
                <Btn label="Reject" kind="danger" colors={colors} onPress={async () => { await feeApi.reviewProof(String(p.id), false, "Rejected from mobile"); await proofs.reload(); }} />
              </Card>
            ))}
          </QueryState>
        ) : (
          <QueryState loading={unpaid.loading} error={unpaid.error} data={unpaid.data} onRetry={() => void unpaid.reload()} colors={colors} empty={<Empty title="No unpaid challans" colors={colors} />}>
            {(unpaid.data || []).map((c) => (
              <Card key={c.id} colors={colors}>
                <Text style={{ color: colors.ink, fontWeight: "800" }}>{c.challanNumber}</Text>
                <Text style={{ color: colors.muted }}>{c.studentName} · {money(c.totalPayable)}</Text>
                <Badge value={c.status} colors={colors} />
              </Card>
            ))}
          </QueryState>
        )}
      </Screen>
    </RequireAuth>
  );
}
