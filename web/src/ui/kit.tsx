import { createContext, useContext, useEffect, useState, type FormEvent, type ReactNode } from "react";
import { ApiError, openAuthedFile } from "../api/client";
import { pretty, statusTone } from "../lib/format";
import { useToast } from "../lib/toast";

const FormBusy = createContext<{ busy: boolean; label: string }>({ busy: false, label: "Working…" });

export function friendlyMessage(error: unknown, fallback = "Something went wrong. Please try again.") {
  if (error instanceof ApiError) {
    if (error.status === 0) return "Cannot reach the ERP server. Start Spring Boot on port 8080.";
    if (error.status === 401) return "Your session expired. Please sign in again.";
    if (error.status >= 500) return "The server could not complete this request. Please try again.";
    if (error.message && !/exception|sql|hibernate|stack/i.test(error.message)) return error.message;
    if (error.status === 403) return "You do not have permission to do that.";
    if (error.status === 404) return "The requested record was not found.";
    return fallback;
  }
  if (error instanceof Error && error.message && !/exception|sql|hibernate|at [a-z]+\./i.test(error.message)) {
    return error.message;
  }
  if (typeof error === "string" && error.trim()) return error;
  return fallback;
}

export function Spinner({ small }: { small?: boolean }) {
  return <i className={`spinner ${small ? "sm" : ""}`} aria-hidden />;
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
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
  loading,
  loadingText,
}: {
  children: ReactNode;
  onClick?: () => void | Promise<void>;
  type?: "button" | "submit";
  kind?: "solid" | "ghost" | "brass" | "danger" | "ok";
  disabled?: boolean;
  loading?: boolean;
  loadingText?: string;
}) {
  const form = useContext(FormBusy);
  const toast = useToast();
  const [inner, setInner] = useState(false);
  const submitBusy = type === "submit" && form.busy;
  const busy = Boolean(loading || inner || submitBusy);
  const label = busy ? loadingText || (submitBusy ? form.label : null) || children : children;

  const handle = async () => {
    if (!onClick || busy) return;
    const result = onClick();
    if (result && typeof (result as Promise<void>).then === "function") {
      setInner(true);
      try {
        await result;
      } catch (e) {
        toast("err", friendlyMessage(e));
      } finally {
        setInner(false);
      }
    }
  };

  return (
    <button type={type} className={`btn ${kind === "solid" ? "" : kind}`.trim()} onClick={type === "submit" ? undefined : handle} disabled={disabled || busy} aria-busy={busy}>
      {busy ? <Spinner small /> : null}
      {label}
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

export function Loading({ label }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <Spinner />
      <span>{label || "Loading…"}</span>
    </div>
  );
}

export function ErrorBox({ error, onRetry, title }: { error: unknown; onRetry?: () => void; title?: string }) {
  return (
    <div className="error-box" role="alert">
      <strong>{title || "Unable to load"}</strong>
      <p>{friendlyMessage(error)}</p>
      {onRetry ? (
        <Button kind="ghost" onClick={() => onRetry()}>
          Retry
        </Button>
      ) : null}
    </div>
  );
}

export function QueryState({
  status,
  label,
  children,
}: {
  status: { data: unknown; error: unknown; loading: boolean; reload: () => void };
  label: string;
  children: ReactNode;
}) {
  if (status.loading && status.data == null) return <Loading label={`Loading ${label}…`} />;
  if (status.error && status.data == null) {
    return <ErrorBox error={status.error} onRetry={() => void status.reload()} title={`Unable to load ${label}`} />;
  }
  return <>{children}</>;
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

export function Stat({ label, value, loading }: { label: string; value: ReactNode; loading?: boolean }) {
  return (
    <div className="card stat">
      <div className="label">{label}</div>
      <b>{loading ? <Spinner /> : value ?? "—"}</b>
    </div>
  );
}

export function Table({ headers, rows }: { headers: string[]; rows: ReactNode[][] }) {
  if (!rows.length) return <Empty title="No records found." hint="Nothing matched this view." />;
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
    setData(null);
    try {
      setData(await fn());
    } catch (e) {
      setError(e);
      setData(null);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setData(null);
    void (async () => {
      try {
        const next = await fn();
        if (!cancelled) setData(next);
      } catch (e) {
        if (!cancelled) {
          setError(e);
          setData(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { data, error, loading, reload, setData };
}

export function useDebounced<T>(value: T, ms = 320) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = window.setTimeout(() => setV(value), ms);
    return () => window.clearTimeout(t);
  }, [value, ms]);
  return v;
}

export function Search({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return <input className="search" value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />;
}

export function Form({
  onSubmit,
  children,
  busyLabel = "Saving…",
}: {
  onSubmit: () => void | Promise<void>;
  children: ReactNode;
  busyLabel?: string;
}) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const go = async (e: FormEvent) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setErr("");
    try {
      await onSubmit();
    } catch (ex) {
      setErr(friendlyMessage(ex));
    } finally {
      setBusy(false);
    }
  };
  return (
    <form onSubmit={go} aria-busy={busy}>
      <FormBusy.Provider value={{ busy, label: busyLabel }}>
        {children}
        {err ? <div className="error-box" style={{ marginTop: 12 }}>{err}</div> : null}
      </FormBusy.Provider>
    </form>
  );
}

export function FileLink({ href, label }: { href?: string; label?: string }) {
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  if (!href) return <span>—</span>;
  return (
    <span style={{ display: "inline-flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
      <button
        type="button"
        className="btn ghost sm"
        disabled={busy}
        onClick={() => {
          setErr("");
          setBusy(true);
          void openAuthedFile(href)
            .catch((e) => setErr(e instanceof Error ? e.message : "Open failed"))
            .finally(() => setBusy(false));
        }}
      >
        {busy ? <Spinner small /> : null}
        {busy ? "Opening…" : label || "Open file"}
      </button>
      {err ? <span className="hint" style={{ margin: 0 }}>{err}</span> : null}
    </span>
  );
}

export function studentLabel(s: { id: string; admissionNumber?: string; rollNumber?: string; user?: { fullName?: string } }) {
  return s.user?.fullName || s.admissionNumber || s.rollNumber || s.id.slice(0, 8);
}

export function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="row" style={{ marginTop: 12 }}>
      <Button kind="ghost" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Previous
      </Button>
      <span className="hint" style={{ margin: 0 }}>
        Page {page + 1} of {totalPages}
      </span>
      <Button kind="ghost" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next
      </Button>
    </div>
  );
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

export function Donut({ value, max, label }: { value: number; max: number; label: string }) {
  const safeMax = Math.max(1, max);
  const pct = Math.min(100, (value / safeMax) * 100);
  const r = 36;
  const c = 2 * Math.PI * r;
  const dash = (pct / 100) * c;
  return (
    <div className="donut">
      <svg viewBox="0 0 88 88" width="88" height="88" aria-hidden>
        <circle cx="44" cy="44" r={r} fill="none" stroke="var(--accent-2)" strokeWidth="10" />
        <circle
          cx="44"
          cy="44"
          r={r}
          fill="none"
          stroke="var(--accent)"
          strokeWidth="10"
          strokeDasharray={`${dash} ${c}`}
          strokeLinecap="round"
          transform="rotate(-90 44 44)"
        />
      </svg>
      <div>
        <b>{value}</b>
        <span>{label}</span>
      </div>
    </div>
  );
}
