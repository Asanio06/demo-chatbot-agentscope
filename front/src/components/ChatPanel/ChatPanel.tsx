/**
 * ChatPanel — panneau de chat démo branché sur le runtime CopilotKit v2
 * (backend AG-UI `agentscope-java` exposé sous `/api/copilotkit`).
 *
 * Il regroupe :
 * - un sélecteur d'agent démo (AntD `Select`) qui bascule l'agent actif ;
 * - `useAgent({ agentId })` : bind l'agent sélectionné (v2) pour `CopilotChat` ;
 * - `<CopilotChat>` : composant tout-en-un de `@copilotkit/react-core/v2`
 *   (messages, input, suggestions, streaming) — UI de chat pré-construite,
 *   bindé à l'agent actif via la prop explicite `agentId`.
 *
 * Le tout est enveloppé dans une `Card` AntD stylée par CSS module.
 */
import {
  useState,
  type ComponentType,
  type ReactElement,
} from 'react'
import { Card, Select, Space, Typography } from 'antd'
import {
  useAgent,
  CopilotChat,
} from '@copilotkit/react-core/v2'
import {
  demoAgentIds,
  SAMPLE_METAR,
  type DemoAgentKey,
} from '../../agents/demoAgents'
import styles from './ChatPanel.module.css'

const { Text } = Typography

export interface ChatPanelProps {
  /** Sélecteur d'agent rendu en haut du panneau (pour flexibilité des tests). */
  agentSelector?: ComponentType<{ value: string; onChange: (v: string) => void }>
}

/**
 * Petit sélecteur d'agent par défaut (AntD Select), accessible et labelé.
 */
function DefaultAgentSelector({
  value,
  onChange,
}: {
  value: string
  onChange: (v: string) => void
}): ReactElement {
  const options = demoAgentIds.map((id) => ({
    value: id,
    label: id.replace('demo:', '').toUpperCase(),
  }))

  return (
    <Space orientation="vertical" size={2} className={styles.agentSelectorWrap}>
      <label htmlFor="agent-select" className={styles.agentSelectorLabel}>
        Agent démo
      </label>
      <Select
        id="agent-select"
        aria-label="Agent démo"
        value={value}
        onChange={onChange}
        options={options}
        className={styles.agentSelect}
        popupMatchSelectWidth={false}
      />
    </Space>
  )
}

export default function ChatPanel({
  agentSelector: AgentSelector = DefaultAgentSelector,
}: ChatPanelProps): ReactElement {
  const [activeAgent, setActiveAgent] = useState<string>(demoAgentIds[0] ?? '')

  // L'agent actif retombe toujours sur un agent de démo connu (jamais sur un
  // id inexistant) : si la sélection pointe vers un agent non enregistré, on
  // re-bascule sur le premier agent démo. Évite le fallback CopilotKit vers
  // 'default' (agent absent) qui faisait lever « Agent not found after runtime
  // sync » et vider l'UI du chat.
  const agentIdSafe = (demoAgentIds.includes(activeAgent)
    ? activeAgent
    : demoAgentIds[0]) as DemoAgentKey

  // Bind l'agent démo actif (v2). `isReady` est false pendant la synchro du
  // runtime AG-UI distant : on s'en sert pour afficher un état de repli propre
  // plutôt que de laisser un agent provisoire envoyer / l'UI se vider.
  const { isReady } = useAgent({ agentId: agentIdSafe })

  return (
    <Card className={styles.panel} variant="borderless">
      <div className={styles.panelHeader}>
        <div>
          <Text strong className={styles.title}>
            Chat météo aviation
          </Text>
          <Text type="secondary" className={styles.subtitle}>
            Collez un bulletin METAR/TAF brut pour obtenir son décodage structuré.
          </Text>
        </div>
        <AgentSelector value={agentIdSafe} onChange={setActiveAgent} />
      </div>

      <div className={styles.example} role="note">
        <Text className={styles.exampleLabel}>Exemple :</Text>
        <Text code className={styles.exampleCode} copyable>
          {SAMPLE_METAR}
        </Text>
      </div>

      <div className={styles.chat}>
        {isReady ? (
          <CopilotChat
            agentId={agentIdSafe}
            labels={{
              chatInputPlaceholder:
                'Ex. : LFPG 181500Z 24012KT 9999 SCT040 17/06 Q1015 NOSIG',
            }}
          />
        ) : (
          <Text type="secondary" className={styles.connectingNote}>
            Connexion au runtime de l’assistant…
          </Text>
        )}
      </div>
    </Card>
  )
}
