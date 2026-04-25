"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { ArrowUp, FolderUp, Loader2, Rocket, Upload } from "lucide-react";
import { chatApi } from "@/lib/api/client";
import type { AnalysisResult, ChatMessage, ChatSession, Dataset } from "@/lib/types";
import { cn } from "@/lib/utils";
import { ThemeToggle } from "@/components/common/theme-toggle";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useLanguage } from "@/components/providers/language-provider";

function formatTime(raw: string | undefined, localeTag: string) {
  if (!raw) return "";
  const d = new Date(raw);
  if (Number.isNaN(d.valueOf())) return "";
  return d.toLocaleString(localeTag, { hour: "2-digit", minute: "2-digit" });
}

function parseUserLabel() {
  if (typeof window === "undefined") return "AI";
  const rawUser = localStorage.getItem("user");
  if (!rawUser) return "AI";

  try {
    const parsed = JSON.parse(rawUser) as { username?: string; email?: string };
    return (parsed.username || parsed.email || "AI").slice(0, 2).toUpperCase() || "AI";
  } catch {
    return "AI";
  }
}

function ChatPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t, localeTag } = useLanguage();

  const sessionParam = useMemo(() => {
    const raw = searchParams.get("session");
    const parsed = Number(raw);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [searchParams]);

  const [userLabel, setUserLabel] = useState("AI");
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState<number | undefined>(undefined);
  const [input, setInput] = useState("");
  const [analyzing, setAnalyzing] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [lastResult, setLastResult] = useState<AnalysisResult | null>(null);

  const [promoteOpen, setPromoteOpen] = useState(false);
  const [workspaceName, setWorkspaceName] = useState("");
  const [workspaceDesc, setWorkspaceDesc] = useState("");
  const [promoting, setPromoting] = useState(false);

  const [assistantTyping, setAssistantTyping] = useState(false);
  const [streamText, setStreamText] = useState("");
  const streamTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const messageBottomRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const activeSession = sessions.find((session) => session.id === activeSessionId);

  const initialize = useCallback(async () => {
    setLoadingSessions(true);
    try {
      const res = await chatApi.getSessions();
      setSessions(res.data ?? []);
    } catch (error) {
      toast.error((error as Error).message || t("failed_load_sessions"));
    } finally {
      setLoadingSessions(false);
    }
  }, [t]);

  useEffect(() => {
    setUserLabel(parseUserLabel());
    void initialize();
    return () => {
      if (streamTimerRef.current) {
        clearInterval(streamTimerRef.current);
      }
    };
  }, [initialize]);

  useEffect(() => {
    messageBottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, streamText, assistantTyping]);

  useEffect(() => {
    if (loadingSessions) return;

    if (!sessions.length) {
      setActiveSessionId(null);
      setMessages([]);
      setDatasets([]);
      setLastResult(null);
      return;
    }

    if (sessionParam && sessions.some((session) => session.id === sessionParam)) {
      setActiveSessionId(sessionParam);
      return;
    }

    const fallbackSessionId = sessions[0].id;
    setActiveSessionId(fallbackSessionId);
    router.replace(`/chat?session=${fallbackSessionId}`);
  }, [loadingSessions, router, sessionParam, sessions]);

  const loadSessionPayload = useCallback(
    async (sessionId: number) => {
      setLoadingMessages(true);
      try {
        const [mRes, dRes] = await Promise.all([chatApi.getMessages(sessionId), chatApi.getDatasets(sessionId)]);
        setMessages(mRes.data ?? []);
        const nextDatasets = dRes.data ?? [];
        setDatasets(nextDatasets);
        if (selectedDatasetId && !nextDatasets.some((item) => item.id === selectedDatasetId)) {
          setSelectedDatasetId(undefined);
        }
      } catch (error) {
        toast.error((error as Error).message || t("failed_load_session_details"));
      } finally {
        setLoadingMessages(false);
      }
    },
    [selectedDatasetId, t]
  );

  useEffect(() => {
    if (!activeSessionId) return;
    void loadSessionPayload(activeSessionId);
  }, [activeSessionId, loadSessionPayload]);

  const ensureSession = useCallback(async () => {
    if (activeSessionId) return activeSessionId;

    try {
      const created = await chatApi.createSession(t("new_chat_session"));
      const next = created.data;
      if (!next) return null;
      setSessions((prev) => [next, ...prev]);
      setActiveSessionId(next.id);
      router.replace(`/chat?session=${next.id}`);
      return next.id;
    } catch (error) {
      toast.error((error as Error).message || t("failed_create_session"));
      return null;
    }
  }, [activeSessionId, router, t]);

  async function sendMessage() {
    const query = input.trim();
    if (!query) return;

    const sessionId = await ensureSession();
    if (!sessionId) {
      toast.error(t("no_active_session"));
      return;
    }

    setAnalyzing(true);
    setAssistantTyping(false);
    setStreamText("");
    setMessages((prev) => [...prev, { id: Date.now(), role: "USER", content: query, sessionId }]);
    setInput("");

    try {
      const res = await chatApi.analyze(sessionId, query, selectedDatasetId);
      setLastResult(res);
      if (res.success) {
        const summary = res.summary?.trim() || t("analysis_complete");
        await runTypewriter(summary);
        await initialize();
        await loadSessionPayload(sessionId);
      } else {
        toast.error(res.message || t("analysis_failed"));
      }
    } catch (error) {
      toast.error((error as Error).message || t("analysis_request_failed"));
    } finally {
      setAnalyzing(false);
    }
  }

  function runTypewriter(text: string) {
    return new Promise<void>((resolve) => {
      if (streamTimerRef.current) clearInterval(streamTimerRef.current);
      setAssistantTyping(true);
      setStreamText("");
      let index = 0;
      streamTimerRef.current = setInterval(() => {
        index += 1;
        setStreamText(text.slice(0, index));
        if (index >= text.length) {
          if (streamTimerRef.current) clearInterval(streamTimerRef.current);
          streamTimerRef.current = null;
          setAssistantTyping(false);
          resolve();
        }
      }, 16);
    });
  }

  async function triggerUpload(file?: File) {
    const sessionId = await ensureSession();
    if (!sessionId || !file) return;
    setUploading(true);
    try {
      const res = await chatApi.uploadDataset(sessionId, file, file.name);
      if (res.success) {
        toast.success(t("dataset_uploaded_chat"));
        await initialize();
        await loadSessionPayload(sessionId);
      } else {
        toast.error(res.message || t("upload_failed"));
      }
    } catch (error) {
      toast.error((error as Error).message || t("upload_failed"));
    } finally {
      setUploading(false);
    }
  }

  async function promoteToWorkspace() {
    if (!activeSessionId) return;
    if (!workspaceName.trim()) {
      toast.error(t("workspace_name_required"));
      return;
    }
    setPromoting(true);
    try {
      const res = await chatApi.promote(activeSessionId, workspaceName.trim(), workspaceDesc.trim());
      if (res.success && res.data?.id) {
        toast.success(t("promoted_to_workplace", { name: res.data.name }));
        window.location.href = `/workspace?groupId=${res.data.id}`;
      } else {
        toast.error(res.message || t("promotion_failed"));
      }
    } catch (error) {
      toast.error((error as Error).message || t("promotion_failed"));
    } finally {
      setPromoting(false);
    }
  }

  return (
    <div className="flex min-h-[calc(100vh-4.5rem)] flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="min-w-0 truncate text-[15px] font-medium text-[color:var(--color-text-primary)]">
          {activeSession?.title || t("new_chat")}
        </h2>
        <div className="flex items-center gap-2">
          <ThemeToggle iconOnly />
          <div className="grid size-8 place-items-center rounded-full bg-[color:var(--color-background-info)] text-[13px] font-medium text-[color:var(--color-text-info)]">
            {userLabel}
          </div>
        </div>
      </div>

      {activeSessionId ? (
        <div className="flex flex-wrap items-center gap-2">
          <select
            className="control-select max-w-[280px] flex-1"
            value={selectedDatasetId ?? ""}
            onChange={(e) => {
              const next = Number(e.target.value);
              setSelectedDatasetId(Number.isFinite(next) && next > 0 ? next : undefined);
            }}
          >
            <option value="">{t("auto_dataset_selection")}</option>
            {datasets.map((dataset) => (
              <option key={dataset.id} value={dataset.id}>
                {dataset.name} ({dataset.tableName})
              </option>
            ))}
          </select>
          <Button variant="secondary" disabled={uploading} onClick={() => fileInputRef.current?.click()}>
            {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
            {t("upload")}
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              if (!datasets.length) {
                toast.error(t("upload_dataset_first"));
                return;
              }
              setWorkspaceName(`${t("workspace_prefix")}-${new Date().toISOString().slice(0, 10)}`);
              setWorkspaceDesc("");
              setPromoteOpen(true);
            }}
          >
            <Rocket className="h-4 w-4" />
            {t("promote_to_workplace")}
          </Button>
        </div>
      ) : null}

      <div className="surface-card flex flex-1 flex-col gap-6">
        <div className="flex-1 space-y-4">
          {loadingSessions || (activeSessionId && loadingMessages) ? (
            <>
              <Skeleton className="h-16 w-3/4" />
              <Skeleton className="ml-auto h-16 w-2/3" />
              <Skeleton className="h-12 w-1/2" />
            </>
          ) : messages.length ? (
            messages.map((message) => (
              <div key={message.id} className={cn("flex", message.role.toUpperCase() === "USER" ? "justify-end" : "justify-start")}>
                <div
                  className={cn(
                    "text-[14px] leading-[1.7]",
                    message.role.toUpperCase() === "USER"
                      ? "max-w-[60%] rounded-[10px] bg-[color:var(--color-user-bubble-background)] px-[13px] py-[9px] font-normal text-[color:var(--color-user-bubble-text)]"
                      : "max-w-[80%] bg-transparent px-0 py-0 font-normal text-[color:var(--color-text-primary)]"
                  )}
                >
                  {message.content}
                </div>
              </div>
            ))
          ) : (
            <div className="py-10">
              <p className="text-[15px] font-medium text-[color:var(--color-text-primary)]">{t("chat_welcome_title")}</p>
              <p className="mt-2 text-[14px] leading-[1.6] text-[color:var(--color-text-secondary)]">{t("chat_welcome_body")}</p>
            </div>
          )}

          {assistantTyping ? (
            <div className="flex justify-start">
              <div className="max-w-[80%] bg-transparent px-0 py-0 text-[14px] leading-[1.7] font-normal text-[color:var(--color-text-primary)]">
                {streamText}
                <span className="ml-1 inline-block animate-pulse">▋</span>
              </div>
            </div>
          ) : null}

          {lastResult?.generatedCodeOrSql || lastResult?.generatedSql ? (
            <div className="space-y-2">
              <p className="text-[12px] font-normal text-[color:var(--color-text-secondary)]">{t("latest_result_snapshot")}</p>
              <pre className="overflow-x-auto whitespace-pre-wrap rounded-[var(--border-radius-md)] bg-[color:var(--color-background-secondary)] p-4 text-[13px] leading-6 text-[color:var(--color-text-primary)]">
                {lastResult.generatedCodeOrSql || lastResult.generatedSql}
              </pre>
            </div>
          ) : null}

          <div ref={messageBottomRef} />
        </div>
      </div>

      <div className="surface-card space-y-4">
        <Textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={t("ask_agent_placeholder")}
          className="min-h-[120px] resize-none"
          onKeyDown={(e) => {
            if (e.ctrlKey && e.key === "Enter") {
              e.preventDefault();
              void sendMessage();
            }
          }}
        />
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="mono-note">
            {activeSession ? formatTime(activeSession.updatedAt, localeTag) : t("ctrl_enter_run")}
          </span>
          <Button onClick={() => void sendMessage()} disabled={analyzing || !input.trim()}>
            {analyzing ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowUp className="h-4 w-4" />}
            {t("run")} ↗
          </Button>
        </div>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept=".csv,.xlsx,.xls"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          void triggerUpload(file);
          if (event.target) event.target.value = "";
        }}
      />

      <Dialog open={promoteOpen} onOpenChange={setPromoteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("promote_dialog_title")}</DialogTitle>
            <DialogDescription>{t("promote_dialog_desc")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Input value={workspaceName} onChange={(e) => setWorkspaceName(e.target.value)} placeholder={t("workspace_name")} />
            <Textarea
              value={workspaceDesc}
              onChange={(e) => setWorkspaceDesc(e.target.value)}
              placeholder={t("description_optional")}
            />
          </div>
          <DialogFooter>
            <Button variant="secondary" onClick={() => setPromoteOpen(false)}>
              {t("cancel")}
            </Button>
            <Button onClick={() => void promoteToWorkspace()} disabled={promoting}>
              {promoting ? <Loader2 className="h-4 w-4 animate-spin" /> : <FolderUp className="h-4 w-4" />}
              {t("promote")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default function ChatPage() {
  return (
    <Suspense fallback={null}>
      <ChatPageContent />
    </Suspense>
  );
}
