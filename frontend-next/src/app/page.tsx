"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Database, FileText, GitBranch, TerminalSquare } from "lucide-react";
import { getStoredToken } from "@/lib/auth";

const rows = [
  ["01", "Upload", "CSV / Excel / PDF / DOCX"],
  ["02", "Assemble", "schema + chunks + artifacts"],
  ["03", "Execute", "SQL / Python / retrieval"],
  ["04", "Persist", "workspace memory"],
];

export default function HomePage() {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const token = getStoredToken();
    if (token) {
      router.replace("/chat");
      return;
    }
    setReady(true);
  }, [router]);

  if (!ready) {
    return <div className="grid min-h-screen place-items-center bg-background font-mono text-xs uppercase text-[color:var(--color-text-tertiary)]">Loading</div>;
  }

  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="relay-frame min-h-screen px-5 py-5">
        <div className="mx-auto flex min-h-[calc(100vh-40px)] max-w-[1240px] flex-col">
          <header className="flex items-center justify-between border-b border-[color:var(--color-border-primary)] pb-5">
            <div>
              <p className="relay-label">Analyst Workspace</p>
              <h1 className="mt-1 text-[22px] font-black tracking-[-0.02em] md:text-[28px]">Context Relay System</h1>
            </div>
            <button
              type="button"
              onClick={() => router.push("/login")}
              className="inline-flex items-center gap-2 border border-[color:var(--color-border-primary)] bg-[color:var(--color-background-primary)] px-4 py-2 text-sm font-black transition-colors hover:bg-[color:var(--color-button-hover-background)]"
            >
              Enter
              <ArrowRight className="h-4 w-4" />
            </button>
          </header>

          <div className="grid flex-1 gap-6 py-8 lg:grid-cols-[minmax(0,1fr)_440px] lg:items-center">
            <div>
              <p className="relay-label">Interactive data analysis system</p>
              <h2 className="mt-5 max-w-[760px] text-[44px] font-black leading-[0.98] tracking-[-0.045em] text-[color:var(--color-text-primary)] md:text-[72px]">
                把多文件分析变成一条可追踪的执行线路。
              </h2>
              <p className="mt-6 max-w-[620px] text-[16px] leading-8 text-[color:var(--color-text-secondary)]">
                面向结构化数据、非结构化文档和历史分析结果，系统通过工作区组织联合上下文，再由智能体生成 SQL、Python 与检索步骤，形成可复用的分析资产。
              </p>

              <div className="mt-8 grid max-w-[720px] border border-[color:var(--color-border-primary)] bg-[color:var(--color-background-primary)] md:grid-cols-4">
                {[
                  { icon: Database, label: "Tables" },
                  { icon: FileText, label: "Documents" },
                  { icon: GitBranch, label: "Context" },
                  { icon: TerminalSquare, label: "Execution" },
                ].map(({ icon: Icon, label }, index) => (
                  <div
                    key={label}
                    className="flex items-center gap-3 border-b border-[color:var(--color-border-primary)] px-4 py-4 md:border-b-0 md:border-r last:md:border-r-0"
                  >
                    <Icon className="h-5 w-5 text-[color:var(--color-accent-highlight)]" />
                    <span className="font-mono text-[12px] font-bold uppercase">{label}</span>
                    <span className="ml-auto font-mono text-[10px] text-[color:var(--color-text-tertiary)]">0{index + 1}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="relay-panel bg-[color:var(--color-background-primary)]">
              <div className="flex items-center justify-between border-b border-[color:var(--color-border-primary)] px-4 py-3">
                <span className="font-mono text-xs font-black uppercase">analysis route</span>
                <span className="flex items-center gap-2 font-mono text-[11px] uppercase text-[color:var(--color-text-tertiary)]">
                  <span className="relay-status-dot" />
                  ready
                </span>
              </div>
              <table className="relay-table">
                <thead>
                  <tr>
                    <th>Step</th>
                    <th>Unit</th>
                    <th>Payload</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row[0]}>
                      <td className="font-mono font-black text-[color:var(--color-text-primary)]">{row[0]}</td>
                      <td className="font-black text-[color:var(--color-text-primary)]">{row[1]}</td>
                      <td className="font-mono">{row[2]}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="border-t border-[color:var(--color-border-primary)] p-4">
                <pre className="relay-code overflow-hidden p-4 text-[12px] leading-6">
{`workspace.context = {
  tables: schema + relations,
  docs: chunks + citations,
  memory: artifacts,
  tools: ["SQL", "Python", "RAG"]
}`}
                </pre>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
