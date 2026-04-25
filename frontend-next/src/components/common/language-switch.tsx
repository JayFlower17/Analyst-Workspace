"use client";

import { Languages } from "lucide-react";
import { cn } from "@/lib/utils";
import { useLanguage } from "@/components/providers/language-provider";

export function LanguageSwitch({ className }: { className?: string }) {
  const { locale, setLocale, t } = useLanguage();

  return (
    <div
      className={cn(
        "inline-flex items-center gap-1 rounded-[var(--border-radius-md)] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] p-1",
        className
      )}
      aria-label={t("language")}
    >
      <Languages className="ml-1 h-4 w-4 text-[color:var(--color-text-secondary)]" />
      <button
        type="button"
        onClick={() => setLocale("zh")}
        className={cn(
          "rounded-[var(--border-radius-md)] px-[14px] py-[7px] text-[13px] font-medium transition-colors duration-150",
          locale === "zh"
            ? "bg-[color:var(--color-background-info)] text-[color:var(--color-text-info)]"
            : "text-[color:var(--color-text-primary)] hover:bg-[color:var(--color-button-hover-background)]"
        )}
      >
        中文
      </button>
      <button
        type="button"
        onClick={() => setLocale("en")}
        className={cn(
          "rounded-[var(--border-radius-md)] px-[14px] py-[7px] text-[13px] font-medium transition-colors duration-150",
          locale === "en"
            ? "bg-[color:var(--color-background-info)] text-[color:var(--color-text-info)]"
            : "text-[color:var(--color-text-primary)] hover:bg-[color:var(--color-button-hover-background)]"
        )}
      >
        EN
      </button>
    </div>
  );
}
