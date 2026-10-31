import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { assertCertification, assertRetentionPolicy, requirements, commands, readSuite } from './validate-phase7.mjs';

const now = Date.now();
function valid() {
  return {
    schema_version: 1, status: 'passed-local', scope: 'isolated-local-technical-only',
    head: 'a'.repeat(40), source_digest: 'b'.repeat(64), produced_at: new Date(now).toISOString(),
    public_launch_authorized: false, publication_status: 'blocked',
    commands: Object.entries(commands).map(([label, command], i) => ({ label, command,
      exit: label === 'publication' ? 1 : 0, started_at: new Date(now - 20000 + i * 1000).toISOString(),
      ended_at: new Date(now - 19500 + i * 1000).toISOString() })),
    requirements, decisions: Array.from({length:40}, (_,i)=>`D-${String(i+1).padStart(2,'0')}`),
    gates:Object.fromEntries(['protocol','ui','mvp_story','writing_contract','lifecycle','moderation_policy','mute_policy','legal','backup_operations','load_profile','rules_preference','local_technical_scope'].map(name=>[name,{status:'approved',source:'owner',human_reference:'fixture-only-negative-tests'}])),
    security:{status:'passed',findings:[],scans:[{ecosystem:'Maven',status:'passed',packages:1},{ecosystem:'npm',status:'passed',packages:1}]},
    maven: { surefire: {tests:361,classes:37,failures:0,errors:0,skipped:0},
      failsafe: {tests:780,classes:153,failures:0,errors:0,skipped:0} },
    node: {tests:970,failures:0,skipped:0}, angular: {tests:533,files:85},
    browser: {desktop:13,mobile320:13,owned_cleanup:2},
    restore: {status:'passed',rpo:10,rto:10,substrates:{betufu:{restored:true,chat_facts:1},
      keycloak:{restored:true,users:3},'private-objects':{restored:true,nonempty:true}},
      suppression:{register_current:true,applied_before_serving:true,served_after_reconciliation:0}},
    retention: {common_days:90,evidence_days:180,other_expurgo:'blocked',independent_legal_review:'pending'},
    prohibitions: Array.from({length:35},(_,i)=>({plan:`07-${String(i+1).padStart(2,'0')}`,status:'reviewed-local',evidence:['maven','browser','publication']})),
  };
}
const context = {head:'a'.repeat(40),source_digest:'b'.repeat(64),now};
test('bounded local certification accepts complete evidence while keeping publication blocked', () => {
  assertCertification(valid(), context);
});
for (const [name, change] of [
  ['stale HEAD', r=>r.head='c'.repeat(40)],
  ['changed source', r=>r.source_digest='c'.repeat(64)],
  ['stale report', r=>r.produced_at=new Date(now-86400001).toISOString()],
  ['skipped Maven proof', r=>r.maven.failsafe.skipped=1],
  ['empty Maven proof', r=>r.maven.failsafe.tests=0],
  ['missing requirement', r=>delete r.requirements['CHAT-01']],
  ['missing decision', r=>r.decisions.pop()],
  ['pending gate', r=>r.gates.legal.status='pending'],
  ['missing vulnerability scan', r=>delete r.security],
  ['confirmed vulnerability', r=>r.security.findings.push({id:'fixture-only'})],
  ['failed command', r=>r.commands[1].exit=1],
  ['overlapping commands', r=>r.commands[1].started_at=r.commands[0].started_at],
  ['invented command', r=>r.commands[0].command='echo passed'],
  ['missing command', r=>r.commands.pop()],
  ['missing mobile journey', r=>r.browser.mobile320=0],
  ['unsafe publication', r=>r.public_launch_authorized=true],
  ['incomplete restore', r=>r.restore.substrates.keycloak.restored=false],
  ['restored suppressed content', r=>r.restore.suppression.served_after_reconciliation=1],
  ['RPO overflow', r=>r.restore.rpo=3601],
  ['RTO overflow', r=>r.restore.rto=14401],
  ['unresolved prohibition', r=>r.prohibitions[0].status='unresolved'],
  ['invented retention', r=>r.retention.other_expurgo='approved'],
]) test(`certification refuses ${name}`, () => {
  const r=structuredClone(valid());change(r);assert.throws(()=>assertCertification(r,context),/BLOCKED/);
});
test('JUnit suite parser rejects missing attributes and contradictory empty coverage', () => {
  assert.deepEqual(readSuite('<testsuite tests="2" failures="0" errors="0" skipped="0">'),
    {tests:2,failures:0,errors:0,skipped:0});
  assert.throws(()=>readSuite('<testsuite tests="2">'),/BLOCKED/);
});

test('actual proposed backup policy does not authorize expurgo', () => {
  const policy=JSON.parse(readFileSync(new URL('../docs/phase7-privacy-policy.json',import.meta.url),'utf8'));
  assertRetentionPolicy(policy);
  for (const change of [r=>r.status='approved',r=>r.authority.status='approved',r=>r.disposition='PURGED_FROM_PROJECTION']) {
    const changed=structuredClone(policy);
    change(changed.classes.backups.retention);
    assert.throws(()=>assertRetentionPolicy(changed),/UNAPPROVED_EXPURGO/);
  }
});
