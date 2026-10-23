import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { admissionApi } from "../api/services";
import { Badge, Btn, Card, Empty, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { pretty } from "../lib/format";

export default function Admissions() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => admissionApi.applications(0));
  if (!user || !["SCHOOL_ADMIN", "PRINCIPAL"].includes(user.role)) {
    return <Screen colors={colors}><Empty title="Not permitted" colors={colors} /></Screen>;
  }
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Admissions" subtitle="Submitted applications. CNIC uniqueness is tenant-wide." colors={colors} />
      <QueryState loading={list.loading} error={list.error} data={list.data?.content} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No submitted applications" colors={colors} />}>
        {(list.data?.content || []).map((a) => (
          <Card key={String(a.id)} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(a.studentName || a.fullName || "Applicant")}</Text>
            <Text style={{ color: colors.muted }}>{String(a.className || "")} · {String(a.cnicMasked || a.cnic || "")}</Text>
            <Badge value={String(a.status)} colors={colors} />
            {user.role === "SCHOOL_ADMIN" || user.role === "PRINCIPAL" ? (
              <>
                <Btn label="Accept" colors={colors} onPress={async () => { await admissionApi.setStatus(String(a.id), "ACCEPTED"); await list.reload(); }} />
                <Btn label="Reject" kind="danger" colors={colors} onPress={async () => { await admissionApi.setStatus(String(a.id), "REJECTED"); await list.reload(); }} />
              </>
            ) : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
