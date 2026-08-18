# front — METAR / TAF Decoder (démo chatbot)

Front **React 19 + Vite + Ant Design 6 + CopilotKit v2** du projet
`demo-chatbot-agentscope` (mini chatbot aéronautique de décodage de bulletins
météo **METAR / TAF**).

Il se connecte au runtime AG-UI du backend `agentscope-java` (Spring Boot)
exposé sous `/api/copilotkit`.

## Stack

| Brique | Version | Note |
|---|---|---|
| React | 19.2.x | hooks + functional components |
| Vite | 8.x | build + dev server |
| Ant Design | 6.6.x | Layout, Card, Select, Typography, Tag |
| CSS Modules | — | `.module.css` par composant (conventions BEM-ish) |
| CopilotKit | 1.68.x (v2) | `@copilotkit/react-core/v2` + `@copilotkit/react-ui` |
| TypeScript | 6.x | `.tsx` / `.ts` (pas de JS) |
| Oxlint | — | lint |

## Intégration CopilotKit v2 (exigence spec)

- Import du provider : `import { CopilotKit } from '@copilotkit/react-core/v2'`
- Provider rendu avec :
  ```tsx
  <CopilotKit
    runtimeUrl="/api/copilotkit"
    agents__unsafe_dev_only={demoAgents}
  >
  ```
  - `runtimeUrl="/api/copilotkit"` : pointe le runtime AG-UI backend.
  - `agents__unsafe_dev_only` : registre des **agents démo DEV ONLY**
    (HttpAgent AG-UI), défini dans `src/agents/demoAgents.ts`.
- UI de chat : composant tout-en-un `CopilotChat` (`@copilotkit/react-ui`),
  bindé à l'agent actif via `useAgent({ agentId })`.
- Styles v2 importés dans `src/main.tsx` :
  `@copilotkit/react-core/v2/styles.css` et `@copilotkit/react-ui/v2/styles.css`.

> ⚠️ `agents__unsafe_dev_only` est **réservé au dev / démo** : les agents serveur
> (agentscope AG-UI) fournis par le backend prennent le dessus en production.

## Structure

```
front/
  index.html
  vite.config.ts            # proxy /api -> back (dev)
  package.json
  scripts/
    smoke-browser.mjs       # smoke test navigateur (Playwright-core + Chrome)
  src/
    main.tsx                # entrée : styles globaux + mount App
    App.tsx                 # <CopilotKit runtimeUrl agents__unsafe_dev_only> + layout AntD
    App.module.css
    index.css               # reset/base globaux
    agents/
      demoAgents.ts         # HttpAgent AG-UI (agents démo) + RUNTIME_URL
    components/
      ChatPanel/
        ChatPanel.tsx       # Card AntD + sélecteur agent + <CopilotChat>
        ChatPanel.module.css
```

## Démarrage (dev)

```bash
npm install
npm run dev          # serveur Vite (port 5173), proxy /api -> http://localhost:8080
```

Le proxy dev vers le back est configurable :
`VITE_BACKEND_PROXY_TARGET=http://localhost:8080 npm run dev`.

## Build & vérification

```bash
npm run build        # tsc -b && vite build (doit passer)
npm run lint         # oxlint
npm run preview      # sert le build
npm run smoke:browser  # rend le build dans un vrai Chrome headless + vérifie l'UI
```

## Agents démo

Dans `src/agents/demoAgents.ts`, deux agents démo (`agents__unsafe_dev_only`) :
- `demo:decode-metar` — décodage d'un bulletin METAR ;
- `demo:decode-taf` — décodage d'un bulletin TAF.

Ils sont des `HttpAgent` AG-UI pointant vers `/api/copilotkit`. Le sélecteur
AntD dans le ChatPanel permet de basculer l'agent actif.

## Langue

UI, prompts et instructions du chat : **français**. Termes techniques METAR/TAF
conservés en anglais (OACI, QNH, NOSIG, NDB/RVR…).
