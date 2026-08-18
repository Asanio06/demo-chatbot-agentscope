/**
 * ChatPanel — panneau de chat démo branché sur le runtime CopilotKit v2
 * (backend AG-UI `agentscope-java` exposé sous `/api/copilotkit`).
 *
 * Il regroupe :
 * - un sélecteur d'agent démo (AntD `Select`) qui bascule l'agent actif ;
 * - `useAgent({ agentId })` : bind l'agent sélectionné (v2) pour `CopilotChat` ;
 * - `<CopilotChat>` : composant tout-en-un de `@copilotkit/react-ui`
 *   (messages, input, suggestions, streaming) — UI de chat pré-construite.
 *
 * Le tout est enveloppé dans une `Card` AntD stylée par CSS module.
 */
import {
  useState,
  type ComponentType,
  type ReactElement,
} from 'react'
import { Card, Select, Space, Typography } from 'antd'
import { useAgent } from '@copilotkit/react-core/v2'
import { CopilotChat } from '@copilotkit/react-ui'
import {
  demoAgentIds,
  SAMPLE_METAR,
  type DemoAgentKey,
} from '../../agents/demoAgents'
import styles from './ChatPanel.module.css'

const { Text } = Typography

/** Instructions système (français), injectées dans le message système du chat. */
const CHAT_INSTRUCTIONS = [
  'Tu es un assistant météo aéronautique spécialisé dans le décodage des bulletins',
  'METAR et TAF. Quand l’utilisateur colle un bulletin brut, restitue un décodage',
  'structuré et lisible : code OACI, heure (jour + heure UTC), vent (direction/vitesse',
  'et rafales), visibilité, nuages (FEW/SCT/BKN/OVC + altitude en centaines de pieds),',
  'température/point de rosée, pression QNH, temps présent et tendance (NOSIG, ...).',
  'Réponds en français, conserve les termes techniques METAR/TAF en anglais.',
  'Si le bulletin est malformé ou inconnu, réponds explicitement "bulletin non reconnu"',
  'sans inventer de données.',
].join(' ')

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
    <Space direction="vertical" size={2} className={styles.agentSelectorWrap}>
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

  // Bind l'agent démo actif (v2) : c'est lui que CopilotChat exécutera.
  useAgent({ agentId: activeAgent })
  const agentIdSafe = (demoAgentIds.includes(activeAgent)
    ? activeAgent
    : demoAgentIds[0]) as DemoAgentKey

  return (
    <Card className={styles.panel} bordered={false}>
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
        <CopilotChat
          instructions={CHAT_INSTRUCTIONS}
          labels={{
            placeholder: 'Ex. : LFPG 181500Z 24012KT 9999 SCT040 17/06 Q1015 NOSIG',
          }}
        />
      </div>
    </Card>
  )
}
