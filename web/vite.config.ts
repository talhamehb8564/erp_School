import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const apiOrigin = process.env.VITE_API_ORIGIN || "http://127.0.0.1:8080";

const apiProxy = {
  "/api": {
    target: apiOrigin,
    changeOrigin: true,
    timeout: 20_000,
    proxyTimeout: 20_000,
    configure(proxy) {
      proxy.on("error", (_err, _req, res) => {
        if (res && !res.headersSent && "writeHead" in res) {
          res.writeHead(503, { "Content-Type": "application/json" });
          res.end(JSON.stringify({
            success: false,
            errorCode: "API_UNAVAILABLE",
            message: "Cannot reach the ERP server. Start the Spring Boot API on port 8080.",
          }));
        }
      });
    },
  },
};

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    strictPort: true,
    allowedHosts: true,
    proxy: apiProxy,
  },
  preview: {
    host: "0.0.0.0",
    port: 5173,
    allowedHosts: true,
    proxy: apiProxy,
  },
});
