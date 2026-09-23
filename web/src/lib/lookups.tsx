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

export function LookupsProvider({ children, enabled }: { children: React.ReactNode; enabled: boolean }) {
  const [classes, setClasses] = useState<SchoolClass[]>([]);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [sections, setSections] = useState<Record<string, Section[]>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const reload = useCallback(async () => {
    if (!enabled) return;
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
      let allSections = bulkSections;
      if (!allSections.length && c.length) {
        const nested = await Promise.all(c.map((cl) => academicApi.sections(cl.id).catch(() => [] as Section[])));
        allSections = nested.flat();
      }
      const map: Record<string, Section[]> = {};
      for (const sec of allSections) {
        (map[sec.classId] ||= []).push(sec);
      }
      setSections(map);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, [enabled]);

  useEffect(() => {
    if (!enabled) return;
    void reload();
  }, [enabled, reload]);

  const value = useMemo<Lookups>(
    () => ({
      classes,
      subjects,
      sections,
      className: (id) => classes.find((c) => c.id === id)?.name || id?.slice(0, 8) || "—",
      subjectName: (id) => subjects.find((s) => s.id === id)?.name || id?.slice(0, 8) || "—",
      loading,
      error,
      reload,
    }),
    [classes, subjects, sections, loading, error, reload],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useLookups() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("lookups");
  return ctx;
}
