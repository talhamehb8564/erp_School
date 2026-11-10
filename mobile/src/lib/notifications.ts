import { useEffect, useRef } from "react";
import { AppState, Platform } from "react-native";
import { requireOptionalNativeModule } from "expo";
import { notificationApi } from "../api/services";
import { useSession } from "./session";
import { portalTitle } from "./format";

/**
 * Do not import the `expo-notifications` package index.
 *
 * `build/index.js` re-exports `./topicSubscription`, and on Android that file
 * calls `requireNativeModule('ExpoTopicSubscriptionModule')`. Expo Go on
 * Android (SDK 53+) does not ship that native module, so a barrel import throws
 * while evaluating `src/app/_layout.tsx`. Expo Router then loads an undefined
 * route module and crashes with `Cannot read property 'ErrorBoundary' of undefined`.
 *
 * Local banners/badges use separate files that do not touch the topic module.
 * `requireOptionalNativeModule` is checked first so Metro never evaluates a
 * `requireNativeModule` throw in Expo Go.
 */
type LocalNotifier = {
  scheduleNotificationAsync: (request: {
    content: { title: string; body: string };
    trigger: null;
  }) => Promise<unknown>;
  setBadgeCountAsync: (count: number) => Promise<unknown>;
};

let cached: LocalNotifier | null | undefined;

function loadLocalNotifier(): LocalNotifier | null {
  if (cached !== undefined) return cached;
  if (Platform.OS === "web") {
    cached = null;
    return cached;
  }
  const scheduler = requireOptionalNativeModule("ExpoNotificationScheduler");
  const handlerMod = requireOptionalNativeModule("ExpoNotificationsHandlerModule");
  if (!scheduler || !handlerMod) {
    cached = null;
    return cached;
  }
  try {
    // Subpath requires — not `expo-notifications` — so TopicSubscriptionModule.android.js is never loaded.
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const handler = require("expo-notifications/build/NotificationsHandler") as {
      setNotificationHandler: (h: {
        handleNotification: () => Promise<{
          shouldShowAlert: boolean;
          shouldPlaySound: boolean;
          shouldSetBadge: boolean;
          shouldShowBanner: boolean;
          shouldShowList: boolean;
        }>;
      }) => void;
    };
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const schedule = require("expo-notifications/build/scheduleNotificationAsync") as {
      scheduleNotificationAsync: LocalNotifier["scheduleNotificationAsync"];
    };
    handler.setNotificationHandler({
      handleNotification: async () => ({
        shouldShowAlert: true,
        shouldPlaySound: false,
        shouldSetBadge: true,
        shouldShowBanner: true,
        shouldShowList: true,
      }),
    });
    const badgeMod = requireOptionalNativeModule("ExpoBadgeModule");
    let setBadgeCountAsync: LocalNotifier["setBadgeCountAsync"] = async () => false;
    if (badgeMod) {
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      const badge = require("expo-notifications/build/setBadgeCountAsync") as {
        setBadgeCountAsync: LocalNotifier["setBadgeCountAsync"];
      };
      setBadgeCountAsync = badge.setBadgeCountAsync;
    }
    cached = {
      scheduleNotificationAsync: schedule.scheduleNotificationAsync,
      setBadgeCountAsync,
    };
  } catch {
    cached = null;
  }
  return cached;
}

export function NotificationWatcher() {
  const { user, ready, brand } = useSession();
  const last = useRef<number | null>(null);

  useEffect(() => {
    if (!ready || !user) return;
    let cancelled = false;
    const notifier = loadLocalNotifier();
    const tick = async () => {
      try {
        const r = await notificationApi.unread();
        if (cancelled) return;
        const n = Number(r.unread || 0);
        if (last.current != null && n > last.current && notifier) {
          await notifier.scheduleNotificationAsync({
            content: {
              title: brand?.name || portalTitle(brand),
              body: n === 1 ? "You have a new notification." : `You have ${n} unread notifications.`,
            },
            trigger: null,
          });
        }
        last.current = n;
        if (notifier) await notifier.setBadgeCountAsync(n).catch(() => undefined);
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
  }, [ready, user?.id, brand?.name]);

  return null;
}
