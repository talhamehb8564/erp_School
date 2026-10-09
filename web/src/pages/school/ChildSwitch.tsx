import { useEffect } from "react";
import { useSession } from "../../lib/session";
import type { StudentUser } from "../../lib/types";

export default function ChildSwitch({ childrenList }: { childrenList: StudentUser[] }) {
  const { childId, setChildId } = useSession();
  const ids = childrenList.map((c) => c.id).join(",");
  useEffect(() => {
    if (!childrenList.length) return;
    if (!childId || !childrenList.some((c) => c.id === childId)) {
      setChildId(childrenList[0].id);
    }
  }, [childId, ids, childrenList, setChildId]);
  if (!childrenList.length) return null;
  return (
    <div className="children">
      {childrenList.map((c) => (
        <button type="button" key={c.id} className={`chip ${childId === c.id ? "on" : ""}`} onClick={() => setChildId(c.id)}>
          {c.user?.fullName || c.admissionNumber || c.id.slice(0, 8)}
        </button>
      ))}
    </div>
  );
}

export function useActiveStudentId(fallback?: string | null) {
  const { user, childId } = useSession();
  if (user?.role === "PARENT") return childId;
  return fallback || null;
}
