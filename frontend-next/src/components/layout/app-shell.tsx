"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { BarChart3, Database, Globe2, LogOut, MessageSquare, Moon, Plus, Settings2, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { chatApi } from "@/lib/api/client";
import { clearAuth, getStoredToken, getStoredUser } from "@/lib/auth";
import type { ChatSession } from "@/lib/types";
import { cn } from "@/lib/utils";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useLanguage } from "@/components/providers/language-provider";
import { useTheme } from "@/components/providers/theme-provider";

type ShellProps = {
  children: React.ReactNode;
};

type NavItem = {
  label: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
};

function parseUser() {
  const fallback = { name: "User", initials: "AI" };
  const parsed = getStoredUser();
  if (!parsed) return fallback;
  const name = parsed.username || parsed.email || fallback.name;
  const initials = name.slice(0, 2).toUpperCase() || fallback.initials;
  return { name, initials };
}

function formatSessionLabel(session: ChatSession, fallback: string) {
  return session.title?.trim() || fallback;
}

export function AppShell({ children }: ShellProps) {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const { locale, setLocale, t } = useLanguage();
  const { themeMode, setThemeMode } = useTheme();

  const [ready, setReady] = useState(false);
  const [userName, setUserName] = useState("User");
  const [userLabel, setUserLabel] = useState("AI");
  const [chatSessions, setChatSessions] = useState<ChatSession[]>([]);
  const [loadingChatSessions, setLoadingChatSessions] = useState(false);

  const activeChatSessionId = useMemo(() => {
    const raw = searchParams.get("session");
    const parsed = Number(raw);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [searchParams]);

  const navItems = useMemo<NavItem[]>(
    () => [
      { label: "Ask", href: "/chat", icon: MessageSquare },
      { label: "Workspace", href: "/workspace", icon: BarChart3 },
      { label: "Warehouse", href: "/datasets", icon: Database },
    ],
    []
  );

  const loadChatSessions = useCallback(async () => {
    if (!pathname.startsWith("/chat")) return;
    setLoadingChatSessions(true);
    try {
      const res = await chatApi.getSessions();
      setChatSessions(res.data ?? []);
    } catch (error) {
      toast.error((error as Error).message || t("failed_load_sessions"));
    } finally {
      setLoadingChatSessions(false);
    }
  }, [pathname, t]);

  useEffect(() => {
    const token = getStoredToken();
    if (!token) {
      router.replace("/login");
      return;
    }

    const user = parseUser();
    setUserName(user.name);
    setUserLabel(user.initials);
    setReady(true);
  }, [router]);

  useEffect(() => {
    if (!ready) return;
    if (!pathname.startsWith("/chat")) {
      setChatSessions([]);
      return;
    }
    void loadChatSessions();
  }, [loadChatSessions, pathname, ready, activeChatSessionId]);

  useEffect(() => {
    if (!ready) return;
    const reload = () => {
      if (pathname.startsWith("/chat")) void loadChatSessions();
    };
    window.addEventListener("chat-sessions-changed", reload);
    return () => window.removeEventListener("chat-sessions-changed", reload);
  }, [loadChatSessions, pathname, ready]);

  const handleCreateSession = useCallback(async () => {
    try {
      const created = await chatApi.createSession(t("new_chat_session"));
      const next = created.data;
      if (!next) return;
      setChatSessions((prev) => [next, ...prev.filter((session) => session.id !== next.id)]);
      window.dispatchEvent(new Event("chat-sessions-changed"));
      router.push(`/chat?session=${next.id}`);
    } catch (error) {
      toast.error((error as Error).message || t("failed_create_session"));
    }
  }, [router, t]);

  const handleDeleteSession = useCallback(
    async (sessionId: number) => {
      try {
        await chatApi.deleteSession(sessionId);
        setChatSessions((prev) => prev.filter((session) => session.id !== sessionId));
        window.dispatchEvent(new Event("chat-sessions-changed"));
        if (sessionId === activeChatSessionId) {
          router.replace("/chat");
        }
      } catch (error) {
        toast.error((error as Error).message || "删除会话失败");
      }
    },
    [activeChatSessionId, router]
  );

  if (!ready) {
    return (
      <div className="grid min-h-screen place-items-center px-4">
        <div className="h-8 w-32 animate-pulse rounded-[var(--border-radius-md)] bg-[color:var(--color-background-secondary)]" />
      </div>
    );
  }

  const activeModule = navItems.find((item) => pathname.startsWith(item.href)) ?? navItems[0];

  return (
    <div className="min-h-screen bg-background text-foreground">
      <div className="grid min-h-screen grid-cols-1 xl:grid-cols-[248px_minmax(0,1fr)]">
        <aside className="sidebar-surface flex flex-col border-r border-[color:var(--color-border-tertiary)] xl:min-h-screen">
          <div className="border-b border-[color:var(--color-border-tertiary)] px-4 py-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="relay-label">Analyst Workspace</p>
                <p className="mt-1 truncate text-[18px] font-black tracking-[-0.01em] text-[color:var(--color-text-primary)]">
                  Context Relay
                </p>
              </div>
              <div className="relay-status-dot mt-1 shrink-0" />
            </div>
            <div className="mt-4 grid grid-cols-3 overflow-hidden rounded-[10px] border border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] text-center font-mono text-[10px] uppercase text-[color:var(--color-text-tertiary)]">
              <span className="border-r border-[color:var(--color-border-tertiary)] py-2">API</span>
              <span className="border-r border-[color:var(--color-border-tertiary)] py-2">RAG</span>
              <span className="py-2">SQL</span>
            </div>
          </div>

          <div className="flex h-12 shrink-0 items-center justify-between border-b border-[color:var(--color-border-tertiary)] px-3">
            <p className="font-mono text-[11px] uppercase tracking-[0.12em] text-[color:var(--color-text-tertiary)]">Routes</p>
            <span className="font-mono text-[10px] uppercase text-[color:var(--color-text-tertiary)]">03 modules</span>
          </div>

          <nav className="flex-1 overflow-y-auto p-3">
            <div className="space-y-1">
              {navItems.map((item) => {
                const active = pathname.startsWith(item.href);
                const Icon = item.icon;
                return (
                  <div key={item.href} className="space-y-1">
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => router.push(item.href)}
                        className={cn(
                          "flex h-[44px] min-w-0 flex-1 items-center gap-3 rounded-[10px] border px-3 text-left text-[14px] transition-[background-color,color,border-color] duration-[120ms]",
                          active
                            ? "border-transparent bg-[color:var(--color-sidebar-active-background)] font-black text-[color:var(--color-sidebar-active-text)]"
                            : "border-transparent bg-transparent font-medium text-[color:var(--color-text-secondary)] hover:bg-[color:var(--color-sidebar-hover-background)]"
                        )}
                      >
                        <Icon className="h-4 w-4" />
                        <span className="flex-1">{item.label}</span>
                        <span className="font-mono text-[10px] text-current opacity-55">
                          {item.href === "/chat" ? "01" : item.href === "/workspace" ? "02" : "03"}
                        </span>
                      </button>
                      {item.href === "/chat" ? (
                        <button
                          type="button"
                          onClick={() => void handleCreateSession()}
                          aria-label={t("new_chat")}
                          title={t("new_chat")}
                          className="inline-flex h-[44px] w-9 shrink-0 items-center justify-center rounded-[10px] border border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] text-[color:var(--color-text-primary)] transition-[background-color,border-color,color] duration-150 hover:bg-[color:var(--color-button-hover-background)]"
                        >
                          <Plus className="h-4 w-4" />
                        </button>
                      ) : null}
                    </div>

                    {item.href === "/chat" && active ? (
                      <div className="space-y-1">
                        {loadingChatSessions ? (
                          <>
                            <div className="h-8 animate-pulse rounded-[7px] bg-[color:var(--color-background-primary)]" />
                            <div className="h-8 animate-pulse rounded-[7px] bg-[color:var(--color-background-primary)]" />
                          </>
                        ) : (
                          chatSessions.map((session) => (
                            <div
                              key={session.id}
                              className={cn(
                                "group/session flex h-8 w-full items-center rounded-[8px] text-[12px] text-[color:var(--color-text-secondary)] transition-[background-color,color,border-color] duration-[120ms]",
                                session.id === activeChatSessionId
                                  ? "bg-[color:var(--color-sidebar-active-background)] font-bold text-[color:var(--color-text-primary)]"
                                  : "bg-transparent font-normal hover:bg-[color:var(--color-button-hover-background)] hover:text-[color:var(--color-text-primary)]"
                              )}
                            >
                              <button
                                type="button"
                                onClick={() => router.push(`/chat?session=${session.id}`)}
                                className="min-w-0 flex-1 truncate py-1.5 pr-2 pl-3 text-left"
                              >
                                {formatSessionLabel(session, t("session_label", { id: session.id }))}
                              </button>
                              <button
                                type="button"
                                aria-label="删除会话"
                                title="删除会话"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  void handleDeleteSession(session.id);
                                }}
                                className="mr-1 grid size-6 shrink-0 place-items-center opacity-0 transition-opacity hover:text-[color:var(--color-text-danger)] group-hover/session:opacity-100"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            </div>
                          ))
                        )}
                      </div>
                    ) : null}
                  </div>
                );
              })}
            </div>
          </nav>

          <div className="border-t border-[color:var(--color-border-tertiary)] p-3">
            <div className="flex items-center gap-2 rounded-[12px] border border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] px-2 py-[7px] transition-[background-color] duration-[120ms] hover:bg-[color:var(--color-button-hover-background)]">
              <div className="grid size-8 shrink-0 place-items-center rounded-[8px] bg-[color:var(--color-text-primary)] text-[12px] font-black text-[color:var(--color-background-primary)]">
                {userLabel}
              </div>
              <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-[color:var(--color-text-primary)]">
                {userName}
              </span>
              <DropdownMenu>
                <DropdownMenuTrigger
                  className="inline-flex size-6 items-center justify-center text-[color:var(--color-text-secondary)] transition-[background-color,color] duration-[120ms] hover:bg-[color:var(--color-button-hover-background)] hover:text-[color:var(--color-text-primary)]"
                  aria-label={t("settings")}
                >
                  <Settings2 className="h-4 w-4" />
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  align="end"
                  side="top"
                  sideOffset={8}
                  className="min-w-[220px] rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] p-1 shadow-none ring-0"
                >
                  <div className="px-2 py-1 text-[12px] font-medium text-[color:var(--color-text-secondary)]">
                    {t("settings")}
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuSub>
                    <DropdownMenuSubTrigger>
                      <Moon className="h-4 w-4" />
                      主题
                    </DropdownMenuSubTrigger>
                    <DropdownMenuSubContent className="min-w-[160px] rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] p-1 shadow-none ring-0">
                      <DropdownMenuRadioGroup
                        value={themeMode}
                        onValueChange={(value) => setThemeMode(value as "light" | "dark" | "system")}
                      >
                        <DropdownMenuRadioItem value="light">浅色</DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="dark">深色</DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="system">跟随系统</DropdownMenuRadioItem>
                      </DropdownMenuRadioGroup>
                    </DropdownMenuSubContent>
                  </DropdownMenuSub>
                  <DropdownMenuSub>
                    <DropdownMenuSubTrigger>
                      <Globe2 className="h-4 w-4" />
                      {t("language")}
                    </DropdownMenuSubTrigger>
                    <DropdownMenuSubContent className="min-w-[160px] rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] p-1 shadow-none ring-0">
                      <DropdownMenuRadioGroup value={locale} onValueChange={(value) => setLocale(value as "zh" | "en")}>
                        <DropdownMenuRadioItem value="zh">{t("chinese")}</DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="en">{t("english")}</DropdownMenuRadioItem>
                      </DropdownMenuRadioGroup>
                    </DropdownMenuSubContent>
                  </DropdownMenuSub>
                  <DropdownMenuGroup>
                    <DropdownMenuItem
                      variant="destructive"
                      onClick={() => {
                        clearAuth();
                        router.replace("/login");
                      }}
                    >
                      <LogOut className="h-4 w-4" />
                      {t("logout")}
                    </DropdownMenuItem>
                  </DropdownMenuGroup>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
        </aside>

        <main className={cn("flex min-h-screen min-w-0 flex-col overflow-hidden", pathname.startsWith("/chat") && "chat-main")}>
          <header className="flex h-12 shrink-0 items-center justify-between border-b border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)] px-5">
            <div className="flex min-w-0 items-center gap-2">
              <span className="relay-status-dot" />
              <span className="truncate text-sm font-black text-[color:var(--color-text-primary)]">{activeModule.label}</span>
            </div>
            <div className="hidden items-center gap-5 font-mono text-[10px] uppercase text-[color:var(--color-text-tertiary)] md:flex">
              <span>backend : 8080</span>
              <span>executor : 8000</span>
              <span>local mode</span>
            </div>
          </header>
          <div className={cn("page-shell", pathname.startsWith("/chat") && "page-shell-chat")}>
            <div className={cn("fade-reveal", pathname.startsWith("/chat") && "h-full")}>{children}</div>
          </div>
        </main>
      </div>
    </div>
  );
}
