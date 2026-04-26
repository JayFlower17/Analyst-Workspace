import * as React from "react";
import { Input as InputPrimitive } from "@base-ui/react/input";
import { cn } from "@/lib/utils";

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(
        "h-10 w-full min-w-0 rounded-[var(--border-radius-md)] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)] px-3 py-2 text-[15px] text-[color:var(--color-text-primary)] transition-[background-color,border-color,box-shadow] duration-150 outline-none placeholder:text-[color:var(--color-text-tertiary)] hover:border-[color:var(--color-border-secondary)] hover:bg-[color:var(--color-sidebar-hover-background)] focus-visible:border-[color:rgba(242,201,76,0.3)] focus-visible:shadow-[0_0_0_2px_var(--color-focus-ring)] disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium",
        className
      )}
      {...props}
    />
  );
}

export { Input };
