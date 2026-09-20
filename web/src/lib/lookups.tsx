import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { academicApi } from "../api/services";
import type { SchoolClass, Section, Subject } from "./types";

interface Lookups {
  classes: SchoolClass[];
  subjects: Subject[];
  sections: Record<string, Section[]>;
  className: (id?: string) => string;
  subjectName: (id?: string) => string;
  reload: () => Promise<void>;
}

const Ctx = createContext<Lookups | null>(null);

export function LookupsProvider({ children, enabled }: { children: React.ReactNode; enabled: boolean }) {
  const [classes, setClasses] = useState<SchoolClass[]>([]);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [sections, setSections] = useState<Record<string, Section[]>>({});

  const reload = async () => {
    if (!enabled) return;
    try {
      const [c, s] = await Promise.all([academicApi.classes(), academicApi.subjects()]);
      setClasses(c);
      setSubjects(s);
      const map: Record<string, Section[]> = {};
      await Promise.all(
        c.map(async (cl) => {
          try {
            map[cl.id] = await academicApi.sections(cl.id);
          } catch {
            map[cl.id] = [];
          }
        }),
      );
      setSections(map);
    } catch {
      /* locked tenants cannot load academics */
    }
  };

  useEffect(() => {
    void reload();
  }, [enabled]);

  const value = useMemo<Lookups>(
    () => ({
      classes,
      subjects,
      sections,
      className: (id) => classes.find((c) => c.id === id)?.name || id?.slice(0, 8) || "—",
      subjectName: (id) => subjects.find((s) => s.id === id)?.name || id?.slice(0, 8) || "—",
      reload,
    }),
    [classes, subjects, sections],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useLookups() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("lookups");
  return ctx;
}
