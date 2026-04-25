import { Button as ButtonPrimitive } from "@base-ui/react/button";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "group/button inline-flex shrink-0 items-center justify-center gap-2 rounded-[var(--border-radius-md)] border [border-width:0.5px] text-[13px] font-medium whitespace-nowrap transition-[background-color,border-color,color,transform,box-shadow] duration-150 outline-none select-none active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 focus-visible:shadow-[0_0_0_2px_var(--color-focus-ring)] [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default:
          "border-[color:var(--color-border-secondary)] bg-transparent text-[color:var(--color-text-primary)] hover:border-[color:var(--color-border-secondary)] hover:bg-[color:var(--color-button-hover-background)]",
        outline:
          "border-[color:var(--color-border-secondary)] bg-transparent text-[color:var(--color-text-primary)] hover:border-[color:var(--color-border-secondary)] hover:bg-[color:var(--color-button-hover-background)]",
        secondary:
          "border-[color:var(--color-border-secondary)] bg-transparent text-[color:var(--color-text-primary)] hover:border-[color:var(--color-border-secondary)] hover:bg-[color:var(--color-button-hover-background)]",
        ghost:
          "border-transparent bg-transparent text-[color:var(--color-text-secondary)] hover:bg-[color:var(--color-button-hover-background)] hover:text-[color:var(--color-text-primary)]",
        destructive:
          "border-[color:var(--color-text-danger)] bg-transparent text-[color:var(--color-text-danger)] hover:bg-[color:var(--color-background-danger)]",
        link: "border-transparent bg-transparent px-0 text-[color:var(--color-text-info)] hover:text-[color:var(--color-text-primary)]",
      },
      size: {
        default: "h-9 px-[14px] py-[7px]",
        xs: "h-7 px-2.5 text-[13px]",
        sm: "h-8 px-3 text-[13px]",
        lg: "h-10 px-4 text-[13px]",
        icon: "size-9",
        "icon-xs": "size-7",
        "icon-sm": "size-8",
        "icon-lg": "size-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

function Button({
  className,
  variant = "default",
  size = "default",
  ...props
}: ButtonPrimitive.Props & VariantProps<typeof buttonVariants>) {
  return (
    <ButtonPrimitive
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  );
}

export { Button, buttonVariants };
