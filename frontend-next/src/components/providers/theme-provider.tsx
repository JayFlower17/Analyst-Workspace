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

const STORAGE_KEY = "analyst-workspace-theme-v2";
const ThemeContext = createContext<ThemeContextValue | null>(null);

const themeVariables: Record<Theme, Record<string, string>> = {
  light: {
    "--color-background-primary": "#f9f7f1",
    "--color-background-secondary": "#eee9df",
    "--color-background-tertiary": "#f4f0e8",
    "--color-background-info": "#f4ded4",
    "--color-background-danger": "#f8e0dc",
    "--color-background-success": "#e3efe5",
    "--color-background-warning": "#f3ead5",
    "--color-sidebar-background": "#ece7dd",
    "--color-sidebar-hover-background": "#ded7ca",
    "--color-sidebar-active-background": "#d7d0c4",
    "--color-sidebar-active-text": "#171512",
    "--color-user-bubble-background": "#efe0bd",
    "--color-user-bubble-text": "#171512",
    "--color-accent-highlight": "#cc7d5f",
    "--color-text-primary": "#171512",
    "--color-text-secondary": "#5f5a52",
    "--color-text-tertiary": "#8a8378",
    "--color-text-info": "#a95e43",
    "--color-text-danger": "#b64d43",
    "--color-text-success": "#31734f",
    "--color-text-warning": "#91652d",
    "--color-border-tertiary": "rgba(38, 34, 28, 0.1)",
    "--color-border-secondary": "rgba(38, 34, 28, 0.18)",
    "--color-border-primary": "rgba(38, 34, 28, 0.28)",
    "--color-border-info": "#cc7d5f",
    "--color-focus-ring": "rgba(204, 125, 95, 0.18)",
    "--color-button-hover-background": "rgba(23, 21, 18, 0.055)",
    "--color-grid-line": "rgba(38, 34, 28, 0.08)",
  },
  dark: {
    "--color-background-primary": "#2d2d2b",
    "--color-background-secondary": "#363633",
    "--color-background-tertiary": "#242421",
    "--color-background-info": "rgba(204, 125, 95, 0.18)",
    "--color-background-danger": "rgba(224, 103, 90, 0.16)",
    "--color-background-success": "rgba(113, 171, 128, 0.14)",
    "--color-background-warning": "#483a2c",
    "--color-sidebar-background": "#242421",
    "--color-sidebar-hover-background": "#343431",
    "--color-sidebar-active-background": "#3d3d39",
    "--color-sidebar-active-text": "#f9f9f7",
    "--color-user-bubble-background": "#4a3c2d",
    "--color-user-bubble-text": "#f9f9f7",
    "--color-accent-highlight": "#cc7d5f",
    "--color-text-primary": "#f9f9f7",
    "--color-text-secondary": "#b9b6ae",
    "--color-text-tertiary": "#8e8a82",
    "--color-text-info": "#d88b70",
    "--color-text-danger": "#e27669",
    "--color-text-success": "#8fcf9d",
    "--color-text-warning": "#d3a56d",
    "--color-border-tertiary": "rgba(249, 249, 247, 0.08)",
    "--color-border-secondary": "rgba(249, 249, 247, 0.13)",
    "--color-border-primary": "rgba(249, 249, 247, 0.2)",
    "--color-border-info": "#cc7d5f",
    "--color-focus-ring": "rgba(204, 125, 95, 0.24)",
    "--color-button-hover-background": "rgba(249, 249, 247, 0.07)",
    "--color-grid-line": "rgba(249, 249, 247, 0.06)",
  },
};

function resolveTheme(mode: ThemeMode): Theme {
  if (mode === "system") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  return mode;
}

function detectThemeMode(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "light" || stored === "dark" || stored === "system") return stored;
  return "light";
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
  const [themeMode, setThemeModeState] = useState<ThemeMode>("light");
  const [theme, setThemeState] = useState<Theme>("light");

  useEffect(() => {
    const nextMode = detectThemeMode();
    const nextTheme = resolveTheme(nextMode);
    setThemeModeState(nextMode);
    setThemeState(nextTheme);
    localStorage.setItem(STORAGE_KEY, nextMode);
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
