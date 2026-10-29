import { useState } from "react";
import { Text, View } from "react-native";
import { useAppearance } from "../lib/appearance";
import { attendanceApi } from "../api/services";
import { Badge, Btn, Card, Empty, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { DateField } from "../ui/dates";
import { today } from "../lib/format";
import type { AttendanceStatus } from "../lib/types";
import { RequireAuth } from "../ui/gate";

export default function TeacherAttendance() {
  const { colors } = useAppearance();
  const [date, setDate] = useState(today());
  const [err, setErr] = useState("");
  const day = useAsync(() => attendanceApi.teacherDay(date), [date]);
  return (
    <RequireAuth roles={["SCHOOL_ADMIN", "PRINCIPAL"]}>
      <Screen colors={colors} onRefresh={() => void day.reload()} refreshing={day.loading}>
        <Title title="Staff attendance" subtitle="Teachers for this campus. Status is stored per day on the server." colors={colors} />
        <DateField label="Date" value={date} onChange={setDate} colors={colors} />
        {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
        <QueryState
          loading={day.loading}
          error={day.error}
          data={day.data}
          onRetry={() => void day.reload()}
          colors={colors}
          empty={<Empty title="No staff records found" hint="Active teacher accounts for this school appear here." colors={colors} />}
        >
          {(day.data || []).map((r) => (
            <Card key={String(r.teacherUserId)} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{String(r.fullName || r.username)}</Text>
              <Badge value={String(r.status || "UNMARKED")} colors={colors} />
              <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
                {(["PRESENT", "ABSENT", "LATE", "LEAVE"] as AttendanceStatus[]).map((status) => (
                  <Btn
                    key={status}
                    label={status}
                    kind={String(r.status) === status ? "solid" : "ghost"}
                    colors={colors}
                    onPress={async () => {
                      setErr("");
                      try {
                        await attendanceApi.markTeacher({ teacherUserId: r.teacherUserId, date, status });
                        await day.reload();
                      } catch (e) {
                        setErr(friendlyMessage(e));
                      }
                    }}
                  />
                ))}
              </View>
            </Card>
          ))}
        </QueryState>
      </Screen>
    </RequireAuth>
  );
}
