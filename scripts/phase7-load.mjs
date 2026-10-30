#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { performance } from 'node:perf_hooks';
import { runIsolated } from './phase7-e2e.mjs';
import { sourceDigest } from './phase7-quality.mjs';

const ROOT = resolve(import.meta.dirname, '..');
const PROFILE = 'infra/load/phase7-load-profile.json';
const requireGate = (condition, code) => { if (!condition) throw Error(`BLOCKED: ${code}`); };
export function validateProfile(p) {
  requireGate(p?.schema_version === 1 && p.scope === 'isolated-local-proof' &&
    p.concurrent_users === 50 && p.duration_seconds === 300 && p.think_ms === 1000 &&
    !Object.hasOwn(p, 'base_url') && !Object.hasOwn(p, 'target'), 'LOAD_PROFILE');
  requireGate(JSON.stringify(p.mix_percent) === JSON.stringify({ chat_read: 60, navigation: 20, predictions: 20 }) &&
    JSON.stringify(p.slo) === JSON.stringify({ p95_ms: 1000, error_rate_percent: 1, integrity_violations: 0 }), 'LOAD_THRESHOLDS');
}
export async function validateLoadAuthority() {
  const profile = JSON.parse(await readFile(resolve(ROOT, PROFILE), 'utf8')); validateProfile(profile);
  const decisions = JSON.parse(await readFile(resolve(ROOT, 'docs/phase7-decisions.json'), 'utf8'));
  for (const name of ['load_profile', 'local_technical_scope']) {
    const gate = decisions.gates?.[name];
    requireGate(gate?.status === 'approved' && gate.source === 'owner' && gate.fixture !== true &&
      typeof gate.human_reference === 'string' && gate.human_reference.length > 0, 'LOAD_AUTHORITY');
    const entries = Object.entries(gate.evidence_hashes ?? {}); requireGate(entries.length > 0, 'LOAD_AUTHORITY');
    for (const [path, digest] of entries) {
      requireGate(path === '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-30-PLAN.md', 'LOAD_AUTHORITY_PATH');
      requireGate(createHash('sha256').update(await readFile(resolve(ROOT, path))).digest('hex') === digest, 'LOAD_AUTHORITY_DIGEST');
    }
  }
  requireGate(decisions.gates.local_technical_scope.public_launch_authorized === false &&
    profile.authority_reference === decisions.gates.load_profile.human_reference, 'LOAD_AUTHORITY');
  for (const key of ['concurrent_users', 'duration_seconds', 'mix_percent', 'slo'])
    requireGate(JSON.stringify(profile[key]) === JSON.stringify(decisions.gates.load_profile.parameters?.[key]), 'LOAD_AUTHORITY_PARAMETERS');
  return profile;
}
export const workloadKind = step => step % 10 < 6 ? 'chat_read' : step % 10 < 8 ? 'navigation' : 'predictions';
export const marketIsOpen = (market, until) => market?.state === 'OPEN' && Date.parse(market.closeDeadline) > until;
export function summarize(samples, seconds) {
  requireGate(samples.length > 0 && seconds > 0 && samples.every(s => Number.isFinite(s.ms) && s.ms >= 0 && typeof s.ok === 'boolean'), 'LOAD_SAMPLES');
  const sorted = samples.map(s => s.ms).sort((a, b) => a - b);
  return { requests: samples.length, p95_ms: sorted[Math.ceil(sorted.length * .95) - 1],
    error_rate_percent: samples.filter(s => !s.ok).length * 100 / samples.length,
    requests_per_second: samples.length / seconds };
}
export function assertLoadReport(r, p) {
  validateProfile(p);
  requireGate(r?.schema_version === 1 && r.status === 'passed' && r.scope === 'isolated-local-proof' &&
    r.actual_users === 50 && r.concurrent_peak === 50 && r.duration_seconds >= 300, 'LOAD_ACTUAL_RUN');
  requireGate(Number.isFinite(r.metrics?.p95_ms) && r.metrics.p95_ms <= p.slo.p95_ms &&
    Number.isFinite(r.metrics?.error_rate_percent) && r.metrics.error_rate_percent <= p.slo.error_rate_percent &&
    r.metrics.requests > 0 && r.metrics.requests_per_second > 0, 'LOAD_SLO');
  const total = Object.values(r.counts ?? {}).reduce((a, b) => a + b, 0);
  requireGate(total === r.metrics.requests && Object.entries(p.mix_percent).every(([key, percent]) =>
    r.counts[key] > 0 && Math.abs(r.counts[key] / total * 100 - percent) <= 2), 'LOAD_MIX');
  requireGate(r.integrity_violations === 0 && r.integrity_checked_accounts === 50 &&
    r.accepted_predictions >= 50 && r.replayed_predictions > 0, 'LOAD_INTEGRITY');
}
async function workload({ run, users, password }, profile) {
  requireGate(users.length === 50 && new Set(users.map(u => u.username)).size === 50, 'LOAD_USERS');
  const { chromium } = await import('../frontend/node_modules/@playwright/test/index.mjs');
  const browser = await chromium.launch({ channel: 'chrome', headless: true,
    args: ['--host-resolver-rules=MAP localhost 127.0.0.1'] });
  const contexts = [], participants = [];
  const report = { schema_version: 1, scope: 'isolated-local-proof', status: 'failed',
    produced_at: new Date().toISOString(), run_id: run, source_digest: await sourceDigest(),
    profile_sha256: createHash('sha256').update(await readFile(resolve(ROOT, PROFILE))).digest('hex'),
    actual_users: 0, concurrent_peak: 0, duration_seconds: 0,
    counts: { chat_read: 0, navigation: 0, predictions: 0 },
    failures: {},
    accepted_predictions: 0, replayed_predictions: 0, integrity_violations: 0,
    integrity_checked_accounts: 0, public_launch_authorized: false,
    limitations: ['Local closed-loop generator: one request/second/user plus response time; not a saturation or cloud benchmark',
      'Bounded seeded markets; new accepted predictions and genuine idempotent replays counted separately'] };
  try {
    // Login is preparation, not part of the sustained50-user measurement.
    for (const user of users) {
      const context = await browser.newContext({ baseURL: 'http://localhost:4300' }); contexts.push(context);
      const page = await context.newPage();
      await page.goto('http://localhost:4300/'); await page.locator('a.arena-nav__login:visible').click();
      await page.locator('#username').fill(user.username); await page.locator('#password').fill(password);
      await page.getByRole('button', { name: 'Entrar', exact: true }).click();
      await page.locator('a.arena-nav__account:visible').waitFor({ timeout: 15_000 });
      const balance = await context.request.get('/api/v1/me/credits'); requireGate(balance.status() === 200, 'LOAD_LOGIN');
      participants.push({ context, baseline: await balance.json(), receipts: new Map() });
      await page.close();
      if (participants.length % 10 === 0) console.log(`[phase7-load] OIDC preparation: ${participants.length}/50`);
    }
    report.actual_users = participants.length;
    const request = participants[0].context.request;
    const agenda = await request.get('/api/v1/public/sports/events'); requireGate(agenda.status() === 200, 'LOAD_DATASET');
    const items = (await agenda.json()).items; requireGate(items?.length > 0, 'LOAD_DATASET');
    const markets = [];
    for (const event of items) {
      const detail = await request.get(`/api/v1/public/sports/events/${event.eventId}`);
      requireGate(detail.status() === 200, 'LOAD_DATASET');
      const { marketId } = await detail.json();
      if (!marketId) continue;
      const response = await request.get(`/api/v1/public/odds/markets/${marketId}`);
      requireGate(response.status() === 200, 'LOAD_DATASET'); const m = await response.json();
      if (!marketIsOpen(m, Date.now() + profile.duration_seconds * 1000 + 10_000)) continue;
      markets.push({ marketId, marketVersionId: m.marketVersionId,
        publicationId: m.current.publicationId, ruleVersion: m.explanation.algorithmVersion, selection: 'HOME', stake: 10 });
    }
    requireGate(markets.length > 0, 'LOAD_MARKETS');
    const samples = [], start = performance.now(), until = start + profile.duration_seconds * 1000;
    console.log('[phase7-load] Starting sustained50-user/300s measurement');
    let active = 0;
    await Promise.all(participants.map(async (participant, userIndex) => {
      active++; report.concurrent_peak = Math.max(report.concurrent_peak, active);
      let step = 0, predictionStep = 0;
      try {
        do {
          const kind = workloadKind(step++), began = performance.now(); let ok = false, status = 'exception';
          report.counts[kind]++;
          try {
            const client = participant.context.request;
            if (kind === 'chat_read') {
              const response = await client.get(step % 2 ? '/api/v1/chat/global' : `/api/v1/chat/events/${items[userIndex % items.length].eventId}`, { timeout: 5000 });
              status = response.status();
              const body = await response.json(); ok = response.status() === 200 && Array.isArray(body.messages) && body.messages.length > 0;
            } else if (kind === 'navigation') {
              const response = await client.get('/api/v1/public/sports/events', { timeout: 5000 });
              status = response.status();
              ok = response.status() === 200 && (await response.json()).items?.length > 0;
            } else {
              const market = markets[predictionStep++ % markets.length];
              const prior = participant.receipts.get(market.marketId), key = prior?.key ?? randomUUID();
              const csrf = (await participant.context.cookies()).find(c => c.name === 'XSRF-TOKEN');
              requireGate(csrf, 'LOAD_CSRF');
              const response = await client.post('/api/v1/me/predictions', { data: market, timeout: 5000,
                headers: { Origin: 'http://localhost:4300', 'X-XSRF-TOKEN': decodeURIComponent(csrf.value), 'Idempotency-Key': key } });
              status = response.status();
              const receipt = await response.json(); ok = response.status() === 201 && typeof receipt.predictionId === 'string';
              if (ok && prior) { report.replayed_predictions++; if (JSON.stringify(receipt) !== JSON.stringify(prior.receipt)) report.integrity_violations++; }
              else if (ok) { report.accepted_predictions++; participant.receipts.set(market.marketId, { key, receipt }); }
            }
          } catch { /* Failed requests remain in latency and error metrics. */ }
          if (!ok) { const key = `${kind}:${status}`; report.failures[key] = (report.failures[key] ?? 0) + 1; }
          samples.push({ ms: performance.now() - began, ok });
          await delay(profile.think_ms);
        } while (performance.now() < until);
      } finally { active--; }
    }));
    report.duration_seconds = (performance.now() - start) / 1000; report.metrics = summarize(samples, report.duration_seconds);
    for (const participant of participants) {
      const balance = await participant.context.request.get('/api/v1/me/credits');
      const current = await balance.json();
      if (balance.status() !== 200 || Number(current.balance) !== Number(participant.baseline.balance) - participant.receipts.size * 10) report.integrity_violations++;
      for (const [market, saved] of participant.receipts) {
        const response = await participant.context.request.get(`/api/v1/me/predictions/markets/${market}`);
        if (response.status() !== 200 || JSON.stringify(await response.json()) !== JSON.stringify(saved.receipt)) report.integrity_violations++;
      }
      report.integrity_checked_accounts++;
    }
    report.status = 'passed'; assertLoadReport(report, profile);
  } catch (error) { report.status = 'failed'; report.failure = error.message.startsWith('BLOCKED:') ? error.message : 'Load preparation/execution failed'; throw error; }
  finally {
    await writeFile(resolve(ROOT, 'docs/phase7-load-report.json'), JSON.stringify(report, null, 2) + '\n');
    await Promise.all(contexts.map(context => context.close())); await browser.close();
  }
  console.log(`[phase7-load] ${JSON.stringify({ users: report.actual_users, seconds: report.duration_seconds, ...report.metrics, integrity_violations: report.integrity_violations })}`);
}
export async function runLoad() {
  const profile = await validateLoadAuthority();
  await runIsolated(null, resources => workload(resources, profile));
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    requireGate(JSON.stringify(process.argv.slice(2)) === JSON.stringify(['--profile', PROFILE, '--isolated']), 'LOAD_ARGUMENTS');
    // Finish module evaluation before the isolated runner imports this authority guard.
    runLoad().catch(error => { console.error(`[phase7-load] ${error.message}`); process.exitCode = 1; });
  } catch (error) { console.error(`[phase7-load] ${error.message}`); process.exitCode = 1; }
}
