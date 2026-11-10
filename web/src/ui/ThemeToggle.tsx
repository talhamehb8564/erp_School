import { useState } from "react";
import { applyTheme, readTheme, type Theme } from "../lib/theme";

export default function ThemeToggle({ className = "icon-btn" }: { className?: string }) {
  const [theme, setTheme] = useState<Theme>(readTheme);
  return (
    <button
      type="button"
      className={className}
      title={theme === "dark" ? "Use light theme" : "Use dark theme"}
      aria-label={theme === "dark" ? "Use light theme" : "Use dark theme"}
      onClick={() => {
        const next = theme === "dark" ? "light" : "dark";
        applyTheme(next);
        setTheme(next);
      }}
    >
      {theme === "dark" ? "☀" : "☾"}
    </button>
  );
}
