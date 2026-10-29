import { Pressable, Text } from "react-native";
import { useSession } from "../../lib/session";
import { useAppearance } from "../../lib/appearance";
import { Badge, Btn, Card, Empty, QueryState, Screen, Title, useAsync } from "../../ui/kit";
import { notificationApi } from "../../api/services";
import { pretty } from "../../lib/format";
import { router } from "expo-router";

function hrefFor(entityType?: string) {
  const t = (entityType || "").toLowerCase();
  if (t.includes("fee") || t.includes("challan")) return "/fees";
  if (t.includes("online")) return "/online-exams";
  if (t.includes("exam") || t.includes("result")) return "/exams";
  if (t.includes("homework")) return "/homework";
  if (t.includes("attendance")) return "/attendance";
  if (t.includes("remark")) return "/remarks";
  if (t.includes("announc")) return "/announcements";
  return "/(tabs)/alerts";
}

export default function Alerts() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => notificationApi.list(), [user?.id]);
  const rows = list.data?.content || [];
  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title kicker="Inbox" title="Notifications" subtitle="Server-side events for this account only." colors={colors} />
      <Btn
        label="Mark visible as read"
        colors={colors}
        kind="ghost"
        onPress={async () => {
          const unread = rows.filter((n) => !n.readAt);
          for (const n of unread) await notificationApi.read(n.id);
          await list.reload();
        }}
      />
      <QueryState
        loading={list.loading}
        error={list.error}
        data={rows}
        onRetry={() => void list.reload()}
        colors={colors}
        empty={<Empty title="No notifications" colors={colors} />}
      >
        {rows.map((n) => (
          <Pressable key={n.id} onPress={() => { if (!n.readAt) void notificationApi.read(n.id).then(() => list.reload()); router.push(hrefFor(n.entityType) as never); }}>
            <Card colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{n.title || n.type}</Text>
              {n.body ? <Text style={{ color: colors.muted, marginTop: 4 }}>{n.body}</Text> : null}
              <Badge value={n.readAt ? "READ" : "UNREAD"} colors={colors} />
              <Text style={{ color: colors.muted, fontSize: 11, marginTop: 6 }}>{pretty(n.type)} · {n.createdAt?.slice(0, 16)}</Text>
            </Card>
          </Pressable>
        ))}
      </QueryState>
    </Screen>
  );
}
