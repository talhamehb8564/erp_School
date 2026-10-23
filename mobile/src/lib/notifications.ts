import { useEffect, useRef } from "react";
import { AppState, Platform } from "react-native";
import * as Notifications from "expo-notifications";
import { notificationApi } from "../api/services";
import { useSession } from "./session";

try {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowAlert: true,
      shouldPlaySound: false,
      shouldSetBadge: true,
      shouldShowBanner: true,
      shouldShowList: true,
    }),
  });
} catch {
  /* web / unsupported */
}

export function NotificationWatcher() {
  const { user, ready } = useSession();
  const last = useRef<number | null>(null);

  useEffect(() => {
    if (!ready || !user) return;
    let cancelled = false;
    const tick = async () => {
      try {
        const r = await notificationApi.unread();
        if (cancelled) return;
        const n = Number(r.unread || 0);
        if (last.current != null && n > last.current) {
          await Notifications.scheduleNotificationAsync({
            content: {
              title: "School ERP",
              body: n === 1 ? "You have a new notification." : `You have ${n} unread notifications.`,
            },
            trigger: null,
          });
        }
        last.current = n;
        if (Platform.OS !== "web") await Notifications.setBadgeCountAsync(n).catch(() => undefined);
      } catch {
        /* ignore poll errors */
      }
    };
    void tick();
    const id = setInterval(() => void tick(), 45_000);
    const sub = AppState.addEventListener("change", (s) => {
      if (s === "active") void tick();
    });
    return () => {
      cancelled = true;
      clearInterval(id);
      sub.remove();
    };
  }, [ready, user?.id]);

  return null;
}
