"use client";

import { motion } from "framer-motion";

type SplitTextProps = {
  text: string;
  className?: string;
  startDelay?: number;
};

export function SplitText({ text, className = "", startDelay = 0 }: SplitTextProps) {
  const words = text.split(" ");

  return (
    <span className={className}>
      {words.map((word, index) => (
        <motion.span
          key={`${word}-${index}`}
          style={{ display: "inline-block", marginRight: "0.25em" }}
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{
            duration: 0.6,
            delay: startDelay + index * 0.08,
            ease: [0.25, 0.1, 0.25, 1],
          }}
        >
          {word}
        </motion.span>
      ))}
    </span>
  );
}
