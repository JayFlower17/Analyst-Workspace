import * as React from "react";
import { cn } from "@/lib/utils";

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "flex min-h-24 w-full rounded-[var(--border-radius-md)] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)] px-3 py-3 text-[15px] leading-7 text-[color:var(--color-text-primary)] transition-[background-color,border-color,box-shadow] duration-150 outline-none placeholder:text-[color:var(--color-text-tertiary)] hover:border-[color:var(--color-border-secondary)] hover:bg-[color:var(--color-sidebar-hover-background)] focus-visible:border-[color:rgba(242,201,76,0.3)] focus-visible:shadow-[0_0_0_2px_var(--color-focus-ring)] disabled:cursor-not-allowed disabled:opacity-50",
        className
      )}
      {...props}
    />
  );
}

export { Textarea };
