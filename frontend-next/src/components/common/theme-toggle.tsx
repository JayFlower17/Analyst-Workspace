"use client";

import { Moon, Sun } from "lucide-react";
import { cn } from "@/lib/utils";
import { useTheme } from "@/components/providers/theme-provider";
import { useLanguage } from "@/components/providers/language-provider";

type ThemeToggleProps = {
  className?: string;
  iconOnly?: boolean;
};

export function ThemeToggle({ className, iconOnly = false }: ThemeToggleProps) {
  const { theme, toggleTheme } = useTheme();
  const { t } = useLanguage();
  const isDark = theme === "dark";

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={isDark ? t("switch_to_light") : t("switch_to_dark")}
      title={isDark ? t("switch_to_light") : t("switch_to_dark")}
      className={cn(
        iconOnly
          ? "inline-flex size-8 items-center justify-center rounded-[7px] bg-transparent text-[color:var(--color-text-secondary)] transition-[background-color,color,transform,box-shadow] duration-150 hover:bg-[color:var(--color-button-hover-background)] hover:text-[color:var(--color-text-primary)] active:scale-[0.98] focus-visible:shadow-[0_0_0_2px_var(--color-focus-ring)]"
          : "inline-flex h-9 items-center justify-center gap-2 rounded-[var(--border-radius-md)] border [border-width:0.5px] border-[color:var(--color-border-secondary)] bg-transparent px-[14px] py-[7px] text-[13px] font-medium text-[color:var(--color-text-primary)] transition-[background-color,border-color,color,transform,box-shadow] duration-150 hover:bg-[color:var(--color-button-hover-background)] active:scale-[0.98] focus-visible:shadow-[0_0_0_2px_var(--color-focus-ring)]",
        className
      )}
    >
      {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
      {iconOnly ? null : isDark ? t("light_mode") : t("dark_mode")}
    </button>
  );
}
