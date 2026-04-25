"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { messages, type Locale, type TranslationKey } from "@/lib/i18n/messages";

const STORAGE_KEY = "lang";

type Params = Record<string, string | number>;

type LanguageContextValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: TranslationKey, params?: Params) => string;
  localeTag: string;
};

const LanguageContext = createContext<LanguageContextValue | null>(null);

function detectLocale(): Locale {
  if (typeof window === "undefined") return "en";
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "zh" || stored === "en") return stored;
  return navigator.language.toLowerCase().startsWith("zh") ? "zh" : "en";
}

function fillParams(template: string, params?: Params): string {
  if (!params) return template;
  let text = template;
  Object.entries(params).forEach(([k, v]) => {
    text = text.replaceAll(`{${k}}`, String(v));
  });
  return text;
}

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  // Keep SSR and first client render deterministic to avoid hydration mismatch.
  const [locale, setLocaleState] = useState<Locale>("en");

  useEffect(() => {
    const next = detectLocale();
    setLocaleState(next);
    document.documentElement.lang = next === "zh" ? "zh-CN" : "en";
  }, []);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    if (typeof window !== "undefined") {
      localStorage.setItem(STORAGE_KEY, next);
      document.documentElement.lang = next === "zh" ? "zh-CN" : "en";
    }
  }, []);

  const t = useCallback(
    (key: TranslationKey, params?: Params) => {
      const template = messages[locale][key] ?? messages.en[key] ?? String(key);
      return fillParams(template, params);
    },
    [locale]
  );

  const localeTag = locale === "zh" ? "zh-CN" : "en-US";

  const value = useMemo(
    () => ({
      locale,
      setLocale,
      t,
      localeTag,
    }),
    [locale, setLocale, t, localeTag]
  );

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
  const ctx = useContext(LanguageContext);
  if (!ctx) {
    throw new Error("useLanguage must be used within LanguageProvider");
  }
  return ctx;
}
