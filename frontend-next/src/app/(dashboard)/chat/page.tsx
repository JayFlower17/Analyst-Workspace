"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { ArrowUp, FolderUp, Loader2, Upload } from "lucide-react";
import { artifactApi, chatApi } from "@/lib/api/client";
import type { AnalysisResult, Artifact, ChatMessage, ChatSession, Dataset } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useLanguage } from "@/components/providers/language-provider";

function formatTime(raw: string | undefined, localeTag: string) {
  if (!raw) return "";
  const date = new Date(raw);
  if (Number.isNaN(date.valueOf())) return "";
  return date.toLocaleString(localeTag, {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function parsePreview(value?: string | null) {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value) as Record<string, unknown>[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function ResultTable({ rows }: { rows: Record<string, unknown>[] }) {
  const columns = rows.length ? Object.keys(rows[0]) : [];
  if (!rows.length) return null;

  return (
    <div className="mt-5 overflow-hidden border border-[color:var(--color-border-primary)] bg-[color:var(--color-background-primary)]">
      <div className="max-h-[280px] overflow-auto">
        <table className="min-w-full border-collapse text-left text-sm">
          <thead className="sticky top-0 bg-[color:var(--color-background-secondary)]">
            <tr>
              {columns.map((column) => (
                <th
                  key={column}
                  className="border-b border-[color:var(--color-border-primary)] px-4 py-3 font-mono text-[11px] font-black uppercase text-[color:var(--color-text-primary)]"
                >
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.slice(0, 10).map((row, rowIndex) => (
              <tr key={rowIndex} className="border-t border-[color:var(--color-border-tertiary)]">
                {columns.map((column) => (
                  <td key={column} className="px-4 py-3 text-[color:var(--color-text-secondary)]">
                    {String(row[column] ?? "")}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ChatPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t, localeTag } = useLanguage();

  const sessionParam = useMemo(() => {
    const raw = Number(searchParams.get("session"));
    return Number.isFinite(raw) && raw > 0 ? raw : null;
  }, [searchParams]);

  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState<number | undefined>(undefined);
  const [input, setInput] = useState("");
  const [lastResult, setLastResult] = useState<AnalysisResult | null>(null);
  const [recentArtifacts, setRecentArtifacts] = useState<Artifact[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [promoteOpen, setPromoteOpen] = useState(false);
  const [workspaceName, setWorkspaceName] = useState("");
  const [workspaceDesc, setWorkspaceDesc] = useState("");
  const [promoting, setPromoting] = useState(false);
  const [showCode, setShowCode] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const missingSessionRef = useRef<number | null>(null);

  const resetConversationView = useCallback(() => {
    setMessages([]);
    setDatasets([]);
    setSelectedDatasetId(undefined);
    setInput("");
    setLastResult(null);
    setRecentArtifacts([]);
    setShowCode(false);
  }, []);

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

  const loadSessionPayload = useCallback(
    async (sessionId: number) => {
      setLoadingMessages(true);
      try {
        const [messageRes, datasetRes, artifactRes] = await Promise.all([
          chatApi.getMessages(sessionId),
          chatApi.getDatasets(sessionId),
          artifactApi.recent({ sessionId, limit: 8 }),
        ]);
        const nextDatasets = datasetRes.data ?? [];
        setMessages(messageRes.data ?? []);
        setDatasets(nextDatasets);
        setRecentArtifacts(artifactRes.data ?? []);
        setSelectedDatasetId((currentDatasetId) =>
          currentDatasetId && !nextDatasets.some((dataset) => dataset.id === currentDatasetId)
            ? undefined
            : currentDatasetId
        );
      } catch (error) {
        toast.error((error as Error).message || t("failed_load_session_details"));
      } finally {
        setLoadingMessages(false);
      }
    },
    [t]
  );

  useEffect(() => {
    void initialize();
  }, [initialize]);

  useEffect(() => {
    const reloadSessions = () => {
      void initialize();
    };
    window.addEventListener("chat-sessions-changed", reloadSessions);
    return () => window.removeEventListener("chat-sessions-changed", reloadSessions);
  }, [initialize]);

  useEffect(() => {
    if (!scrollRef.current) return;
    scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages, lastResult, recentArtifacts]);

  useEffect(() => {
    if (loadingSessions) return;

    if (!sessions.length) {
      missingSessionRef.current = null;
      setActiveSessionId(null);
      resetConversationView();
      return;
    }

    if (sessionParam && sessions.some((session) => session.id === sessionParam)) {
      missingSessionRef.current = null;
      setActiveSessionId(sessionParam);
      return;
    }

    if (sessionParam && !sessions.some((session) => session.id === sessionParam)) {
      resetConversationView();
      setActiveSessionId(null);

      if (missingSessionRef.current !== sessionParam) {
        missingSessionRef.current = sessionParam;
        void initialize();
        return;
      }

      missingSessionRef.current = null;
      const fallback = sessions[0]?.id;
      if (fallback) {
        setActiveSessionId(fallback);
        router.replace(`/chat?session=${fallback}`);
      } else {
        router.replace("/chat");
      }
      return;
    }

    const fallback = sessions[0].id;
    missingSessionRef.current = null;
    setActiveSessionId(fallback);
    router.replace(`/chat?session=${fallback}`);
  }, [initialize, loadingSessions, resetConversationView, router, sessionParam, sessions]);

  useEffect(() => {
    resetConversationView();
    if (!activeSessionId) {
      setLoadingMessages(false);
      return;
    }
    void loadSessionPayload(activeSessionId);
  }, [activeSessionId, loadSessionPayload, resetConversationView]);

  const ensureSession = useCallback(async (titleHint?: string) => {
    if (activeSessionId) return activeSessionId;

    try {
      const normalizedTitle = titleHint?.trim().replace(/\s+/g, " ");
      const sessionTitle = normalizedTitle
        ? normalizedTitle.slice(0, 28)
        : t("new_chat_session");
      const created = await chatApi.createSession(sessionTitle);
      const next = created.data;
      if (!next) return null;
      setSessions((prev) => [next, ...prev]);
      setActiveSessionId(next.id);
      router.replace(`/chat?session=${next.id}`);
      window.dispatchEvent(new Event("chat-sessions-changed"));
      return next.id;
    } catch (error) {
      toast.error((error as Error).message || t("failed_create_session"));
      return null;
    }
  }, [activeSessionId, router, t]);

  async function sendMessage() {
    if (analyzing) return;

    const query = input.trim();
    if (!query) return;

    const sessionBeforeSend = activeSessionId ? sessions.find((session) => session.id === activeSessionId) : null;
    const shouldRenameActiveSession =
      !!activeSessionId &&
      messages.length === 0 &&
      (!sessionBeforeSend?.title ||
        sessionBeforeSend.title === t("new_chat_session") ||
        sessionBeforeSend.title.includes("新建") ||
        sessionBeforeSend.title.toLowerCase().includes("new"));

    const sessionId = await ensureSession(query);
    if (!sessionId) {
      toast.error(t("no_active_session"));
      return;
    }

    setAnalyzing(true);
    setMessages((prev) => [...prev, { id: Date.now(), role: "USER", content: query, sessionId }]);
    setInput("");
    setShowCode(false);

    try {
      if (shouldRenameActiveSession) {
        const normalizedTitle = query.replace(/\s+/g, " ").slice(0, 28);
        const updated = await chatApi.updateSession(sessionId, normalizedTitle);
        if (updated.data) {
          setSessions((prev) => prev.map((session) => (session.id === sessionId ? updated.data! : session)));
          window.dispatchEvent(new Event("chat-sessions-changed"));
        }
      }
      const result = await chatApi.analyze(sessionId, query, selectedDatasetId);
      setLastResult(result);
      if (!result.success) {
        toast.error(result.message || t("analysis_failed"));
      }
      await initialize();
      await loadSessionPayload(sessionId);
    } catch (error) {
      toast.error((error as Error).message || t("analysis_request_failed"));
    } finally {
      setAnalyzing(false);
    }
  }

  async function triggerUpload(file?: File) {
    const sessionId = await ensureSession(file?.name);
    if (!sessionId || !file) return;

    setUploading(true);
    try {
      const res = await chatApi.uploadDataset(sessionId, file, file.name);
      if (!res.success) {
        toast.error(res.message || t("upload_failed"));
        return;
      }
      toast.success(t("dataset_uploaded_chat"));
      await initialize();
      await loadSessionPayload(sessionId);
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
      if (!res.success || !res.data?.id) {
        toast.error(res.message || t("promotion_failed"));
        return;
      }
      toast.success(t("promoted_to_workplace", { name: res.data.name }));
      window.location.href = `/workspace?groupId=${res.data.id}`;
    } catch (error) {
      toast.error((error as Error).message || t("promotion_failed"));
    } finally {
      setPromoting(false);
    }
  }

  const previewArtifacts = recentArtifacts.slice(0, 4);
  const isEmptyState = !loadingMessages && !messages.length && !lastResult;
  const hasConversation = messages.length > 0 || !!lastResult;

  const composer = (
    <div className="mx-auto w-full max-w-[860px]">
      <div className="relay-panel">
        <div className="flex items-center justify-between border-b border-[color:var(--color-border-tertiary)] px-4 py-3">
          <span className="font-mono text-[11px] font-black uppercase tracking-[0.08em] text-[color:var(--color-text-tertiary)]">
            analysis prompt
          </span>
          <span className="flex items-center gap-2 font-mono text-[11px] uppercase text-[color:var(--color-text-tertiary)]">
            <span className="relay-status-dot" />
            context ready
          </span>
        </div>
        <div className="px-4 pb-4 pt-4">
        <Textarea
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="输入分析问题..."
          className="min-h-[104px] resize-none rounded-[10px] border-none !bg-[color:var(--color-background-secondary)] px-4 py-3 text-[17px] leading-8 shadow-none outline-none hover:!bg-[color:var(--color-background-secondary)] focus:!bg-[color:var(--color-background-secondary)] focus:border-none focus:outline-none focus-visible:!bg-[color:var(--color-background-secondary)] focus-visible:border-transparent focus-visible:shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
              event.preventDefault();
              void sendMessage();
            }
          }}
        />

        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-[color:var(--color-border-tertiary)] pt-3">
          <div className="flex flex-wrap items-center gap-2">
            <select
              className="chat-dataset-select h-9 rounded-[9px] border border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] px-3 font-mono text-[12px] text-[color:var(--color-text-secondary)] outline-none ring-0 transition-colors hover:bg-[color:var(--color-button-hover-background)]"
              value={selectedDatasetId ?? ""}
              onChange={(event) => {
                const next = Number(event.target.value);
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

            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="grid h-9 w-9 place-items-center rounded-[9px] border border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] text-[color:var(--color-text-secondary)] transition-colors hover:bg-[color:var(--color-button-hover-background)] hover:text-[color:var(--color-text-primary)] disabled:opacity-60"
              title="上传数据集"
            >
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
            </button>

            <button
              type="button"
              onClick={() => {
                if (!datasets.length) {
                  toast.error(t("upload_dataset_first"));
                  return;
                }
                setWorkspaceName(`工作区-${new Date().toISOString().slice(0, 10)}`);
                setWorkspaceDesc("");
                setPromoteOpen(true);
              }}
              className="grid h-9 w-9 place-items-center rounded-[9px] border border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] text-[color:var(--color-text-secondary)] transition-colors hover:bg-[color:var(--color-button-hover-background)] hover:text-[color:var(--color-text-primary)]"
              title="转为工作区"
            >
              <FolderUp className="h-4 w-4" />
            </button>
          </div>

          <button
            type="button"
            onClick={() => void sendMessage()}
            disabled={analyzing || !input.trim()}
            className="grid h-11 w-11 place-items-center rounded-[10px] bg-[color:var(--color-accent-highlight)] text-white transition-[filter,transform] hover:brightness-[0.96] active:scale-[0.98] disabled:opacity-50"
            title="发送"
          >
            {analyzing ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowUp className="h-4 w-4" />}
          </button>
        </div>
        </div>
      </div>
    </div>
  );

  if (isEmptyState) {
    return (
      <div className="relay-frame flex h-full min-h-[calc(100vh-3rem)] flex-col border-0">
        <div className="flex w-full flex-1 items-center justify-center px-6 py-10">
          {composer}
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
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <DialogTitle>{t("promote_dialog_title")}</DialogTitle>
              <DialogDescription>{t("promote_dialog_desc")}</DialogDescription>
            </DialogHeader>
            <div className="space-y-4">
              <Input value={workspaceName} onChange={(event) => setWorkspaceName(event.target.value)} placeholder={t("workspace_name")} />
              <Textarea
                value={workspaceDesc}
                onChange={(event) => setWorkspaceDesc(event.target.value)}
                placeholder={t("description_optional")}
                rows={6}
              />
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setPromoteOpen(false)}>
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

  return (
      <div className="relay-frame flex h-full min-h-[calc(100vh-3rem)] flex-col border-0">
      <div className="flex w-full flex-1 flex-col px-6 pb-6 pt-10">
        <div
          ref={scrollRef}
          className="flex-1 overflow-y-auto"
        >
            <div className="mx-auto max-w-[820px] space-y-8 pb-8">
            {loadingMessages ? (
              <>
                <Skeleton className="ml-auto h-16 w-[44%]" />
                <Skeleton className="h-16 w-[58%]" />
                <Skeleton className="h-28 w-[68%]" />
              </>
            ) : null}

            {!loadingMessages &&
              messages.map((message) => {
                const isUser = message.role.toUpperCase() === "USER";
                return (
                  <div key={message.id} className={cn("flex", isUser ? "justify-end" : "justify-start")}>
                    <div
                      className={cn(
                        "max-w-[78%] px-1 text-[15px] leading-8",
                        isUser
                          ? "relay-user-bubble px-5 py-3 font-medium shadow-none"
                          : "text-[color:var(--color-text-secondary)]"
                      )}
                    >
                      {message.content}
                    </div>
                  </div>
                );
              })}

            {!loadingMessages && lastResult && !messages.some((message) => message.role.toUpperCase() === "ASSISTANT") ? (
              <div className="flex justify-start">
                <div className="max-w-[760px] px-1">
                  {lastResult.summary ? (
                    <article className="whitespace-pre-wrap border-l-2 border-[color:var(--color-accent-highlight)] pl-5 text-[15px] leading-8 text-[color:var(--color-text-primary)]">
                      {lastResult.summary}
                    </article>
                  ) : null}

                  <ResultTable rows={lastResult.data ?? []} />

                  {lastResult.generatedCodeOrSql || lastResult.generatedSql ? (
                    <div className="mt-4">
                      <button
                        type="button"
                        onClick={() => setShowCode((prev) => !prev)}
                        className="text-sm text-[color:var(--color-text-secondary)] transition-colors hover:text-[color:var(--color-text-primary)]"
                      >
                        {showCode ? "隐藏代码" : "查看代码"}
                      </button>
                      {showCode ? (
                        <pre className="relay-code mt-3 overflow-x-auto whitespace-pre-wrap px-4 py-4 text-sm leading-7">
                          {lastResult.generatedCodeOrSql || lastResult.generatedSql}
                        </pre>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </div>
            ) : null}

            {!loadingMessages && !hasConversation && previewArtifacts.length ? (
              <div className="space-y-3 pt-2">
                <div className="text-xs uppercase tracking-[0.12em] text-[color:var(--color-text-tertiary)]">Recent</div>
                <div className="grid gap-3 sm:grid-cols-2">
                  {previewArtifacts.map((artifact) => (
                    <button
                      key={artifact.id}
                      type="button"
                      onClick={() => {
                        setInput(artifact.userQuery ?? "");
                        setLastResult({
                          success: true,
                          data: parsePreview(artifact.resultPreviewJson),
                          generatedCodeOrSql: artifact.generatedCodeOrSql,
                          summary: artifact.summary,
                          recommendedChart: artifact.chartType,
                          artifactId: artifact.id,
                        });
                      }}
                      className="relay-panel-soft px-4 py-4 text-left transition-colors hover:border-[color:var(--color-border-info)] hover:bg-[color:var(--color-background-primary)]"
                    >
                      <div className="line-clamp-2 text-sm font-medium text-[color:var(--color-text-primary)]">
                        {artifact.userQuery || t("artifact_query")}
                      </div>
                      <div className="mt-2 line-clamp-3 text-sm leading-6 text-[color:var(--color-text-secondary)]">
                        {artifact.summary || "无摘要"}
                      </div>
                      <div className="mt-3 text-xs text-[color:var(--color-text-tertiary)]">
                        {formatTime(artifact.createdAt, localeTag)}
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
        </div>

        <div className="mx-auto mt-4 w-full max-w-[760px]">{composer}</div>
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
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t("promote_dialog_title")}</DialogTitle>
            <DialogDescription>{t("promote_dialog_desc")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Input value={workspaceName} onChange={(event) => setWorkspaceName(event.target.value)} placeholder={t("workspace_name")} />
            <Textarea
              value={workspaceDesc}
              onChange={(event) => setWorkspaceDesc(event.target.value)}
              placeholder={t("description_optional")}
              rows={6}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPromoteOpen(false)}>
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
