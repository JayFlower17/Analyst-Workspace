"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { BarChart3, Database, Globe2, LogOut, MessageSquare, Monitor, Plus, Settings2, Moon, Sun } from "lucide-react";
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
  const { theme, themeMode, setThemeMode } = useTheme();

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
      { label: t("nav_chat"), href: "/chat", icon: MessageSquare },
      { label: t("nav_workplace"), href: "/workspace", icon: BarChart3 },
      { label: t("nav_datasets"), href: "/datasets", icon: Database },
    ],
    [t]
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

  const handleCreateSession = useCallback(async () => {
    try {
      const created = await chatApi.createSession(t("new_chat_session"));
      const next = created.data;
      if (!next) return;
      setChatSessions((prev) => [next, ...prev.filter((session) => session.id !== next.id)]);
      router.push(`/chat?session=${next.id}`);
    } catch (error) {
      toast.error((error as Error).message || t("failed_create_session"));
    }
  }, [router, t]);

  if (!ready) {
    return (
      <div className="grid min-h-screen place-items-center px-4">
        <div className="h-8 w-32 animate-pulse rounded-[var(--border-radius-md)] bg-[color:var(--color-background-secondary)]" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <div className="mx-auto grid min-h-screen max-w-[1600px] grid-cols-1 xl:grid-cols-[232px_minmax(0,1fr)]">
        <aside className="sidebar-surface flex flex-col border-r [border-width:0.5px] border-[color:var(--color-border-tertiary)] xl:min-h-screen">
          <div className="flex h-12 shrink-0 items-center justify-between px-[14px]">
            <p className="truncate text-[14px] font-medium tracking-[0.01em] text-[color:var(--color-text-primary)]">{t("app_name")}</p>
            <button
              type="button"
              onClick={() => void handleCreateSession()}
              aria-label={t("new_chat")}
              title={t("new_chat")}
              className="inline-flex size-7 items-center justify-center rounded-[7px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-transparent text-[color:var(--color-text-primary)] transition-[background-color,border-color,color] duration-150 hover:bg-[color:var(--color-button-hover-background)]"
            >
              <Plus className="h-4 w-4" />
            </button>
          </div>

          <nav className="flex-1 overflow-y-auto p-2">
            <div className="space-y-1">
              {navItems.map((item) => {
                const active = pathname.startsWith(item.href);
                const Icon = item.icon;
                return (
                  <div key={item.href} className="space-y-1">
                    <button
                      type="button"
                      onClick={() => router.push(item.href)}
                      className={cn(
                        "flex h-[40px] w-full items-center gap-2 rounded-[8px] px-[10px] text-left text-[14px] transition-[background-color,color,border-color] duration-[120ms] border [border-width:0.5px]",
                        active
                          ? "border-[color:rgba(242,201,76,0.3)] bg-[color:rgba(242,201,76,0.08)] font-medium text-[color:var(--color-text-primary)]"
                          : "border-transparent bg-transparent font-normal text-[color:var(--color-text-secondary)] hover:bg-[color:var(--color-sidebar-hover-background)]"
                      )}
                    >
                      <Icon className="h-4 w-4" />
                      <span>{item.label}</span>
                    </button>

                    {item.href === "/chat" && active ? (
                      <div className="space-y-1">
                        {loadingChatSessions ? (
                          <>
                            <div className="h-8 animate-pulse rounded-[7px] bg-[color:var(--color-background-primary)]" />
                            <div className="h-8 animate-pulse rounded-[7px] bg-[color:var(--color-background-primary)]" />
                          </>
                        ) : (
                          chatSessions.map((session) => (
                            <button
                              key={session.id}
                              type="button"
                              onClick={() => router.push(`/chat?session=${session.id}`)}
                              className={cn(
                                "flex h-8 w-full items-center rounded-[7px] pr-[10px] pl-[28px] text-left text-[13px] text-[color:var(--color-text-secondary)] transition-[background-color,color] duration-[120ms]",
                                session.id === activeChatSessionId
                                  ? "bg-[color:var(--color-sidebar-active-background)] font-medium text-[color:var(--color-text-primary)]"
                                  : "bg-transparent font-normal hover:bg-[color:var(--color-button-hover-background)] hover:text-[color:var(--color-text-primary)]"
                              )}
                            >
                              <span className="truncate">
                                {formatSessionLabel(session, t("session_label", { id: session.id }))}
                              </span>
                            </button>
                          ))
                        )}
                      </div>
                    ) : null}
                  </div>
                );
              })}
            </div>
          </nav>

          <div className="border-t [border-width:0.5px] border-[color:var(--color-border-tertiary)] p-2">
            <div className="flex items-center gap-2 rounded-[8px] px-2 py-[6px] transition-[background-color] duration-[120ms] hover:bg-[color:var(--color-button-hover-background)]">
              <div className="grid size-8 shrink-0 place-items-center rounded-full bg-[color:var(--color-background-info)] text-[13px] font-medium text-[color:var(--color-text-info)]">
                {userLabel}
              </div>
              <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-[color:var(--color-text-primary)]">
                {userName}
              </span>
              <DropdownMenu>
                <DropdownMenuTrigger
                  className="inline-flex size-5 items-center justify-center rounded-[6px] text-[color:var(--color-text-secondary)] transition-[background-color,color] duration-[120ms] hover:bg-[color:var(--color-button-hover-background)] hover:text-[color:var(--color-text-primary)]"
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
                  <DropdownMenuSub>
                    <DropdownMenuSubTrigger>
                      {theme === "dark" ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}
                      {t("appearance")}
                    </DropdownMenuSubTrigger>
                    <DropdownMenuSubContent className="min-w-[180px] rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] p-1 shadow-none ring-0">
                      <DropdownMenuRadioGroup value={themeMode} onValueChange={(value) => setThemeMode(value as "system" | "light" | "dark")}>
                        <DropdownMenuRadioItem value="system">
                          <Monitor className="h-4 w-4" />
                          {t("follow_system")}
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="light">
                          <Sun className="h-4 w-4" />
                          {t("light_mode")}
                        </DropdownMenuRadioItem>
                        <DropdownMenuRadioItem value="dark">
                          <Moon className="h-4 w-4" />
                          {t("dark_mode")}
                        </DropdownMenuRadioItem>
                      </DropdownMenuRadioGroup>
                    </DropdownMenuSubContent>
                  </DropdownMenuSub>
                  <DropdownMenuSeparator />
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

        <main className="min-w-0">
          <div className="page-shell">
            <div className="fade-reveal">{children}</div>
          </div>
        </main>
      </div>
    </div>
  );
}
