/**
 * Agents démo du front — chatbot METAR/TAF.
 *
 * On enregistre ici les "demo AI agents" demandés par la spec :
 * - HttpAgent AG-UI : le client HTTP qui parle le protocole AG-UI au runtime
 *   backend exposé sous `/api/copilotkit` (agentscope-java).
 * - `agents__unsafe_dev_only` : registre d'agents DEV ONLY (côté client) — c'est
 *   le flag CopilotKit v2 qui autorise l'enregistrement d'agents de démo sans
 *   passer par un runtime distant. Utile pour faire tourner/déboguer l'UI en
 *   l'absence (ou en marge) du back complet.
 *
 * Chaque agent porte ses instructions système (message système initial AG-UI) :
 * c'est le moyen v2 (client AG-UI) d'injecter la personnalité du chat, la prop
 * `instructions` de l'ancien `CopilotChat` v1 n'existant plus en v2.
 *
 * @note DEV ONLY : ne jamais utiliser en production. Le runtime réel fournit
 *       les agents serveur (agentscope AG-UI) qui prennent le dessus.
 */
import { HttpAgent } from '@ag-ui/client'
import type { Message } from '@ag-ui/client'

/** URL du runtime AG-UI backend (mappé par le back vers agentscope AG-UI). */
export const RUNTIME_URL = '/api/copilotkit'

/**
 * Instructions système (français), portées par l'agent en tant que message
 * système initial (injecté en tête de conversation par le runtime AG-UI).
 */
export const CHAT_INSTRUCTIONS = [
  'Tu es un assistant météo aéronautique spécialisé dans le décodage des bulletins',
  'METAR et TAF. Quand l’utilisateur colle un bulletin brut, restitue un décodage',
  'structuré et lisible : code OACI, heure (jour + heure UTC), vent (direction/vitesse',
  'et rafales), visibilité, nuages (FEW/SCT/BKN/OVC + altitude en centaines de pieds),',
  'température/point de rosée, pression QNH, temps présent et tendance (NOSIG, ...).',
  'Réponds en français, conserve les termes techniques METAR/TAF en anglais.',
  'Si le bulletin est malformé ou inconnu, réponds explicitement "bulletin non reconnu"',
  'sans inventer de données.',
].join(' ')

/** Message système initial transmis à chaque agent de démo. */
const systemInstructionMessage: Message = {
  id: 'system-instructions',
  role: 'system',
  content: CHAT_INSTRUCTIONS,
}

/**
 * Registre d'agents de démo, consommé par la prop `agents__unsafe_dev_only`
 * du composant `<CopilotKit>` (v2).
 *
 * Chaque agent est un `HttpAgent` (AG-UI) pointant vers le runtime backend.
 * Le préfixe `demo:` est volontaire pour bien distinguer les agents DEV.
 */
export const demoAgents: Record<string, HttpAgent> = {
  'demo:decode-metar': new HttpAgent({
    agentId: 'demo:decode-metar',
    description:
      'Décode un bulletin METAR (météo aviation) brut : aéroport, heure, vent, ' +
      'visibilité, nuages, température/rosée, QNH, temps présent et tendance.',
    url: RUNTIME_URL,
    initialMessages: [systemInstructionMessage],
  }),
  'demo:decode-taf': new HttpAgent({
    agentId: 'demo:decode-taf',
    description:
      'Décode un bulletin TAF (prévision météo aviation) brut : période de validité, ' +
      'vent, visibilité, nuages, temps et évolution (prob / tempo / becoming).',
    url: RUNTIME_URL,
    initialMessages: [systemInstructionMessage],
  }),
}

/** Liste ordonnée des ids d'agents de démo (pour le sélecteur AntD). */
export const demoAgentIds: readonly string[] = Object.keys(demoAgents)

/** Petit helper d'exemple pour un bulletin METAR de démonstration. */
export const SAMPLE_METAR = 'LFPG 181500Z 24012KT 9999 SCT040 17/06 Q1015 NOSIG'

export type DemoAgentKey = keyof typeof demoAgents
