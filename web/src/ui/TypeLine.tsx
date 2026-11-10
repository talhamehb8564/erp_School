import { useEffect, useState } from "react";

export default function TypeLine({
  text,
  className,
  as: Tag = "p",
  speed = 14,
}: {
  text: string;
  className?: string;
  as?: "p" | "span" | "h3";
  speed?: number;
}) {
  const [n, setN] = useState(0);
  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce || !text) {
      setN(text.length);
      return;
    }
    setN(0);
    let i = 0;
    const step = Math.max(1, Math.ceil(text.length / 220));
    const id = window.setInterval(() => {
      i = Math.min(text.length, i + step);
      setN(i);
      if (i >= text.length) window.clearInterval(id);
    }, speed);
    return () => window.clearInterval(id);
  }, [text, speed]);
  return (
    <Tag className={className}>
      {text.slice(0, n)}
      {n < text.length ? <span className="caret" aria-hidden /> : null}
    </Tag>
  );
}
