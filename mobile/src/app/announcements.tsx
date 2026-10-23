import { useState } from "react";
import { Text } from "react-native";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { announcementApi } from "../api/services";
import { Btn, Card, Empty, Field, QueryState, Screen, Title, useAsync } from "../ui/kit";
import { fmtDate } from "../lib/format";

export default function Announcements() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => announcementApi.list());
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const canPost = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Announcements" colors={colors} />
      {canPost ? (
        <Card colors={colors}>
          <Field label="Title" colors={colors} value={title} onChangeText={setTitle} />
          <Field label="Body" colors={colors} value={body} onChangeText={setBody} multiline />
          <Btn
            label="Publish"
            colors={colors}
            onPress={async () => {
              await announcementApi.create({ title, body });
              setTitle("");
              setBody("");
              await list.reload();
            }}
          />
        </Card>
      ) : null}
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No announcements" colors={colors} />}>
        {(list.data || []).map((a) => (
          <Card key={a.id} colors={colors}>
            <Text style={{ color: colors.ink, fontWeight: "800" }}>{a.title}</Text>
            <Text style={{ color: colors.muted }}>{fmtDate(a.createdAt)}</Text>
            {a.body ? <Text style={{ color: colors.ink, marginTop: 6 }}>{a.body}</Text> : null}
          </Card>
        ))}
      </QueryState>
    </Screen>
  );
}
