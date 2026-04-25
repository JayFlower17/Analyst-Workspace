"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Loader2, Sparkles } from "lucide-react";
import { authApi } from "@/lib/api/client";
import { getStoredToken, persistAuth } from "@/lib/auth";
import { useLanguage } from "@/components/providers/language-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

type Mode = "login" | "register";

type Props = {
  mode: Mode;
};

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function AuthPageShell({ mode }: Props) {
  const router = useRouter();
  const { t } = useLanguage();
  const isLogin = mode === "login";

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const title = useMemo(() => (isLogin ? t("login_title") : t("register_title")), [isLogin, t]);
  const description = useMemo(
    () => (isLogin ? t("login_page_description") : t("register_page_description")),
    [isLogin, t]
  );

  useEffect(() => {
    if (getStoredToken()) {
      router.replace("/chat");
    }
  }, [router]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    if (isLogin) {
      const identity = username.trim();
      if (!identity || !password) {
        setError(t("fill_username_password"));
        return;
      }

      setLoading(true);
      try {
        const res = await authApi.signin(identity, password);
        if (!res.token) {
          throw new Error(t("login_failed_no_token"));
        }
        persistAuth(res, identity);
        router.replace("/chat");
      } catch (err) {
        setError((err as Error).message || t("login_failed"));
      } finally {
        setLoading(false);
      }
      return;
    }

    const trimmedUsername = username.trim();
    const trimmedEmail = email.trim();

    if (!trimmedUsername || !trimmedEmail || !password || !confirmPassword) {
      setError(t("fill_register_info"));
      return;
    }
    if (trimmedUsername.length < 3) {
      setError(t("username_min_length"));
      return;
    }
    if (!EMAIL_RE.test(trimmedEmail)) {
      setError(t("invalid_email"));
      return;
    }
    if (password.length < 6) {
      setError(t("password_min_length"));
      return;
    }
    if (password !== confirmPassword) {
      setError(t("password_not_match"));
      return;
    }

    setLoading(true);
    try {
      await authApi.signup(trimmedUsername, trimmedEmail, password, confirmPassword);
      const signinRes = await authApi.signin(trimmedUsername, password);
      if (!signinRes.token) {
        throw new Error(t("login_failed_no_token"));
      }
      persistAuth(signinRes, trimmedUsername);
      router.replace("/chat");
    } catch (err) {
      setError((err as Error).message || t("register_failed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-background px-6 py-12">
      <div className="w-full max-w-md rounded-[20px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-primary)] p-8 shadow-none">
        <div className="mb-6 flex items-center gap-2 text-[color:var(--color-text-secondary)]">
          <Sparkles className="h-4 w-4" />
          <span className="text-sm font-medium">{t("app_name")}</span>
        </div>

        <div className="mb-6 space-y-2">
          <h1 className="text-2xl font-medium text-[color:var(--color-text-primary)]">{title}</h1>
          <p className="text-sm text-[color:var(--color-text-secondary)]">{description}</p>
        </div>

        {error ? (
          <div className="mb-4 rounded-[12px] border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-300">
            {error}
          </div>
        ) : null}

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <label className="text-sm font-medium text-[color:var(--color-text-primary)]" htmlFor="username">
              {t("username")}
            </label>
            <Input
              id="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder={isLogin ? t("username") : t("username")}
              autoComplete={isLogin ? "username" : "nickname"}
            />
          </div>

          {!isLogin ? (
            <div className="space-y-2">
              <label className="text-sm font-medium text-[color:var(--color-text-primary)]" htmlFor="email">
                {t("email")}
              </label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder={t("email")}
                autoComplete="email"
              />
            </div>
          ) : null}

          <div className="space-y-2">
            <label className="text-sm font-medium text-[color:var(--color-text-primary)]" htmlFor="password">
              {t("password")}
            </label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={t("password_hint")}
              autoComplete={isLogin ? "current-password" : "new-password"}
            />
          </div>

          {!isLogin ? (
            <div className="space-y-2">
              <label className="text-sm font-medium text-[color:var(--color-text-primary)]" htmlFor="confirm-password">
                {t("confirm_password")}
              </label>
              <Input
                id="confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                placeholder={t("confirm_password")}
                autoComplete="new-password"
              />
            </div>
          ) : null}

          <Button className="w-full" type="submit" disabled={loading}>
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            {isLogin ? t("login") : t("register")}
          </Button>
        </form>

        <div className="mt-6 text-sm text-[color:var(--color-text-secondary)]">
          {isLogin ? (
            <p>
              {t("no_account")}{" "}
              <Link className="text-[color:var(--color-text-primary)] underline underline-offset-4" href="/register">
                {t("go_register")}
              </Link>
            </p>
          ) : (
            <p>
              {t("has_account")}{" "}
              <Link className="text-[color:var(--color-text-primary)] underline underline-offset-4" href="/login">
                {t("go_login")}
              </Link>
            </p>
          )}
        </div>
      </div>
    </main>
  );
}
