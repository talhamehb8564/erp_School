import { useEffect, useState, type PointerEvent as ReactPointerEvent } from "react";

const KEY = "atrium.sidebarWidth";
export const SIDEBAR_MIN = 180;
export const SIDEBAR_MAX = 420;
export const SIDEBAR_DEFAULT = 248;

function clamp(n: number) {
  return Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, Math.round(n)));
}

function readStored() {
  const n = Number(localStorage.getItem(KEY));
  return Number.isFinite(n) ? clamp(n) : SIDEBAR_DEFAULT;
}

export function useSidebarWidth() {
  const [width, setWidth] = useState(readStored);

  useEffect(() => {
    document.documentElement.style.setProperty("--sidebar", `${width}px`);
    localStorage.setItem(KEY, String(width));
  }, [width]);

  const onResizePointerDown = (e: ReactPointerEvent<HTMLButtonElement>) => {
    if (window.matchMedia("(max-width: 820px)").matches) return;
    e.preventDefault();
    const startX = e.clientX;
    const startW = width;
    const move = (ev: PointerEvent) => setWidth(clamp(startW + ev.clientX - startX));
    const up = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  };

  return { width, onResizePointerDown };
}
