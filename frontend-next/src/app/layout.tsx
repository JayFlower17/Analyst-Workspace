import type { Metadata } from "next";
import { Toaster } from "sonner";
import { LanguageProvider } from "@/components/providers/language-provider";
import { ThemeProvider } from "@/components/providers/theme-provider";
import "./globals.css";

export const metadata: Metadata = {
  title: "Agent analytics workspace",
  description: "AI-native analytics frontend powered by the existing backend APIs",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen bg-background font-sans text-foreground">
        <ThemeProvider>
          <LanguageProvider>
            {children}
            <Toaster
              position="top-right"
              theme="light"
              toastOptions={{
                style: {
                  background: "var(--color-background-primary)",
                  border: "0.5px solid var(--color-border-tertiary)",
                  color: "var(--color-text-primary)",
                  borderRadius: "var(--border-radius-lg)",
                  boxShadow: "none",
                },
              }}
            />
          </LanguageProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
