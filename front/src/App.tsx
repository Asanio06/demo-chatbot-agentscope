/**
 * App — racine du front demo-chatbot-agentscope.
 *
 * Enveloppe toute l'UI dans le provider CopilotKit v2 :
 *   <CopilotKit runtimeUrl="/api/copilotkit" agents__unsafe_dev_only={demoAgents}>
 *
 * - `runtimeUrl='/api/copilotkit'` : point d'entrée AG-UI du backend
 *   (agentscope-java). C'est l'exigence d'intégration de la spec.
 * - `agents__unsafe_dev_only` : registre des agents démo (DEV ONLY) — les
 *   HttpAgent AG-UI définis dans `src/agents/demoAgents.ts`.
 *
 * La mise en page utilise Ant Design (Layout/Header/Content + Typography) et
 * le style custom passe par CSS modules (App.module.css).
 */
import { CopilotKit } from '@copilotkit/react-core/v2'
import { Layout, Tag, Typography } from 'antd'
import { demoAgents, RUNTIME_URL } from './agents/demoAgents'
import ChatPanel from './components/ChatPanel/ChatPanel'
import styles from './App.module.css'

const { Header, Content } = Layout
const { Title, Paragraph, Text } = Typography

export default function App() {
  return (
    <CopilotKit runtimeUrl={RUNTIME_URL} agents__unsafe_dev_only={demoAgents}>
      <Layout className={styles.page}>
        <Header className={styles.header}>
          <div className={styles.headerInner}>
            <div className={styles.brand}>
              <span className={styles.logo} aria-hidden="true">
                ✈️
              </span>
              <Title level={3} className={styles.brandTitle}>
                METAR&nbsp;/&nbsp;TAF&nbsp;Decoder
              </Title>
            </div>
            <Text className={styles.headerTagline}>
              Démo chatbot météo aviation — agentscope-java&nbsp;·&nbsp;CopilotKit v2
            </Text>
          </div>
        </Header>

        <Content className={styles.content}>
          <section className={styles.intro}>
            <Title level={1} className={styles.introTitle}>
              Décodez les bulletins météo aviation
            </Title>
            <Paragraph className={styles.introText}>
              Collez un bulletin <Tag className={styles.tag}>METAR</Tag> ou{' '}
              <Tag className={styles.tag}>TAF</Tag> brut et le chatbot vous renvoie un
              décodage structuré (aéroport, heure UTC, vent, visibilité, nuages,
              température, QNH, temps, tendance).
            </Paragraph>
          </section>

          <ChatPanel />
        </Content>
      </Layout>
    </CopilotKit>
  )
}
