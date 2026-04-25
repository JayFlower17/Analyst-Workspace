"use client";

import type { ComponentType } from "react";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Bot, ChartNoAxesCombined, Sparkles } from "lucide-react";
import { BlurIn } from "@/components/BlurIn";
import { HeroSection } from "@/components/HeroSection";
import { getStoredToken } from "@/lib/auth";

export default function HomePage() {
  const router = useRouter();
  const [ready, setReady] = useState(false);
  const featuresRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const token = getStoredToken();
    if (token) {
      router.replace("/chat");
      return;
    }
    setReady(true);
  }, [router]);

  if (!ready) {
    return <div className="grid min-h-screen place-items-center bg-[#070612] text-sm text-white/60">Loading</div>;
  }

  return (
    <>
      <main className="bg-[#070612] text-white">
        <HeroSection
          onGetStarted={() => router.push("/login")}
          onLearnMore={() => featuresRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })}
        />

        <section ref={featuresRef} className="bg-[#070612] px-6 py-20 lg:px-12">
          <div className="mx-auto max-w-7xl">
            <BlurIn className="mb-10">
              <div className="max-w-2xl">
                <p className="mb-3 text-sm font-medium text-white/50">Why teams start here</p>
                <h2 className="text-3xl leading-tight font-medium text-white md:text-4xl">
                  Bring analysis, automation, and decision-making into one calmer workflow.
                </h2>
              </div>
            </BlurIn>

            <div className="grid gap-4 md:grid-cols-3">
              <FeatureCard
                icon={Sparkles}
                title="AI workflows that stay useful"
                description="Move from messy requests to structured outcomes without babysitting every step."
              />
              <FeatureCard
                icon={ChartNoAxesCombined}
                title="Answers grounded in your data"
                description="Explore trends, compare segments, and turn raw tables into decisions with less friction."
              />
              <FeatureCard
                icon={Bot}
                title="One place for people and models"
                description="Keep collaboration, prompts, and outputs close enough that the work still feels human."
              />
            </div>

            <BlurIn delay={0.2} className="mt-10">
              <button
                type="button"
                onClick={() => router.push("/register")}
                className="inline-flex items-center gap-2 rounded-full border border-white/15 px-5 py-3 text-sm font-medium text-white/80 transition-colors duration-150 hover:bg-white/5 hover:text-white"
              >
                <span>Open your workspace</span>
                <ArrowRight className="h-4 w-4" />
              </button>
            </BlurIn>
          </div>
        </section>
      </main>
    </>
  );
}

function FeatureCard({
  icon: Icon,
  title,
  description,
}: {
  icon: ComponentType<{ className?: string }>;
  title: string;
  description: string;
}) {
  return (
    <BlurIn className="rounded-[1.25rem] border border-white/10 bg-white/5 p-6 backdrop-blur-sm">
      <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-white/80">
        <Icon className="h-5 w-5" />
      </div>
      <h3 className="text-xl font-medium text-white">{title}</h3>
      <p className="mt-3 text-base leading-relaxed text-white/65">{description}</p>
    </BlurIn>
  );
}
