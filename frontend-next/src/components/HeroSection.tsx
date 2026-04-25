"use client";

import { useEffect, useRef } from "react";
import { motion } from "framer-motion";
import { ArrowRight, Sparkles } from "lucide-react";
import { BlurIn } from "@/components/BlurIn";
import { SplitText } from "@/components/SplitText";

const HERO_STREAM = "https://stream.mux.com/s8pMcOvMQXc4GD6AX4e1o01xFogFxipmuKltNfSYza0200.m3u8";

type HeroSectionProps = {
  onGetStarted: () => void;
  onLearnMore: () => void;
};

export function HeroSection({ onGetStarted, onLearnMore }: HeroSectionProps) {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    let mounted = true;
    let hlsInstance: { destroy: () => void } | null = null;

    async function setupVideo() {
      const video = videoRef.current;
      if (!video || !mounted) return;

      const { default: Hls } = await import("hls.js");
      if (!mounted || !videoRef.current) return;

      if (Hls.isSupported()) {
        const hls = new Hls();
        hls.loadSource(HERO_STREAM);
        hls.attachMedia(videoRef.current);
        hlsInstance = hls;
      } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
        video.src = HERO_STREAM;
      }
    }

    void setupVideo();

    return () => {
      mounted = false;
      hlsInstance?.destroy();
    };
  }, []);

  return (
    <section className="relative flex h-screen w-full items-center overflow-hidden bg-[#070612]">
      <motion.video
        ref={videoRef}
        autoPlay
        loop
        muted
        playsInline
        className="absolute inset-0 z-0 h-full w-full object-cover"
        style={{
          marginLeft: "200px",
          transformOrigin: "left center",
          objectPosition: "center center",
        }}
        initial={{ opacity: 0, scale: 1.24, x: 0 }}
        animate={{
          opacity: 1,
          scale: [1.24, 1.32, 1.26, 1.24],
          x: [0, 36, 18, 0],
          y: [0, -10, 6, 0],
        }}
        transition={{
          opacity: { duration: 1.1, ease: "easeOut" },
          scale: { duration: 18, repeat: Number.POSITIVE_INFINITY, ease: "easeInOut" },
          x: { duration: 18, repeat: Number.POSITIVE_INFINITY, ease: "easeInOut" },
          y: { duration: 18, repeat: Number.POSITIVE_INFINITY, ease: "easeInOut" },
        }}
      />

      <div className="absolute inset-0 z-10 bg-[radial-gradient(circle_at_left,rgba(7,6,18,0.06),rgba(7,6,18,0.52)_45%,rgba(7,6,18,0.9)_78%,#070612_100%)]" />
      <div className="absolute inset-0 z-10 bg-[linear-gradient(90deg,#070612_0%,rgba(7,6,18,0.6)_24%,rgba(7,6,18,0.16)_48%,rgba(7,6,18,0.3)_100%)]" />
      <div className="absolute bottom-0 left-0 right-0 z-10 h-40 bg-[linear-gradient(to_top,#070612,rgba(7,6,18,0.18),transparent)]" />

      <div className="relative z-20 mx-auto flex w-full max-w-7xl items-center px-6 lg:px-12">
        <div className="max-w-3xl">
          <div className="flex flex-col gap-6">
            <BlurIn duration={0.6} delay={0}>
              <div className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/5 px-3 py-1.5 text-sm font-medium text-white/80 backdrop-blur-sm">
                <Sparkles className="h-3 w-3 text-white/80" />
                <span>New AI Automation Ally</span>
              </div>
            </BlurIn>

            <div className="text-4xl leading-tight font-medium text-white md:text-5xl lg:text-6xl lg:leading-[1.2]">
              <div className="block">
                <SplitText text="Unlock the Power of AI" />
              </div>
              <div className="mt-2 flex flex-wrap items-baseline">
                <SplitText text="for Your" startDelay={0.32} />
                <SplitText text="Business." startDelay={0.52} className="font-serif italic" />
              </div>
            </div>

            <div className="flex flex-col gap-6">
              <BlurIn duration={0.6} delay={0.4}>
                <p className="max-w-xl text-lg leading-relaxed font-normal text-white/80">
                  Our cutting-edge AI platform automates, analyzes, and accelerates your workflows so you can focus on
                  what really matters.
                </p>
              </BlurIn>

              <BlurIn duration={0.6} delay={0.6}>
                <div className="flex flex-wrap gap-4">
                  <button
                    type="button"
                    onClick={onGetStarted}
                    className="flex items-center gap-2 rounded-full bg-white px-6 py-3 font-medium text-[#070612] transition-colors duration-150 hover:bg-white/90"
                  >
                    <span>Get Started</span>
                    <ArrowRight className="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    onClick={onLearnMore}
                    className="rounded-full bg-white/20 px-8 py-3 font-medium text-white backdrop-blur-sm transition-colors duration-150 hover:bg-white/30"
                  >
                    Learn More
                  </button>
                </div>
              </BlurIn>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
