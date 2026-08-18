# demo-chatbot-agentscope

Mini chatbot démo **aéronautique** : décodage de bulletins météo **METAR / TAF**.

Deux briques dans le même dépôt, orchestrées par `docker compose` (voir les
tickets back/front pour le détail) :
- `back/`  — Spring Boot + agentscope-java v2, agent conversationnel qui décode
  les bulletins, persistance d'état PostgreSQL, exposé via le protocole **AG-UI**
  sous `/api/copilotkit`.
- `front/` — React 19 + CopilotKit v2, UI de chat connectée au runtime AG-UI du
  back (`<CopilotKit runtimeUrl="/api/copilotkit">`), Ant Design 6 + CSS Modules.

> ⚠️ Le front n'est livré que sur la branche `feat/t56364df7-frontend`
> (ticket t_56364df7). Le back est livré sur sa propre branche (t_34feb4ae).

## Démarrage (une fois back + front en place)

```bash
docker compose up
```

- postgres : persistance d'état agentscope
- back : Spring Boot, AG-UI sous `/api/copilotkit`
- front : Vite, sert l'UI de chat

L'utilisateur ouvre le front, colle un METAR/TAF brut (ex.
`LFPG 181500Z 24012KT 9999 SCT040 17/06 Q1015 NOSIG`) et reçoit son décodage
structuré.
