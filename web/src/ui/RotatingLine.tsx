import { useEffect, useState } from "react";

const DEFAULT = [
  "Manage your school smarter",
  "Everything in one place",
  "Faster school management",
  "Connected. Organised. Simple.",
];

export default function RotatingLine({ lines = DEFAULT, interval = 3200 }: { lines?: string[]; interval?: number }) {
  const [i, setI] = useState(0);
  const [on, setOn] = useState(true);
  useEffect(() => {
    if (lines.length < 2) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const id = window.setInterval(() => {
      setOn(false);
      window.setTimeout(() => {
        setI((n) => (n + 1) % lines.length);
        setOn(true);
      }, 280);
    }, interval);
    return () => window.clearInterval(id);
  }, [lines, interval]);
  return (
    <p className={`rotating-line ${on ? "on" : ""}`} aria-live="polite">
      {lines[i]}
    </p>
  );
}
