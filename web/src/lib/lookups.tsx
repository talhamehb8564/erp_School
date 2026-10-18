import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { academicApi } from "../api/services";
import type { SchoolClass, Section, Subject } from "./types";

interface Lookups {
  classes: SchoolClass[];
  subjects: Subject[];
  sections: Record<string, Section[]>;
  className: (id?: string) => string;
  subjectName: (id?: string) => string;
  loading: boolean;
  error: unknown;
  reload: () => Promise<void>;
}

const Ctx = createContext<Lookups | null>(null);

const LOOKUP_PREFIXES = [
  "/app/students",
  "/app/academics",
  "/app/timetable",
  "/app/attendance",
  "/app/homework",
  "/app/exams",
  "/app/online-exams",
  "/app/fees",
  "/app/salaries",
  "/app/announcements",
  "/app/calendar",
];

export function lookupsNeeded(pathname: string) {
  return LOOKUP_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

export function LookupsProvider({ children, enabled, prefetch }: { children: React.ReactNode; enabled: boolean; prefetch?: boolean }) {
  const [classes, setClasses] = useState<SchoolClass[]>([]);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [sections, setSections] = useState<Record<string, Section[]>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [c, s, bulkSections] = await Promise.all([
        academicApi.classes(),
        academicApi.subjects(),
        academicApi.allSections().catch(() => [] as Section[]),
      ]);
      setClasses(c);
      setSubjects(s);
      const map: Record<string, Section[]> = {};
      for (const sec of bulkSections) {
        (map[sec.classId] ||= []).push(sec);
      }
      setSections(map);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (classes.length || subjects.length) return;
    if (enabled) {
      void reload();
      return;
    }
    if (!prefetch) return;
    const idle = (window as Window & { requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number }).requestIdleCallback;
    if (typeof idle === "function") {
      const id = idle(() => { void reload(); }, { timeout: 400 });
      const cancel = (window as Window & { cancelIdleCallback?: (id: number) => void }).cancelIdleCallback;
      return () => cancel?.(id);
    }
    const t = window.setTimeout(() => { void reload(); }, 0);
    return () => window.clearTimeout(t);
  }, [enabled, prefetch, reload, classes.length, subjects.length]);

  const classMap = useMemo(() => new Map(classes.map((c) => [c.id, c.name])), [classes]);
  const subjectMap = useMemo(() => new Map(subjects.map((s) => [s.id, s.name])), [subjects]);
  const value = useMemo<Lookups>(
    () => ({
      classes,
      subjects,
      sections,
      className: (id) => (id && classMap.get(id)) || id?.slice(0, 8) || "—",
      subjectName: (id) => (id && subjectMap.get(id)) || id?.slice(0, 8) || "—",
      loading,
      error,
      reload,
    }),
    [classes, subjects, sections, classMap, subjectMap, loading, error, reload],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useLookups() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("lookups");
  return ctx;
}
