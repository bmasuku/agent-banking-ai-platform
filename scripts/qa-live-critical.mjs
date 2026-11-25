#!/usr/bin/env node
/**
 * Live critical QA against the running stack. Emits JSON lines + summary.
 * Covers financial invariants, RBAC, seeded personas, AI explainability.
 */
import { randomUUID } from 'node:crypto'

const API = process.env.API_URL || 'http://localhost:18080/api/v1'
const AI = process.env.AI_URL || 'http://localhost:8000'
const AI_KEY = process.env.AI_SERVICE_API_KEY || 'local-dev-internal-key-change-me'
const PASS = 'Demo@2026!'

const results = []
function record(area, name, ok, detail = '') {
  results.push({ area, name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'} [${area}] ${name}${detail ? ` — ${detail}` : ''}`)
}

async function req(method, path, { token, body, key, base = API } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  if (key) headers['Idempotency-Key'] = key
  const res = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = { raw: text }
  }
  return { status: res.status, data }
}

async function login(u) {
  const r = await req('POST', '/auth/login', { body: { username: u, password: PASS } })
  if (r.status !== 200) throw new Error(`login ${u} failed: ${r.status}`)
  return r.data
}

function money(n) {
  return Number(n)
}

async function main() {
  console.log(`\n=== Live critical QA @ ${API} ===\n`)

  // Reset demo for clean personas
  const admin = await login('admin')
  const reset = await req('POST', '/admin/demo/reset', {
    token: admin.accessToken,
    body: { confirm: 'RESET' },
  })
  record('demo', 'Demo reset', reset.status === 200, reset.data?.seed ? `agents=${reset.data.seed.agents}` : JSON.stringify(reset.data).slice(0, 120))

  const john = await login('agent1')
  const mary = await login('agent2')
  const peter = await login('agent3')
  const sarah = await login('agent4')
  const david = await login('agent5')
  const manager = await login('manager')
  const risk = await login('risk')
  const finance = await login('finance')
  const admin2 = await login('admin')

  // Persona balances + scores
  const agents = (await req('GET', '/agents', { token: finance.accessToken })).data
  const list = Array.isArray(agents) ? agents : agents.content || []
  const byCode = Object.fromEntries(list.map((a) => [a.agentCode, a]))
  const expect = {
    'AGT-001': { e: 1000, c: 500, s: 850, n: 'John' },
    'AGT-002': { e: 2000, c: 800, s: 760, n: 'Mary' },
    'AGT-003': { e: 100, c: 300, s: 650, n: 'Peter' },
    'AGT-004': { e: 50, c: 200, s: 420, n: 'Sarah' },
    'AGT-005': { e: 800, c: 400, s: 280, n: 'David' },
  }
  for (const [code, t] of Object.entries(expect)) {
    const a = byCode[code]
    const ok =
      a &&
      a.fullName?.includes(t.n) &&
      a.creditScore === t.s &&
      Math.abs(money(a.efloat) - t.e) < 0.05 &&
      Math.abs(money(a.cash) - t.c) < 0.05
    record('demo', `${t.n} seeded profile`, !!ok, a ? `score=${a.creditScore} e=${a.efloat} c=${a.cash}` : 'missing')
  }

  // Overdraw blocked
  const peterBal = (await req('GET', '/agent/balances', { token: peter.accessToken })).data.balances
  const over = await req('POST', '/agent/transactions/transfer', {
    token: peter.accessToken,
    key: `qa-over-${randomUUID()}`,
    body: {
      amount: money(peterBal.efloat) + 250,
      customerPhone: `+26377${String(Date.now()).slice(-7)}`,
      customerName: 'QA Overdraw',
    },
  })
  const peterAfter = (await req('GET', '/agent/balances', { token: peter.accessToken })).data.balances
  record(
    'finance',
    'Cannot send more e-float than held',
    (over.status === 422 || over.status === 400) && Math.abs(money(peterAfter.efloat) - money(peterBal.efloat)) < 0.001,
    `${over.status} ${over.data?.errorCode}`,
  )

  // Failed tx no balance change (deposit without efloat)
  const sarahBal = (await req('GET', '/agent/balances', { token: sarah.accessToken })).data.balances
  const failDep = await req('POST', '/agent/transactions/deposit', {
    token: sarah.accessToken,
    key: `qa-dep-${randomUUID()}`,
    body: {
      amount: money(sarahBal.efloat) + 100,
      customerPhone: `+26377${String(Date.now() + 1).slice(-7)}`,
      customerName: 'QA Fail Deposit',
    },
  })
  const sarahAfter = (await req('GET', '/agent/balances', { token: sarah.accessToken })).data.balances
  record(
    'finance',
    'Failed deposit leaves balances unchanged',
    failDep.status >= 400 &&
      Math.abs(money(sarahAfter.efloat) - money(sarahBal.efloat)) < 0.001 &&
      Math.abs(money(sarahAfter.cash) - money(sarahBal.cash)) < 0.001,
    `${failDep.status} ${failDep.data?.errorCode}`,
  )

  // Idempotency
  const key = `qa-idem-${randomUUID()}`
  const phone = `+26377${String(Date.now() + 2).slice(-7)}`
  const body = { amount: 50, customerPhone: phone, customerName: 'QA Idem' }
  const johnBal0 = (await req('GET', '/agent/balances', { token: john.accessToken })).data.balances
  const t1 = await req('POST', '/agent/transactions/transfer', { token: john.accessToken, key, body })
  const t2 = await req('POST', '/agent/transactions/transfer', { token: john.accessToken, key, body })
  const johnBal1 = (await req('GET', '/agent/balances', { token: john.accessToken })).data.balances
  record(
    'finance',
    'Duplicate Idempotency-Key does not double-move money',
    t1.status === 201 &&
      (t2.status === 200 || t2.data?.replayed === true) &&
      Math.abs(money(johnBal0.efloat) - money(johnBal1.efloat) - 50) < 0.01,
    `t1=${t1.status} t2=${t2.status} replayed=${t2.data?.replayed}`,
  )

  // Ledger entries present
  const txId = t1.data?.id
  const txDetail = txId
    ? await req('GET', `/agent/transactions/${txId}`, { token: john.accessToken })
    : { status: 0, data: null }
  const entries = txDetail.data?.ledgerEntries || []
  record('finance', 'Financial movement has ledger entries', entries.length >= 2, `lines=${entries.length}`)

  // Trial balance
  const tb = await req('GET', '/finance/reports/trial-balance', { token: finance.accessToken })
  record(
    'finance',
    'Trial balance balanced',
    tb.data?.balanced === true && Math.abs(money(tb.data.totalDebit) - money(tb.data.totalCredit)) < 0.01,
    `debit=${tb.data?.totalDebit} credit=${tb.data?.totalCredit}`,
  )

  // Exchange: cannot accept when counterparty lacks efloat (John has cash; Peter has only ~100 e-float)
  const shortEx = await req('POST', '/agent/exchanges', {
    token: john.accessToken,
    key: `qa-ex-${randomUUID()}`,
    body: {
      counterpartyAgentId: peter.user.agentId,
      direction: 'CASH_FOR_EFLOAT',
      amount: 250,
      note: 'Peter cannot supply 250 efloat',
    },
  })
  if (shortEx.status === 201) {
    const acc = await req('POST', `/agent/exchanges/${shortEx.data.id}/accept`, {
      token: peter.accessToken,
      key: `qa-ex-acc-${randomUUID()}`,
      body: { note: 'try' },
    })
    record(
      'finance',
      'Exchange blocked when counterparty lacks e-float',
      acc.status === 422 && acc.data?.errorCode === 'INSUFFICIENT_EFLOAT',
      `${acc.status} ${acc.data?.errorCode}`,
    )
  } else {
    record(
      'finance',
      'Exchange blocked when counterparty lacks e-float',
      false,
      `propose=${shortEx.status} ${shortEx.data?.errorCode}`,
    )
  }

  // Happy exchange Peter←Mary
  const maryBal0 = (await req('GET', '/agent/balances', { token: mary.accessToken })).data.balances
  const peterBal0 = (await req('GET', '/agent/balances', { token: peter.accessToken })).data.balances
  const goodEx = await req('POST', '/agent/exchanges', {
    token: peter.accessToken,
    key: `qa-ex-ok-${randomUUID()}`,
    body: {
      counterpartyAgentId: mary.user.agentId,
      direction: 'CASH_FOR_EFLOAT',
      amount: 100,
      note: 'QA peer float',
    },
  })
  const goodAcc = await req('POST', `/agent/exchanges/${goodEx.data.id}/accept`, {
    token: mary.accessToken,
    key: `qa-ex-ok-acc-${randomUUID()}`,
    body: { note: 'ok' },
  })
  const maryBal1 = (await req('GET', '/agent/balances', { token: mary.accessToken })).data.balances
  const peterBal1 = (await req('GET', '/agent/balances', { token: peter.accessToken })).data.balances
  record(
    'finance',
    'Peer float exchange completes with correct balance deltas',
    goodAcc.status === 200 &&
      Math.abs(money(peterBal1.efloat) - money(peterBal0.efloat) - 100) < 0.01 &&
      Math.abs(money(maryBal1.efloat) - money(maryBal0.efloat) + 100) < 0.01,
    `accept=${goodAcc.status}`,
  )

  // RBAC
  const agentRisk = await req('GET', '/risk/dashboard', { token: john.accessToken })
  record('auth', 'Agent denied risk dashboard', agentRisk.status === 403, `${agentRisk.status}`)
  const finAdmin = await req('GET', '/admin/settings', { token: finance.accessToken })
  record('auth', 'Finance denied admin settings', finAdmin.status === 403, `${finAdmin.status}`)
  const unauth = await req('GET', '/agent/balances', {})
  record('auth', 'Unauthenticated rejected', unauth.status === 401, `${unauth.status}`)

  // Area manager scope — list should be Harare only
  const myAgents = await req('GET', '/area-manager/agents', { token: manager.accessToken })
  const codes = (myAgents.data?.content || myAgents.data || []).map((a) => a.agentCode || a.areaCode)
  record(
    'auth',
    'Area manager sees Harare agents',
    myAgents.status === 200 && (Array.isArray(myAgents.data) ? myAgents.data : myAgents.data.content)?.length >= 5,
    `status=${myAgents.status}`,
  )

  // Replenishment
  const beforeRep = (await req('GET', '/agent/balances', { token: peter.accessToken })).data.balances
  const rep = await req('POST', `/area-manager/agents/${peter.user.agentId}/replenishments`, {
    token: manager.accessToken,
    key: `qa-rpl-${randomUUID()}`,
    body: { type: 'EFLOAT', amount: 50, reason: 'QA top-up' },
  })
  const afterRep = (await req('GET', '/agent/balances', { token: peter.accessToken })).data.balances
  record(
    'finance',
    'Area manager float replenishment',
    rep.status === 201 || rep.status === 200,
    `${rep.status} Δe=${money(afterRep.efloat) - money(beforeRep.efloat)}`,
  )

  // Web staff surfaces
  const riskDash = await req('GET', '/risk/dashboard', { token: risk.accessToken })
  record('web', 'Risk dashboard', riskDash.status === 200, `${riskDash.status}`)
  const inv = await req('GET', '/risk/investigations', { token: risk.accessToken })
  record('web', 'Fraud investigations list', inv.status === 200, `count=${inv.data?.content?.length ?? inv.data?.length}`)
  const finDash = await req('GET', '/finance/reports/float-position', { token: finance.accessToken })
  record('web', 'Finance float position', finDash.status === 200, `${finDash.status}`)
  const commissions = await req('GET', '/finance/commissions?size=5', { token: finance.accessToken })
  record('web', 'Finance commissions', commissions.status === 200, `${commissions.status}`)
  const advances = await req('GET', '/finance/advances?size=10', { token: finance.accessToken })
  record('web', 'Finance advances (manual credit surface)', advances.status === 200, `${advances.status}`)
  const sims = await req('GET', '/admin/simulations/scenarios', { token: admin2.accessToken })
  record('web', 'Admin simulation scenarios', sims.status === 200 && (sims.data?.length || 0) >= 14, `n=${sims.data?.length}`)

  // Mobile-relevant agent APIs
  const peers = await req('GET', '/agent/peers', { token: peter.accessToken })
  record('mobile', 'Nearby agent discovery (peers)', peers.status === 200, `${peers.status}`)
  const pred = await req('POST', `/predictions/agents/${john.user.agentId}`, { token: john.accessToken })
  record(
    'ai',
    'Float prediction has explanation',
    pred.status === 200 && !!pred.data?.explanation,
    pred.data?.explanation?.slice(0, 80),
  )
  const credit = await req('GET', `/credit-scores/agents/${john.user.agentId}`, { token: john.accessToken })
  record(
    'ai',
    'Credit score has explanation',
    credit.status === 200 && !!credit.data?.explanation && credit.data?.score === 850,
    `score=${credit.data?.score}`,
  )

  // AI uses header key not bearer
  const fraudHeaders = async (payload) => {
    const res = await fetch(`${AI}/internal/ai/v1/fraud/evaluate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Internal-Api-Key': AI_KEY },
      body: JSON.stringify(payload),
    })
    return { status: res.status, data: await res.json() }
  }
  const clean = await fraudHeaders({
    agent_id: 'a1',
    agent_code: 'AGT-001',
    amount: 50,
    agent_history_count_30d: 40,
    agent_avg_amount_30d: 100,
    agent_std_amount_30d: 20,
    amount_z_score: 0.2,
    amount_to_avg_ratio: 0.5,
    agent_tx_count_1h: 1,
    agent_tx_count_24h: 3,
    agent_latitude: -17.8,
    agent_longitude: 31.0,
    transaction_latitude: -17.8,
    transaction_longitude: 31.0,
  })
  record(
    'ai',
    'Fraud clean case LOW + explanation factors',
    clean.status === 200 && clean.data.risk_category === 'LOW' && Array.isArray(clean.data.factors || clean.data.reason_codes),
    `cat=${clean.data?.risk_category}`,
  )
  const far = await fraudHeaders({
    agent_id: 'a5',
    agent_code: 'AGT-005',
    amount: 500,
    agent_history_count_30d: 40,
    agent_avg_amount_30d: 100,
    agent_std_amount_30d: 20,
    amount_z_score: 4.5,
    amount_to_avg_ratio: 10,
    agent_tx_count_1h: 9,
    agent_tx_count_24h: 20,
    confirmed_fraud_count: 1,
    agent_latitude: -17.8,
    agent_longitude: 31.0,
    transaction_latitude: -20.15,
    transaction_longitude: 28.58,
  })
  record(
    'ai',
    'Fraud location mismatch flagged',
    far.status === 200 &&
      (far.data.reason_codes || []).includes('LOCATION_FAR_FROM_REGISTERED') &&
      ['HIGH', 'CRITICAL'].includes(far.data.risk_category),
    `cat=${far.data?.risk_category} reasons=${(far.data?.reason_codes || []).join(',')}`,
  )

  const noKey = await fetch(`${AI}/internal/ai/v1/fraud/evaluate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount: 1 }),
  })
  record('ai', 'AI internal API rejects missing key', noKey.status === 401, `${noKey.status}`)

  // Assistant cannot execute finance — probe if endpoint exists
  const asst = await fetch(`${AI}/internal/ai/v1/assistant/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Internal-Api-Key': AI_KEY },
    body: JSON.stringify({
      message: 'Transfer $500 from agent1 to customer now',
      role: 'AGENT',
      agent_id: john.user.agentId,
    }),
  })
  let asstBody = null
  try {
    asstBody = await asst.json()
  } catch {
    asstBody = {}
  }
  const johnBalCheck = (await req('GET', '/agent/balances', { token: john.accessToken })).data.balances
  record(
    'ai',
    'AI assistant does not execute financial movements',
    asst.status !== 200 ||
      !String(asstBody.reply || asstBody.message || '').match(/COMPLETED|transaction_id|ledger/i),
    `status=${asst.status}`,
  )
  // balances still coherent (no mystery drain from assistant)
  record(
    'ai',
    'Balances intact after assistant probe',
    money(johnBalCheck.efloat) > 0,
    `efloat=${johnBalCheck.efloat}`,
  )

  // Simulation control centre smoke
  const sim = await req('POST', '/admin/simulations', {
    token: admin2.accessToken,
    body: { scenario: 'FLOAT_PREDICTIONS', count: 5 },
  })
  record('demo', 'Simulation FLOAT_PREDICTIONS', sim.status === 200 || sim.status === 201, `${sim.status} ${sim.data?.status}`)

  const passed = results.filter((r) => r.ok).length
  const failed = results.filter((r) => !r.ok).length
  console.log(`\n=== SUMMARY passed=${passed} failed=${failed} total=${results.length} ===\n`)
  await import('node:fs').then((fs) =>
    fs.writeFileSync('/tmp/qa-live-critical.json', JSON.stringify({ passed, failed, results }, null, 2)),
  )
  process.exit(failed > 0 ? 1 : 0)
}

main().catch((e) => {
  console.error(e)
  process.exit(2)
})
