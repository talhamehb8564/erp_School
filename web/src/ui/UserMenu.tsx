import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { initials } from "../lib/format";
import { useSession } from "../lib/session";
import { Button } from "./kit";

export default function UserMenu({ passwordTo }: { passwordTo: string }) {
  const { user, logout } = useSession();
  const [open, setOpen] = useState(false);
  const wrap = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (!user) return null;
  return (
    <div className="menu user-menu" ref={wrap}>
      <button type="button" className="avatar user-menu-btn" aria-expanded={open} aria-haspopup="menu" onClick={() => setOpen((v) => !v)} title={user.fullName}>
        {initials(user.fullName)}
      </button>
      {open ? (
        <div className="menu-pop" role="menu">
          <div className="user-menu-id">
            <strong>{user.fullName}</strong>
            <span>{user.username}</span>
          </div>
          <Link to={passwordTo} role="menuitem" onClick={() => setOpen(false)}>Credentials</Link>
          <Button kind="ghost" loadingText="Signing out…" onClick={() => logout()}>
            Sign out
          </Button>
        </div>
      ) : null}
    </div>
  );
}
