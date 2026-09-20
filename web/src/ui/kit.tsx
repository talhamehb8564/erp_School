import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { openAuthedFile } from "../api/client";
import { pretty, statusTone } from "../lib/format";

export function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}

export function Button({
  children,
  onClick,
  type = "button",
  kind = "solid",
  disabled,
}: {
  children: ReactNode;
  onClick?: () => void;
  type?: "button" | "submit";
  kind?: "solid" | "ghost" | "brass" | "danger" | "ok";
  disabled?: boolean;
}) {
  return (
    <button type={type} className={`btn ${kind === "solid" ? "" : kind}`.trim()} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  );
}

export function Badge({ value }: { value?: string }) {
  return <span className={`badge ${statusTone(value)}`}>{pretty(value)}</span>;
}

export function Empty({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="empty">
      <strong>{title}</strong>
      {hint ? <p>{hint}</p> : null}
    </div>
  );
}

export function Loading() {
  return <div className="loading">Loading live records…</div>;
}

export function ErrorBox({ error }: { error: unknown }) {
  const msg = error instanceof Error ? error.message : "Something went wrong";
  return <div className="error-box">{msg}</div>;
}

export function Modal({
  title,
  open,
  onClose,
  children,
}: {
  title: string;
  open: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  if (!open) return null;
  return (
    <div className="modal-back" onClick={onClose} role="presentation">
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog">
        <div className="page-title">
          <h2 style={{ margin: 0, fontSize: 24 }}>{title}</h2>
          <Button kind="ghost" onClick={onClose}>
            Close
          </Button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Stat({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="card stat">
      <div className="label">{label}</div>
      <b>{value ?? "—"}</b>
    </div>
  );
}

export function Table({ headers, rows }: { headers: string[]; rows: ReactNode[][] }) {
  if (!rows.length) return <Empty title="No records yet" hint="Live API returned an empty list." />;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {headers.map((h) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {r.map((c, j) => (
                <td key={j}>{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const reload = async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await fn());
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { data, error, loading, reload, setData };
}

export function Search({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return <input className="search" value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />;
}

export function Form({ onSubmit, children }: { onSubmit: () => void | Promise<void>; children: ReactNode }) {
  const go = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit();
  };
  return <form onSubmit={go}>{children}</form>;
}

export function FileLink({ href, label }: { href?: string; label?: string }) {
  const [err, setErr] = useState("");
  if (!href) return <span>—</span>;
  return (
    <span style={{ display: "inline-flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
      <button
        type="button"
        className="btn ghost sm"
        onClick={() => {
          setErr("");
          void openAuthedFile(href).catch((e) => setErr(e instanceof Error ? e.message : "Open failed"));
        }}
      >
        {label || "Open file"}
      </button>
      {err ? <span className="hint" style={{ margin: 0 }}>{err}</span> : null}
    </span>
  );
}

export function studentLabel(s: { id: string; admissionNumber?: string; rollNumber?: string; user?: { fullName?: string } }) {
  return s.user?.fullName || s.admissionNumber || s.rollNumber || s.id.slice(0, 8);
}

export function Bars({ items }: { items: { label: string; value: number; max?: number }[] }) {
  const max = Math.max(1, ...items.map((i) => i.max ?? i.value));
  return (
    <div className="bars">
      {items.map((i) => (
        <div className="bar" key={i.label}>
          <span>{i.label}</span>
          <i>
            <b style={{ width: `${Math.min(100, (i.value / max) * 100)}%` }} />
          </i>
          <span>{i.value}</span>
        </div>
      ))}
    </div>
  );
}
