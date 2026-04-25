"use client";

import { useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";

export default function WorkplaceRedirectPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const query = searchParams.toString();
    router.replace(query ? `/workspace?${query}` : "/workspace");
  }, [router, searchParams]);

  return null;
}
