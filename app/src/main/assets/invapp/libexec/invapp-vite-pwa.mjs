// InVxTermux container -- writes vite.config.js with VitePWA.
// Usage: bun "$PREFIX/libexec/invapp-vite-pwa.mjs" [react]
// Ported from the APK's copy (TermuxBunInstaller.java) so PWA scaffolds are
// identical in the container.
import { writeFileSync, existsSync, unlinkSync } from "fs";
import { basename } from "path";

const name = basename(process.cwd());
const isReact = process.argv[2] === "react";
const reactImport = isReact ? "import react from '@vitejs/plugin-react'\n" : "";
const reactPlug = isReact ? "react(), " : "";

const cfg =
  "import { defineConfig } from 'vite'\n" +
  "import { VitePWA } from 'vite-plugin-pwa'\n" +
  reactImport +
  "\nexport default defineConfig({\n" +
  "  server: { host: '0.0.0.0', port: 5173 },\n" +
  "  preview: { host: '0.0.0.0', port: 4173 },\n" +
  "  plugins: [" +
  reactPlug +
  "VitePWA({\n" +
  "    registerType: 'autoUpdate',\n" +
  "    includeAssets: ['favicon.svg', 'vite.svg'],\n" +
  "    manifest: {\n" +
  `      name: ${JSON.stringify(name)},\n` +
  `      short_name: ${JSON.stringify(name)},\n` +
  "      start_url: '/',\n" +
  "      display: 'standalone',\n" +
  "      background_color: '#ffffff',\n" +
  "      theme_color: '#242424',\n" +
  "      icons: [{ src: '/vite.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }]\n" +
  "    }\n" +
  "  })]\n" +
  "})\n";

for (const f of [
  "vite.config.js",
  "vite.config.ts",
  "vite.config.mjs",
  "vite.config.mts",
]) {
  if (existsSync(f)) unlinkSync(f);
}
writeFileSync("vite.config.js", cfg);
