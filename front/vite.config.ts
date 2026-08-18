import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Port du backend Spring Boot (agentscope-java). Surchargeable :
//   VITE_BACKEND_PROXY_TARGET=http://localhost:8080 npm run dev
const backendProxyTarget = process.env.VITE_BACKEND_PROXY_TARGET || 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy vers le back (AG-UI agentscope exposé sous /api/copilotkit).
    // Le front <CopilotKit runtimeUrl="/api/copilotkit"> vise ce chemin relatif,
    // donc en dev on renvoie /api vers le serveur Spring Boot.
    proxy: {
      '/api': {
        target: backendProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
