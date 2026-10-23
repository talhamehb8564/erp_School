import { useState } from "react";
import { Text } from "react-native";
import { useSession } from "../lib/session";
import { pickImage } from "../lib/picker";
import { useAppearance } from "../lib/appearance";
import { academicApi, feeApi, fileApi, settingsApi, studentApi } from "../api/services";
import { Badge, Btn, Card, Empty, Field, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { money, pretty } from "../lib/format";

export default function Fees() {
  const { user } = useSession();
  const { colors } = useAppearance();
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
  return (
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
            {c.status !== "PAID" ? <Btn label="Upload proof" kind="ghost" colors={colors} onPress={() => setProofFor(c.id)} /> : null}
          </Card>
        ))}
      </QueryState>
      {proofFor ? (
        <Card colors={colors}>
          <Field label="Transaction ref" colors={colors} value={ref} onChangeText={setRef} />
          <Btn
            label="Choose slip image"
            colors={colors}
            onPress={async () => {
              const a = await pickImage();
              if (!a) return;
              const up = await fileApi.upload(a);
              await feeApi.submitProof(proofFor, { slipUrl: up.url, transactionRef: ref });
              setProofFor(null);
              setRef("");
              await mine.reload();
            }}
          />
        </Card>
      ) : null}
    </Screen>
  );
}

function StaffFees() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const account = user?.role === "ACCOUNT_OFFICER";
  const proofs = useAsync(() => feeApi.pendingProofs(20));
  const classes = useAsync(() => academicApi.classes(), [], account);
  const [classId, setClassId] = useState("");
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7) + "-01");
  if (user?.role === "SCHOOL_ADMIN") {
    return <Screen colors={colors}><Empty title="School admin cannot create or send fee challans" hint="Account Officer handles fees." colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => void proofs.reload()} refreshing={proofs.loading}>
      <Title title="Fees" subtitle="Generate challans and verify slips. Teachers cannot operate fees." colors={colors} />
      {account ? (
        <Card colors={colors}>
          <Text style={{ color: colors.ink, fontWeight: "800", marginBottom: 8 }}>Generate class challans</Text>
          {(classes.data || []).map((c) => (
            <Text key={c.id} onPress={() => setClassId(c.id)} style={{ color: classId === c.id ? colors.brand : colors.ink, marginBottom: 6 }}>
              {c.name}
            </Text>
          ))}
          <Field label="Month (YYYY-MM-01)" colors={colors} value={month} onChangeText={setMonth} />
          <Btn
            label="Generate"
            colors={colors}
            onPress={async () => {
              if (!classId) return;
              await feeApi.generate({ classId, month });
            }}
          />
        </Card>
      ) : null}
      <QueryState loading={proofs.loading} error={proofs.error} data={proofs.data} onRetry={() => void proofs.reload()} colors={colors} empty={<Empty title="No pending proofs" colors={colors} />}>
        {(proofs.data || []).map((p) => (
          <Card key={String(p.id)} colors={colors}>
            <Text style={{ color: colors.ink }}>{String(p.transactionRef || p.id)}</Text>
            <Badge value={String(p.status)} colors={colors} />
            {account ? (
              <>
                <Btn label="Approve" colors={colors} onPress={async () => { await feeApi.reviewProof(String(p.id), true); await proofs.reload(); }} />
                <Btn label="Reject" kind="danger" colors={colors} onPress={async () => { await feeApi.reviewProof(String(p.id), false, "Rejected from mobile"); await proofs.reload(); }} />
              </>
            ) : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
