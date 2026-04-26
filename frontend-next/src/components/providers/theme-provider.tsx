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
    "--color-background-primary": "#1E1E1E",
    "--color-background-secondary": "#1A1A1A",
    "--color-background-tertiary": "#121212",
    "--color-background-info": "rgba(242, 201, 76, 0.08)",
    "--color-background-danger": "rgba(204, 107, 107, 0.12)",
    "--color-background-success": "rgba(107, 171, 124, 0.1)",
    "--color-background-warning": "rgba(242, 201, 76, 0.12)",
    "--color-sidebar-background": "#151515",
    "--color-sidebar-hover-background": "#202020",
    "--color-sidebar-active-background": "#232323",
    "--color-sidebar-active-text": "#F5F5F5",
    "--color-user-bubble-background": "#232323",
    "--color-user-bubble-text": "#F5F5F5",
    "--color-accent-highlight": "#F2C94C",
    "--color-text-primary": "#F5F5F5",
    "--color-text-secondary": "#828282",
    "--color-text-tertiary": "#5F5F5F",
    "--color-text-info": "#F2C94C",
    "--color-text-danger": "#D36F6F",
    "--color-text-success": "#7DB28A",
    "--color-text-warning": "#F2C94C",
    "--color-border-tertiary": "rgba(255, 255, 255, 0.06)",
    "--color-border-secondary": "rgba(255, 255, 255, 0.1)",
    "--color-border-primary": "rgba(255, 255, 255, 0.18)",
    "--color-border-info": "rgba(242, 201, 76, 0.55)",
    "--color-focus-ring": "rgba(242, 201, 76, 0.2)",
    "--color-button-hover-background": "rgba(255, 255, 255, 0.04)",
  },
};

function resolveTheme(mode: ThemeMode): Theme {
  if (mode === "system") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  return mode;
}

function detectThemeMode(): ThemeMode {
  return "dark";
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
  const [themeMode, setThemeModeState] = useState<ThemeMode>("dark");
  const [theme, setThemeState] = useState<Theme>("dark");

  useEffect(() => {
    const nextMode = detectThemeMode();
    const nextTheme = resolveTheme(nextMode);
    setThemeModeState(nextMode);
    setThemeState(nextTheme);
    localStorage.setItem(STORAGE_KEY, "dark");
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
