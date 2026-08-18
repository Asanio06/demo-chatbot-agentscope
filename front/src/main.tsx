/**
 * Point d'entrée de l'app front demo-chatbot-agentscope (React 19 + Vite).
 *
 * Chargement global :
 * - `@copilotkit/react-core/v2/styles.css` : styles de la lib v2.
 * - `@copilotkit/react-ui/v2/styles.css` : styles UI v2 (CopilotChat).
 * - reset Ant Design.
 * - `index.css` : styles globaux de base (body, fond).
 */
import { StrictMode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import '@copilotkit/react-core/v2/styles.css'
import '@copilotkit/react-ui/v2/styles.css'
import 'antd/dist/reset.css'
import './index.css'
import App from './App.tsx'

/**
 * Monte l'app dans le conteneur fourni. Exporté pour le smoke test (jsdom) ;
 * le point d'entrée par défaut le fait sur #root.
 */
export function mountApp(container: HTMLElement | null): Root | null {
  if (!container) return null
  const root = createRoot(container)
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
  return root
}

if (typeof document !== 'undefined') {
  mountApp(document.getElementById('root'))
}
