import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateProfile, assertLoadReport, summarize, workloadKind, marketIsOpen } from './phase7-load.mjs';

test('prediction workload excludes suspended, locked and expiring markets without changing business states', () => {
  const until = Date.parse('2026-10-30T12:05:00Z');
  for (const state of ['SUSPENDED', 'LOCKED', 'CLOSED', null])
    assert.equal(marketIsOpen({ state, closeDeadline: '2026-10-31T12:00:00Z' }, until), false);
  assert.equal(marketIsOpen({ state: 'OPEN', closeDeadline: '2026-10-31T12:00:00Z' }, until), true);
  for (const closeDeadline of ['2026-10-30T12:05:00Z', '2026-10-30T12:04:59Z', 'invalid'])
    assert.equal(marketIsOpen({ state: 'OPEN', closeDeadline }, until), false);
});

const profile = () => ({ schema_version: 1, scope: 'isolated-local-proof', concurrent_users: 50,
  duration_seconds: 300, mix_percent: { chat_read: 60, navigation: 20, predictions: 20 },
  slo: { p95_ms: 1000, error_rate_percent: 1, integrity_violations: 0 }, think_ms: 1000 });
test('only the exact ratified local profile is accepted; owner and external targets are refused', () => {
  assert.doesNotThrow(() => validateProfile(profile()));
  for (const change of [{ concurrent_users: 49 }, { duration_seconds: 299 },
    { scope: 'production' }, { base_url: 'http://localhost:4200' }, { base_url: 'https://example.org' },
    { slo: null }, { mix_percent: { chat_read: 100 } }, { think_ms: 0 }])
    assert.throws(() => validateProfile({ ...profile(), ...change }), /BLOCKED/);
});
test('deterministic ten-step mix is60/20/20 for every virtual user', () => {
  const counts = {};
  for (let i = 0; i < 10; i++) { const k = workloadKind(i); counts[k] = (counts[k] ?? 0) + 1; }
  assert.deepEqual(counts, { chat_read: 6, navigation: 2, predictions: 2 });
});
test('latency/error metrics use actual samples including failed requests', () => {
  assert.deepEqual(summarize([{ ms: 20, ok: true }, { ms: 1001, ok: false }], 2),
    { requests: 2, p95_ms: 1001, error_rate_percent: 50, requests_per_second: 1 });
  assert.throws(() => summarize([], 300), /BLOCKED/);
});
test('absent actual users, duration, workload, integrity or SLO proof cannot certify load', () => {
  const good = { schema_version: 1, status: 'passed', scope: 'isolated-local-proof',
    actual_users: 50, concurrent_peak: 50, duration_seconds: 300, metrics: { requests: 500,
      p95_ms: 100, error_rate_percent: 0, requests_per_second: 1.6 },
    counts: { chat_read: 300, navigation: 100, predictions: 100 }, integrity_violations: 0,
    integrity_checked_accounts: 50, accepted_predictions: 50, replayed_predictions: 50 };
  assert.doesNotThrow(() => assertLoadReport(good, profile()));
  for (const change of [{ actual_users: 49 }, { concurrent_peak: 49 }, { duration_seconds: 299 },
    { counts: {} }, { integrity_violations: 1 }, { integrity_checked_accounts: 0 },
    { accepted_predictions: 0 }, { metrics: { ...good.metrics, p95_ms: 1001 } },
    { metrics: { ...good.metrics, error_rate_percent: 1.01 } }, { status: 'fixture' }])
    assert.throws(() => assertLoadReport({ ...good, ...change }, profile()), /BLOCKED/);
});
