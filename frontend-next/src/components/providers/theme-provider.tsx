"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

type Theme = "light" | "dark";
type ThemeMode = Theme | "system";

type ThemeContextValue = {
  theme: Theme;
  themeMode: ThemeMode;
  setTheme: (theme: Theme) => void;
  setThemeMode: (mode: ThemeMode) => void;
  toggleTheme: () => void;
};

const STORAGE_KEY = "theme";
const ThemeContext = createContext<ThemeContextValue | null>(null);

const themeVariables: Record<Theme, Record<string, string>> = {
  light: {
    "--color-background-primary": "#faf9f6",
    "--color-background-secondary": "#faf9f6",
    "--color-background-tertiary": "#f5f4ef",
    "--color-background-info": "#e9f0f3",
    "--color-background-danger": "#fde8e5",
    "--color-background-success": "#e5f4ea",
    "--color-background-warning": "#fbf0d9",
    "--color-sidebar-background": "#eae7e1",
    "--color-sidebar-hover-background": "#eeebe5",
    "--color-sidebar-active-background": "#e5e2dc",
    "--color-sidebar-active-text": "#1a1917",
    "--color-user-bubble-background": "#e5e2dc",
    "--color-user-bubble-text": "#1a1917",
    "--color-accent-highlight": "#8a6a40",
    "--color-text-primary": "#1a1917",
    "--color-text-secondary": "#6b6a65",
    "--color-text-tertiary": "#9c9a93",
    "--color-text-info": "#4a7fa5",
    "--color-text-danger": "#8a2f21",
    "--color-text-success": "#3d7a5a",
    "--color-text-warning": "#8a6030",
    "--color-border-tertiary": "rgba(0, 0, 0, 0.1)",
    "--color-border-secondary": "rgba(0, 0, 0, 0.18)",
    "--color-border-primary": "rgba(0, 0, 0, 0.28)",
    "--color-border-info": "#4a7fa5",
    "--color-focus-ring": "rgba(0, 0, 0, 0.12)",
    "--color-button-hover-background": "rgba(0, 0, 0, 0.04)",
  },
  dark: {
    "--color-background-primary": "#1f1e1b",
    "--color-background-secondary": "#1f1e1b",
    "--color-background-tertiary": "#141412",
    "--color-background-info": "#1a2530",
    "--color-background-danger": "#2a1f1f",
    "--color-background-success": "#1a2a1f",
    "--color-background-warning": "#2a2215",
    "--color-sidebar-background": "#1c1b18",
    "--color-sidebar-hover-background": "#232220",
    "--color-sidebar-active-background": "#2a2925",
    "--color-sidebar-active-text": "#f0ede6",
    "--color-user-bubble-background": "#2a2925",
    "--color-user-bubble-text": "#f0ede6",
    "--color-accent-highlight": "#8a6a40",
    "--color-text-primary": "#f0ede6",
    "--color-text-secondary": "#9c9a93",
    "--color-text-tertiary": "#5c5a54",
    "--color-text-info": "#6ba3cc",
    "--color-text-danger": "#cc6b6b",
    "--color-text-success": "#6bab7c",
    "--color-text-warning": "#ccaa6b",
    "--color-border-tertiary": "rgba(255, 255, 255, 0.1)",
    "--color-border-secondary": "rgba(255, 255, 255, 0.18)",
    "--color-border-primary": "rgba(255, 255, 255, 0.28)",
    "--color-border-info": "#85b7eb",
    "--color-focus-ring": "rgba(255, 255, 255, 0.12)",
    "--color-button-hover-background": "rgba(255, 255, 255, 0.06)",
  },
};

function resolveTheme(mode: ThemeMode): Theme {
  if (mode === "system") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  return mode;
}

function detectThemeMode(): ThemeMode {
  if (typeof window === "undefined") return "system";
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "light" || stored === "dark" || stored === "system") return stored;
  return "system";
}

function applyTheme(theme: Theme) {
  const root = document.documentElement;
  const body = document.body;

  root.dataset.theme = theme;
  body.dataset.theme = theme;
  root.style.colorScheme = theme;

  Object.entries(themeVariables[theme]).forEach(([name, value]) => {
    root.style.setProperty(name, value);
  });
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [themeMode, setThemeModeState] = useState<ThemeMode>("system");
  const [theme, setThemeState] = useState<Theme>("light");

  useEffect(() => {
    const nextMode = detectThemeMode();
    const nextTheme = resolveTheme(nextMode);
    setThemeModeState(nextMode);
    setThemeState(nextTheme);
    applyTheme(nextTheme);
  }, []);

  useEffect(() => {
    if (themeMode !== "system") return;

    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const updateFromSystem = () => {
      const nextTheme = mediaQuery.matches ? "dark" : "light";
      setThemeState(nextTheme);
      applyTheme(nextTheme);
    };

    mediaQuery.addEventListener("change", updateFromSystem);
    return () => mediaQuery.removeEventListener("change", updateFromSystem);
  }, [themeMode]);

  const setThemeMode = useCallback((nextMode: ThemeMode) => {
    const nextTheme = resolveTheme(nextMode);
    setThemeModeState(nextMode);
    setThemeState(nextTheme);
    localStorage.setItem(STORAGE_KEY, nextMode);
    applyTheme(nextTheme);
  }, []);

  const setTheme = useCallback(
    (nextTheme: Theme) => {
      setThemeMode(nextTheme);
    },
    [setThemeMode]
  );

  const toggleTheme = useCallback(() => {
    setThemeMode(theme === "dark" ? "light" : "dark");
  }, [setThemeMode, theme]);

  const value = useMemo(
    () => ({
      theme,
      themeMode,
      setTheme,
      setThemeMode,
      toggleTheme,
    }),
    [setTheme, setThemeMode, theme, themeMode, toggleTheme]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useTheme must be used within ThemeProvider");
  }
  return ctx;
}
