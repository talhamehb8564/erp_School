import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const apiOrigin = process.env.VITE_API_ORIGIN || "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    strictPort: true,
    allowedHosts: true,
    proxy: {
      "/api": { target: apiOrigin, changeOrigin: true, timeout: 20_000, proxyTimeout: 20_000 },
    },
  },
  preview: {
    host: "0.0.0.0",
    port: 5173,
    allowedHosts: true,
  },
});
