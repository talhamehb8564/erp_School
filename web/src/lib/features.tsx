import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { featureApi } from "../api/services";
import { useSession } from "./session";

export const FEATURE_KEYS = [
  "ATTENDANCE",
  "TEACHER_ATTENDANCE",
  "RESULTS",
  "ONLINE_EXAMS",
  "FEES",
  "HOMEWORK",
  "TIMETABLE",
  "NOTIFICATIONS",
  "ANNOUNCEMENTS",
  "ADMISSIONS",
  "STUDENTS",
  "PARENT_PORTAL",
  "CALENDAR",
  "SALARIES",
  "WEBSITE",
] as const;

export type FeatureKey = (typeof FEATURE_KEYS)[number];

export const FEATURE_LABELS: Record<FeatureKey, string> = {
  ATTENDANCE: "Student attendance",
  TEACHER_ATTENDANCE: "Teacher attendance",
  RESULTS: "Marks & results",
  ONLINE_EXAMS: "Online exams",
  FEES: "Fees",
  HOMEWORK: "Homework",
  TIMETABLE: "Timetable",
  NOTIFICATIONS: "Notifications",
  ANNOUNCEMENTS: "Announcements",
  ADMISSIONS: "Admissions",
  STUDENTS: "Student management",
  PARENT_PORTAL: "Parent portal APIs",
  CALENDAR: "Calendar",
  SALARIES: "Salaries",
  WEBSITE: "Website CMS",
};

export const PATH_FEATURE: Record<string, FeatureKey> = {
  "/app/students": "STUDENTS",
  "/app/attendance": "ATTENDANCE",
  "/app/teacher-attendance": "TEACHER_ATTENDANCE",
  "/app/homework": "HOMEWORK",
  "/app/exams": "RESULTS",
  "/app/online-exams": "ONLINE_EXAMS",
  "/app/fees": "FEES",
  "/app/salaries": "SALARIES",
  "/app/timetable": "TIMETABLE",
  "/app/notifications": "NOTIFICATIONS",
  "/app/announcements": "ANNOUNCEMENTS",
  "/app/admissions": "ADMISSIONS",
  "/app/calendar": "CALENDAR",
  "/app/website": "WEBSITE",
};

interface Features {
  flags: Record<string, boolean>;
  loaded: boolean;
  on: (key?: string) => boolean;
  reload: () => Promise<void>;
}

const Ctx = createContext<Features | null>(null);

export function FeatureProvider({ children }: { children: ReactNode }) {
  const { user } = useSession();
  const [flags, setFlags] = useState<Record<string, boolean>>({});
  const [loaded, setLoaded] = useState(false);

  const reload = async () => {
    if (!user || user.role === "ERP_OWNER") {
      setFlags({});
      setLoaded(true);
      return;
    }
    try {
      setFlags(await featureApi.current());
    } catch {
      setFlags({});
    } finally {
      setLoaded(true);
    }
  };

  useEffect(() => {
    setLoaded(false);
    void reload();
  }, [user?.id, user?.tenantId]);

  const value = useMemo<Features>(() => ({
    flags,
    loaded,
    on: (key) => {
      if (!key) return true;
      const k = key.toUpperCase().replace(/-/g, "_");
      if (!(k in flags)) return true;
      return flags[k] !== false;
    },
    reload,
  }), [flags, loaded]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useFeatures() {
  const ctx = useContext(Ctx);
  if (!ctx) {
    return {
      flags: {} as Record<string, boolean>,
      loaded: true,
      on: () => true,
      reload: async () => undefined,
    };
  }
  return ctx;
}
