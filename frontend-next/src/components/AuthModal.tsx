"use client";

import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Loader2, Sparkles, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api/client";
import type { AuthResponse } from "@/lib/types";

type AuthModalProps = {
  open: boolean;
  onClose: () => void;
};

type AuthMode = "login" | "register";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function persistAuth(res: AuthResponse, fallbackUsername: string) {
  localStorage.setItem("token", res.token);
  localStorage.setItem(
    "user",
    JSON.stringify({
      id: res.id,
      username: res.username ?? fallbackUsername,
      email: res.email,
      role: res.role,
    })
  );
}

export function AuthModal({ open, onClose }: AuthModalProps) {
  const router = useRouter();
  const [mode, setMode] = useState<AuthMode>("login");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");

  const [registerName, setRegisterName] = useState("");
  const [registerEmail, setRegisterEmail] = useState("");
  const [registerPassword, setRegisterPassword] = useState("");
  const [registerConfirmPassword, setRegisterConfirmPassword] = useState("");
  const [agreeTerms, setAgreeTerms] = useState(false);

  useEffect(() => {
    if (!open) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKeyDown);

    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open, onClose]);

  useEffect(() => {
    if (!open) {
      setError("");
      setLoading(false);
    }
  }, [open]);

  const submitLabel = useMemo(() => {
    return mode === "login" ? "登录" : "创建账号";
  }, [mode]);

  async function finishAuth(res: AuthResponse, fallbackUsername: string) {
    if (!res.token) {
      throw new Error("认证成功，但没有拿到有效 token。");
    }
    persistAuth(res, fallbackUsername);
    onClose();
    router.push("/chat");
  }

  async function handleLogin(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const identity = loginEmail.trim();

    if (!identity || !loginPassword) {
      setError("请填写邮箱和密码。");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const res = await authApi.signin(identity, loginPassword);
      await finishAuth(res, identity);
    } catch (err) {
      setError((err as Error).message || "登录失败，请稍后重试。");
    } finally {
      setLoading(false);
    }
  }

  async function handleRegister(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const username = registerName.trim();
    const email = registerEmail.trim();

    if (!username || !email || !registerPassword || !registerConfirmPassword) {
      setError("请完整填写注册信息。");
      return;
    }
    if (!EMAIL_RE.test(email)) {
      setError("请输入有效的邮箱地址。");
      return;
    }
    if (registerPassword.length < 6) {
      setError("密码至少需要 6 位。");
      return;
    }
    if (registerPassword !== registerConfirmPassword) {
      setError("两次输入的密码不一致。");
      return;
    }
    if (!agreeTerms) {
      setError("请先同意条款。");
      return;
    }

    setLoading(true);
    setError("");

    try {
      await authApi.signup(username, email, registerPassword, registerConfirmPassword);
      const signinRes = await authApi.signin(username, registerPassword);
      await finishAuth(signinRes, username);
    } catch (err) {
      setError((err as Error).message || "注册失败，请稍后重试。");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AnimatePresence>
      {open ? (
        <>
          <motion.button
            type="button"
            aria-label="Close auth modal"
            className="fixed inset-0 z-50 bg-[rgba(7,6,18,0.8)] backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />

          <motion.div
            role="dialog"
            aria-modal="true"
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: 16 }}
            transition={{ duration: 0.24, ease: "easeOut" }}
            className="fixed left-1/2 top-1/2 z-[60] w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-[1.25rem] border border-white/10 bg-[#0f0e1e] p-8 text-white"
            onClick={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              onClick={onClose}
              className="absolute right-4 top-4 text-white/40 transition-colors duration-150 hover:text-white/80"
              aria-label="Close"
            >
              <X className="h-5 w-5" />
            </button>

            <div className="mb-6 flex items-center gap-2 text-white/70">
              <Sparkles className="h-4 w-4" />
              <span className="text-sm font-medium">Access your AI workspace</span>
            </div>

            <div className="mb-6 flex items-center gap-6 border-b border-white/10">
              <button
                type="button"
                onClick={() => {
                  setMode("login");
                  setError("");
                }}
                className={`border-b pb-3 text-sm transition-colors duration-150 ${
                  mode === "login"
                    ? "border-white text-white"
                    : "border-transparent text-white/50 hover:text-white/80"
                }`}
              >
                登录
              </button>
              <button
                type="button"
                onClick={() => {
                  setMode("register");
                  setError("");
                }}
                className={`border-b pb-3 text-sm transition-colors duration-150 ${
                  mode === "register"
                    ? "border-white text-white"
                    : "border-transparent text-white/50 hover:text-white/80"
                }`}
              >
                注册
              </button>
            </div>

            {error ? (
              <div className="mb-4 rounded-[0.875rem] border border-red-400/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                {error}
              </div>
            ) : null}

            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={mode}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.18, ease: "easeOut" }}
              >
                {mode === "login" ? (
                  <form className="space-y-4" onSubmit={handleLogin}>
                    <AuthField
                      label="邮箱"
                      type="email"
                      placeholder="you@company.com"
                      value={loginEmail}
                      onChange={setLoginEmail}
                    />
                    <AuthField
                      label="密码"
                      type="password"
                      placeholder="Enter your password"
                      value={loginPassword}
                      onChange={setLoginPassword}
                    />

                    <div className="text-right">
                      <button type="button" className="text-sm text-white/50 transition-colors duration-150 hover:text-white/80">
                        忘记密码？
                      </button>
                    </div>

                    <SubmitButton loading={loading} label={submitLabel} />

                    <div className="flex items-center gap-3 py-1">
                      <div className="h-px flex-1 bg-white/10" />
                      <span className="text-xs text-white/40">或者</span>
                      <div className="h-px flex-1 bg-white/10" />
                    </div>

                    <button
                      type="button"
                      className="w-full rounded-full border border-white/20 px-4 py-2.5 text-sm text-white transition-colors duration-150 hover:bg-white/5"
                    >
                      Continue with Google
                    </button>
                  </form>
                ) : (
                  <form className="space-y-4" onSubmit={handleRegister}>
                    <AuthField
                      label="姓名"
                      type="text"
                      placeholder="Your name"
                      value={registerName}
                      onChange={setRegisterName}
                    />
                    <AuthField
                      label="邮箱"
                      type="email"
                      placeholder="you@company.com"
                      value={registerEmail}
                      onChange={setRegisterEmail}
                    />
                    <AuthField
                      label="密码"
                      type="password"
                      placeholder="Create a password"
                      value={registerPassword}
                      onChange={setRegisterPassword}
                    />
                    <AuthField
                      label="确认密码"
                      type="password"
                      placeholder="Repeat your password"
                      value={registerConfirmPassword}
                      onChange={setRegisterConfirmPassword}
                    />

                    <label className="flex items-start gap-3 text-sm text-white/60">
                      <input
                        type="checkbox"
                        checked={agreeTerms}
                        onChange={(event) => setAgreeTerms(event.target.checked)}
                        className="mt-0.5 h-4 w-4 rounded border border-white/20 bg-white/5 accent-white"
                      />
                      <span>我已阅读并同意使用条款与隐私政策。</span>
                    </label>

                    <SubmitButton loading={loading} label={submitLabel} />
                  </form>
                )}
              </motion.div>
            </AnimatePresence>
          </motion.div>
        </>
      ) : null}
    </AnimatePresence>
  );
}

function AuthField({
  label,
  type,
  placeholder,
  value,
  onChange,
}: {
  label: string;
  type: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm text-white/60">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="w-full rounded-[0.625rem] border border-white/10 bg-white/5 px-4 py-2.5 text-white outline-none transition-colors duration-150 placeholder:text-white/30 focus:border-white/30"
      />
    </label>
  );
}

function SubmitButton({ loading, label }: { loading: boolean; label: string }) {
  return (
    <button
      type="submit"
      disabled={loading}
      className="flex w-full items-center justify-center gap-2 rounded-full bg-white px-4 py-2.5 text-sm font-medium text-[#070612] transition-colors duration-150 hover:bg-white/90 disabled:cursor-not-allowed disabled:bg-white/70"
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
      {label}
    </button>
  );
}
