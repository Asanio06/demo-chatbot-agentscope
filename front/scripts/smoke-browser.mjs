/**
 * Smoke test navigateur réel (Playwright-core + Chrome système, headless).
 *
 * Charge l'app servie par `vite preview` (http://localhost:5173) dans un vrai
 * Chromium headless et vérifie que l'UI React 19 + CopilotKit v2 + AntD se
 * monte et rend les marqueurs d'intégration.
 *
 * Pré-requis : le preview tourne sur le port 5173 (npm run preview).
 * Usage : node scripts/smoke-browser.mjs
 */
import { existsSync } from 'node:fs'
import { chromium } from 'playwright-core'

const BASE = process.env.SMOKE_BASE_URL || 'http://localhost:5173/'

const chromeCandidates = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
]

const executablePath = chromeCandidates.find((p) => existsSync(p))

const browser = await chromium.launch(
  executablePath
    ? { executablePath, headless: true, args: ['--no-sandbox'] }
    : { channel: 'chrome', headless: true, args: ['--no-sandbox'] },
)

try {
  const page = await browser.newPage()
  const pageErrors = []
  page.on('pageerror', (err) => pageErrors.push(String(err)))

  await page.goto(BASE, { waitUntil: 'networkidle', timeout: 25000 })
  await page.waitForTimeout(1500)

  const checks = await page.evaluate(() => {
    const text = document.body ? document.body.innerText : ''
    const html = document.body ? document.body.innerHTML : ''
    const has = (s) => text.includes(s)
    return {
      url: location.href,
      title: document.title,
      hasBrand: has('METAR') && has('Decoder'),
      hasIntro: has('Décodez les bulletins météo aviation'),
      hasChatCard: has('Chat météo aviation'),
      hasExampleMetar: has('181500Z') && has('Q1015'),
      hasAgentSelector: has('Agent démo'),
      hasChatInput: !!document.querySelector('textarea'),
      bodyHasContent: html.length > 200,
    }
  })

  console.log('=== Browser smoke ===')
  console.log(`URL      : ${checks.url}`)
  console.log(`Title    : ${checks.title}`)
  for (const [k, v] of Object.entries(checks)) {
    if (typeof v === 'boolean') console.log(`${v ? 'PASS' : 'FAIL'}  ${k}`)
  }
  if (pageErrors.length) {
    console.log('--- page errors ---')
    pageErrors.slice(0, 10).forEach((e) => console.log('ERR ' + e.slice(0, 300)))
  }
  const contentPass =
    checks.hasBrand &&
    checks.hasIntro &&
    checks.hasChatCard &&
    checks.hasExampleMetar &&
    checks.hasAgentSelector &&
    checks.bodyHasContent
  const ok = contentPass && pageErrors.length === 0
  console.log(ok ? 'BROWSER_OK' : 'BROWSER_FAIL')
  process.exitCode = ok ? 0 : 1
} finally {
  await browser.close()
}
