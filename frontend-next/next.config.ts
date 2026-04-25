import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    const backendBase = process.env.BACKEND_API_ORIGIN ?? "http://localhost:8080";
    return [
      {
        source: "/backend-api/:path*",
        destination: `${backendBase}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
