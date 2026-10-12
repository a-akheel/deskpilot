import { defineConfig } from "vite";
export default defineConfig({
  server: {
    proxy: { "/api": process.env.DESKPILOT_API_URL || "http://127.0.0.1:8080" },
  },
});
