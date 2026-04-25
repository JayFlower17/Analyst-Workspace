"use client";

import { useEffect } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function GlobalAppError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Keep raw stack in console for debugging.
    console.error("App error boundary caught:", error);
  }, [error]);

  return (
    <div className="grid min-h-screen place-items-center px-4 py-8">
      <Card className="w-full max-w-2xl">
        <CardHeader className="space-y-3">
          <div className="flex items-center gap-2 text-[color:var(--color-text-danger)]">
            <AlertTriangle className="h-5 w-5" />
            <CardTitle>页面发生错误 / Something went wrong</CardTitle>
          </div>
          <CardDescription>可以点击重试，或查看控制台日志进一步定位问题。</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-[var(--border-radius-md)] bg-[color:var(--color-background-secondary)] p-4 text-[14px] text-[color:var(--color-text-primary)]">
            {error.message || "Unknown error"}
          </div>
          {error.digest ? (
            <p className="mt-2 text-[12px] font-normal text-[color:var(--color-text-secondary)]">Digest: {error.digest}</p>
          ) : null}
          <div className="mt-4">
            <Button type="button" variant="secondary" onClick={() => reset()}>
              <RefreshCw className="h-4 w-4" />
              重试 / Retry
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
