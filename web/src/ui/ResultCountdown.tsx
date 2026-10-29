import { useEffect, useRef, useState } from "react";

function pad(n: number) {
  return String(n).padStart(2, "0");
}

/** Live countdown from a backend Instant. Ticks locally; unlocks when the server time is reached. */
export default function ResultCountdown({
  announceAt,
  seconds,
  sessionName,
  onDone,
}: {
  announceAt?: string;
  seconds?: number;
  sessionName?: string;
  onDone?: () => void;
}) {
  const [left, setLeft] = useState(seconds ?? 0);
  const done = useRef(false);
  useEffect(() => {
    const tick = () => {
      if (!announceAt) return;
      setLeft(Math.max(0, Math.floor((new Date(announceAt).getTime() - Date.now()) / 1000)));
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [announceAt]);
  useEffect(() => {
    if (left > 0 || done.current) return;
    if (!announceAt || new Date(announceAt).getTime() > Date.now()) return;
    done.current = true;
    onDone?.();
  }, [left, announceAt, onDone]);
  if (left <= 0) return null;
  const d = Math.floor(left / 86400);
  const h = Math.floor((left % 86400) / 3600);
  const m = Math.floor((left % 3600) / 60);
  const s = left % 60;
  const label = (sessionName && sessionName.trim()) || "Result";
  return (
    <div className="card">
      <p className="kicker">Results sealed</p>
      <h3>{label}</h3>
      <p className="hint" aria-live="polite">
        {label} will be available in: {d} Days {h} Hours {m} Minutes
      </p>
      <div className="countdown" aria-live="polite">
        <div className="countdown-cell"><b>{pad(d)}</b><span>Days</span></div>
        <div className="countdown-cell"><b>{pad(h)}</b><span>Hours</span></div>
        <div className="countdown-cell"><b>{pad(m)}</b><span>Minutes</span></div>
        <div className="countdown-cell"><b>{pad(s)}</b><span>Seconds</span></div>
      </div>
    </div>
  );
}

export function isUpcomingExam(announceAt?: string, seconds?: number, _published?: boolean) {
  if (!announceAt) return false;
  const at = new Date(announceAt).getTime();
  if (Number.isNaN(at) || at <= Date.now()) return false;
  if (seconds != null && seconds <= 0) return false;
  return true;
}

export function pickResultSession<T extends { id: string; name: string; published?: boolean; announceAt?: string }>(rows: T[]) {
  if (!rows.length) return "";
  const upcomingFinal = rows.find((s) => /final/i.test(s.name) && isUpcomingExam(s.announceAt, undefined, s.published));
  if (upcomingFinal) return upcomingFinal.id;
  const anyUpcoming = rows.find((s) => isUpcomingExam(s.announceAt, undefined, s.published));
  if (anyUpcoming) return anyUpcoming.id;
  const publishedFinal = rows.find((s) => /final/i.test(s.name) && s.published);
  if (publishedFinal) return publishedFinal.id;
  const published = rows.find((s) => s.published);
  return (published || rows[0]).id;
}
