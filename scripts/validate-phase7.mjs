#!/usr/bin/env node
import fs from 'node:fs';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { sourceDigest, manifestDigests, assertSecurityReport } from './phase7-quality.mjs';
import { assertLoadReport } from './phase7-load.mjs';
import { validatePhase7Approvals } from './validate-phase7-approvals.mjs';

const ROOT = resolve(import.meta.dirname, '..');
const PHASE = '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto';
const OUTPUT = 'docs/phase7-certification.json';
const EVIDENCE = '.git/gsd/phase7/07-32-evidence.json';
const gate = (condition, code) => { if (!condition) throw Error(`BLOCKED: ${code}`); };
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const git = (...args) => execFileSync('git', args, {cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']}).trim();
const read = path => {
  gate(typeof path === 'string' && !/[:\\\x00-\x1f]/u.test(path) && path.split('/').every(p=>p && p!=='.' && p!=='..'), 'ARTIFACT_PATH');
  let current=ROOT;
  for (const part of path.split('/')) { current=resolve(current,part);gate(!fs.lstatSync(current).isSymbolicLink(),'ARTIFACT_SYMLINK'); }
  return fs.readFileSync(current);
};
const json = path => JSON.parse(read(path));
const fresh = (at, now) => Number.isFinite(Date.parse(at)) && now-Date.parse(at)>=-300000 && now-Date.parse(at)<=86400000;

export const commands = Object.freeze({
  focal:'node scripts/maven.mjs -f backend/pom.xml -Dtest=Phase7ConcurrencyIT,Phase7PrivacyIT,Phase7CertificationIT,ModulithArchitectureTests test',
  clean:'node scripts/maven.mjs -f backend/pom.xml clean',
  maven:'node scripts/maven.mjs -f backend/pom.xml verify',
  node:'node --test scripts/*.test.mjs',
  angular:'npm --prefix frontend test -- --watch=false',
  generator:'npm --prefix frontend run generate:api:check',
  lint:'npm --prefix frontend run lint',
  build:'npm --prefix frontend run build:production',
  spotless:'node scripts/maven.mjs -f backend/pom.xml spotless:check',
  browser:'node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-integrated.spec.ts',
  load:'node scripts/phase7-load.mjs --profile infra/load/phase7-load-profile.json --isolated',
  restore:'node scripts/phase7-restore.mjs --policy infra/backup/phase7-backup-policy.json --isolated',
  publication:'node scripts/phase7-quality.mjs --publication',
  diff:'git diff --check',
});
export const requirements = Object.freeze({
  'CHAT-01':['maven','browser'], 'CHAT-02':['maven','browser'], 'CHAT-03':['maven','browser'],
  'CHAT-04':['maven','browser'], 'CHAT-05':['maven','browser'], 'CHAT-06':['maven','browser'],
  'RT-01':['maven','browser'], 'INTE-05':['maven','browser'],
  'PRIV-03':['node','publication'], 'PRIV-04':['publication'], 'PRIV-05':['maven','browser','publication'],
  'OPS-04':['restore'], 'TEST-03':['maven','node','angular','generator','browser'],
  'TEST-05':['maven','node','generator','spotless'], 'UX-01':['browser'], 'UX-02':['angular','browser'],
});
const decisionIds = Array.from({length:40},(_,i)=>`D-${String(i+1).padStart(2,'0')}`);
const requiredGates = ['protocol','ui','mvp_story','writing_contract','lifecycle','moderation_policy','mute_policy','legal','backup_operations','load_profile','rules_preference','local_technical_scope'];
const plans = Array.from({length:35},(_,i)=>`07-${String(i+1).padStart(2,'0')}`);
export function assertRetentionPolicy(policy) {
  for (const [name,c] of Object.entries(policy.classes)) {
    if (!['common_messages','moderation_evidence'].includes(name))
      gate(['pending','proposed'].includes(c.retention.status) && c.retention.authority.status==='pending' && c.retention.disposition===null,'UNAPPROVED_EXPURGO');
  }
}
export function readSuite(source) {
  const attributes=source.match(/<testsuite\s+([^>]+)/u)?.[1];gate(attributes,'XML_SUITE');
  return Object.fromEntries(['tests','failures','errors','skipped'].map(key=>{
    const value=attributes.match(new RegExp(`\\b${key}="(\\d+)"`))?.[1];gate(value!==undefined,'XML_COUNT');return [key,Number(value)];
  }));
}
export function assertCertification(r, context) {
  gate(r?.schema_version===1 && r.status==='passed-local' && r.scope==='isolated-local-technical-only','CERTIFICATION_SCOPE');
  gate(r.public_launch_authorized===false && r.publication_status==='blocked','PUBLICATION_NOT_AUTHORIZED');
  gate(r.head===context.head && /^[a-f0-9]{40}$/u.test(r.head),'STALE_HEAD');
  gate(r.source_digest===context.source_digest && /^[a-f0-9]{64}$/u.test(r.source_digest),'SOURCE_DRIFT');
  gate(fresh(r.produced_at,context.now),'STALE_REPORT');
  gate(Array.isArray(r.commands) && r.commands.length===Object.keys(commands).length,'COMMAND_COVERAGE');
  let ended=-Infinity;
  for (const [index,[label,command]] of Object.entries(commands).entries()) {
    const c=r.commands[index],start=Date.parse(c?.started_at),end=Date.parse(c?.ended_at);
    gate(c?.label===label && c.command===command && c.exit===(label==='publication'?1:0),'COMMAND_RESULT');
    gate(Number.isFinite(start) && start>=ended && end>=start && end<=context.now+300000 && fresh(c.started_at,context.now),'COMMAND_ORDER');ended=end;
  }
  gate(JSON.stringify(r.requirements)===JSON.stringify(requirements),'REQUIREMENT_COVERAGE');
  gate(JSON.stringify(r.decisions)===JSON.stringify(decisionIds),'DECISION_COVERAGE');
  gate(requiredGates.every(name=>r.gates?.[name]?.status==='approved' && r.gates[name].source==='owner' && r.gates[name].human_reference),'MISSING_GATE');
  gate(r.security?.status==='passed' && Array.isArray(r.security.findings) && r.security.findings.length===0 && ['Maven','npm'].every(name=>r.security.scans?.some(s=>s.ecosystem===name && s.status==='passed' && s.packages>0)),'SECURITY_PROOF');
  for (const name of ['surefire','failsafe']) {
    const s=r.maven?.[name];gate(s?.tests>0 && s.classes>0 && s.failures===0 && s.errors===0 && s.skipped===0,'MAVEN_PROOF');
  }
  gate(r.node?.tests>=960 && r.node.failures===0 && r.node.skipped===0 && r.angular?.tests>=533 && r.angular.files>=85,'FRONTEND_NODE_PROOF');
  gate(r.browser?.desktop===13 && r.browser.mobile320===13 && r.browser.owned_cleanup===2,'BROWSER_PROOF');
  const restore=r.restore;
  gate(restore?.status==='passed' && Number.isFinite(restore.rpo) && restore.rpo>=0 && restore.rpo<=3600 && Number.isFinite(restore.rto) && restore.rto>=0 && restore.rto<=14400,'RESTORE_SLO');
  gate(restore.substrates?.betufu?.restored===true && restore.substrates.betufu.chat_facts>0 && restore.substrates.keycloak?.restored===true && restore.substrates.keycloak.users>0 && restore.substrates['private-objects']?.restored===true && restore.substrates['private-objects'].nonempty===true,'RESTORE_SUBSTRATES');
  gate(restore.suppression?.register_current===true && restore.suppression.applied_before_serving===true && restore.suppression.served_after_reconciliation===0,'RESTORE_SUPPRESSION');
  gate(r.retention?.common_days===90 && r.retention.evidence_days===180 && r.retention.other_expurgo==='blocked' && r.retention.independent_legal_review==='pending','RETENTION_SCOPE');
  gate(Array.isArray(r.prohibitions) && r.prohibitions.length===35 && r.prohibitions.every((p,i)=>p.plan===plans[i] && p.status==='reviewed-local' && JSON.stringify(p.evidence)===JSON.stringify(['maven','browser','publication'])),'PROHIBITION_REVIEW');
}

function suiteReports(start) {
  const counts={},artifacts={},classes=[];
  for (const name of ['surefire','failsafe']) {
    const dir=`backend/target/${name}-reports`,total={classes:0,tests:0,failures:0,errors:0,skipped:0};
    for (const file of fs.readdirSync(resolve(ROOT,dir)).filter(p=>/^TEST-.*\.xml$/u.test(p))) {
      const p=`${dir}/${file}`,bytes=read(p),suite=readSuite(bytes.toString('utf8'));
      gate(fs.statSync(resolve(ROOT,p)).mtimeMs>=Date.parse(start),'STALE_XML');
      gate(suite.tests>0 && suite.failures===0 && suite.errors===0 && suite.skipped===0,'XML_FAILURE');
      for (const key of Object.keys(suite)) total[key]+=suite[key];total.classes++;artifacts[p]=hash(bytes);classes.push(file.slice(5,-4).split('.').at(-1));
    }
    gate(total.classes>0,'MISSING_XML');counts[name]=total;
  }
  for (const name of ['Phase7ConcurrencyIT','Phase7PrivacyIT','Phase7CertificationIT','ModulithArchitectureTests','FlywayMigrationIT','ChatWriteIT','ChatReadIT','ChatPushSecurityIT','ChatPushDeliveryIT','ChatReportIT','ChatMuteIT','ChatModerationIT','ChatSanctionIT','ChatReviewIT','ChatLifecycleConcurrencyIT','ChatSanctionConcurrencyIT','PrivacyRightsIT','PrivacyExportIT','PrivacyRetentionIT','EmergencyPauseIT']) gate(classes.includes(name),`MISSING_CLASS_${name}`);
  return {counts,artifacts,classes};
}
const textLog = path => read(path).toString('utf8').replace(/\x1b\[[0-9;]*m/gu,'');
const count = (text,pattern) => {const matches=[...text.matchAll(pattern)];gate(matches.length>0,'REPORT_COUNT');return Number(matches.at(-1)[1]);};

async function currentEvidence() {
  const e=json(EVIDENCE),head=git('rev-parse','HEAD'),digest=await sourceDigest();
  gate(e.head===head && e.source_digest===digest && e.commands?.length===Object.keys(commands).length,'EXECUTION_BINDING');
  const artifacts={},logs={};
  for (const c of e.commands) {
    gate(c.log===`.git/gsd/phase7/07-32-${c.label}-proof.log`,'LOG_PATH');
    const bytes=read(c.log);gate(hash(bytes)===c.log_sha256,'LOG_DRIFT');artifacts[c.log]=c.log_sha256;logs[c.label]=textLog(c.log);
    gate(c.source_digest===digest && c.head===head,'COMMAND_SOURCE_DRIFT');
  }
  gate(logs.focal.includes('BUILD SUCCESS') && logs.maven.includes('BUILD SUCCESS'),'MAVEN_EXIT');
  gate(logs.publication.includes('INSTITUTIONAL_IDENTITY'),'PUBLICATION_REFUSAL');
  gate((logs.browser.match(/\b13 passed\b/gu)??[]).length===2 && logs.browser.includes('[phase7-desktop]') && logs.browser.includes('[phase7-320px]') && !/\b[1-9]\d* (?:failed|skipped)\b/u.test(logs.browser),'BROWSER_LOG');
  const xml=suiteReports(e.commands.find(c=>c.label==='maven').started_at);Object.assign(artifacts,xml.artifacts);
  const policy=json('docs/phase7-privacy-policy.json'),approval=json('docs/phase7-approvals/07-32.json');
  for (const [p,d] of Object.entries(approval.baseline_hashes).filter(([p])=>!['.planning/STATE.md','.planning/ROADMAP.md'].includes(p))) {gate(hash(read(p))===d,'PROTECTED_DRIFT');artifacts[p]=d;}
  gate(!fs.existsSync(resolve(ROOT,'docs/manual-testing.md')),'OWNER_DOCUMENT_STATE');
  for(const p of [...plans.map(p=>`${PHASE}/${p}-PLAN.md`),'docs/phase7-decisions.json','docs/phase7-privacy-policy.json','docs/phase7-security-gate.json','docs/phase7-load-report.json','docs/phase7-restore-report.json','docs/phase7-suppression-register.json','docs/phase7-approvals/07-32.json','frontend/playwright.phase7.config.ts','infra/load/phase7-load-profile.json']) artifacts[p]=hash(read(p));
  assertRetentionPolicy(policy);
  const r={schema_version:1,status:'passed-local',scope:'isolated-local-technical-only',head,source_digest:digest,
    produced_at:new Date().toISOString(),public_launch_authorized:false,publication_status:'blocked',commands:e.commands,
    requirements,requirement_limits:{'PRIV-03':'Publication refusal verified; public publication not certified','PRIV-04':'Legitimate institutional identity remains pending','PRIV-05':'Local rights flows verified; independent legal review and other class policies remain pending'},
    gates:Object.fromEntries(requiredGates.map(name=>[name,json('docs/phase7-decisions.json').gates[name]])),
    security:json('docs/phase7-security-gate.json'),
    decisions:decisionIds,decision_evidence:Object.fromEntries(decisionIds.map(id=>[id,['D-10','D-32','D-38'].includes(id)?['restore']:id==='D-33'?['load']:['D-11','D-34'].includes(id)?['security','node']:id==='D-14'?['protected-artifacts']:['maven','node','browser']])),
    maven:xml.counts,maven_classes:xml.classes,
    node:{tests:count(logs.node,/^(?:ℹ|#) tests (\d+)\r?$/gmu),failures:count(logs.node,/^(?:ℹ|#) fail (\d+)\r?$/gmu),skipped:count(logs.node,/^(?:ℹ|#) skipped (\d+)\r?$/gmu)},
    angular:{tests:count(logs.angular,/Tests\s+(\d+) passed/gu),files:count(logs.angular,/Test Files\s+(\d+) passed/gu)},
    browser:{desktop:13,mobile320:13,owned_cleanup:(logs.browser.match(/Removed only betufu-phase7-e2e-/gu)??[]).length},
    restore:json('docs/phase7-restore-report.json'),
    retention:{common_days:policy.classes.common_messages.retention.days,evidence_days:policy.classes.moderation_evidence.retention.days,other_expurgo:'blocked',independent_legal_review:policy.legal_review.status},
    prohibitions:plans.map(plan=>({plan,status:'reviewed-local',evidence:['maven','browser','publication'],descriptor:'Historical planning status is superseded only by these bounded execution proofs; no public-launch waiver'})),
    artifacts,limits:['Local technical proof only; public pilot/institutional identity/independent legal review/production operations remain blocked','Dependency scan is not an external application pentest','Source digest plus approval base HEAD binds precommit evidence; at most the exact implementation/documentation commits may follow','Inline read-only review; no independent RepoWise or external review was available']};
  return r;
}
async function validate(r) {
  const live=git('rev-parse','HEAD');
  gate([live,...git('log','-2','--format=%P').split(/\s+/u)].includes(r.head),'STALE_HEAD');
  execFileSync('git',['merge-base','--is-ancestor',r.head,live],{cwd:ROOT,stdio:'pipe'});
  const approval=json('docs/phase7-approvals/07-32.json');
  const allowed=new Set([...approval.scope.paths,`${PHASE}/07-32-PLAN.md`,...approval.closeout_authorization.documentation_paths]);
  for(const p of [...git('diff','--name-only',r.head).split('\n'),...git('ls-files','--others','--exclude-standard').split('\n')].filter(Boolean)) gate(allowed.has(p),'POST_REVIEW_SCOPE');
  assertCertification(r,{head:r.head,source_digest:await sourceDigest(),now:Date.now()});
  gate(r.artifacts && Object.keys(r.artifacts).length>190,'MISSING_ARTIFACTS');
  for(const [p,d] of Object.entries(r.artifacts)) gate(/^[a-f0-9]{64}$/u.test(d) && hash(read(p))===d,`ARTIFACT_DRIFT_${p}`);
  const xml=suiteReports(r.commands.find(c=>c.label==='maven').started_at);
  gate(JSON.stringify(xml.counts)===JSON.stringify(r.maven) && JSON.stringify(xml.classes)===JSON.stringify(r.maven_classes),'XML_TOTAL_DRIFT');
  for(const [p,d] of Object.entries(xml.artifacts)) gate(r.artifacts[p]===d,'MISSING_XML_HASH');
  for(const c of r.commands) {
    gate(c.log===`.git/gsd/phase7/07-32-${c.label}-proof.log` && r.artifacts[c.log]===c.log_sha256 && hash(read(c.log))===c.log_sha256,'COMMAND_LOG_BINDING');
    gate(c.head===r.head && c.source_digest===r.source_digest,'COMMAND_SOURCE_DRIFT');
  }
  const node=textLog(r.commands.find(c=>c.label==='node').log),angular=textLog(r.commands.find(c=>c.label==='angular').log),browser=textLog(r.commands.find(c=>c.label==='browser').log);
  gate(count(node,/^(?:ℹ|#) tests (\d+)\r?$/gmu)===r.node.tests && count(node,/^(?:ℹ|#) fail (\d+)\r?$/gmu)===0 && count(node,/^(?:ℹ|#) skipped (\d+)\r?$/gmu)===0,'NODE_LOG_DRIFT');
  gate(count(angular,/Tests\s+(\d+) passed/gu)===r.angular.tests && count(angular,/Test Files\s+(\d+) passed/gu)===r.angular.files,'ANGULAR_LOG_DRIFT');
  gate((browser.match(/\b13 passed\b/gu)??[]).length===2 && !/\b[1-9]\d* (?:failed|skipped)\b/u.test(browser),'BROWSER_LOG_DRIFT');
  gate(JSON.stringify(json('docs/phase7-restore-report.json'))===JSON.stringify(r.restore),'RESTORE_REPORT_DRIFT');
  assertSecurityReport(json('docs/phase7-security-gate.json'),await manifestDigests());
  const load=json('docs/phase7-load-report.json');assertLoadReport(load,json('infra/load/phase7-load-profile.json'));
  gate(load.source_digest===r.source_digest && fresh(load.produced_at,Date.now()),'STALE_LOAD');
  const restore=r.commands.find(c=>c.label==='restore');
  gate(r.restore.suppression.captured_after_snapshot_at>=Date.parse(restore.started_at) && r.restore.suppression.captured_after_snapshot_at<=Date.parse(restore.ended_at),'STALE_RESTORE');
  const decisions=json('docs/phase7-decisions.json');
  gate(decisionIds.every(id=>decisions.decisions.some(d=>d.id===id && d.human_reference)),'DECISION_AUTHORITY');
  for(const name of requiredGates) {
    const g=decisions.gates[name];gate(g?.status==='approved' && g.source==='owner' && JSON.stringify(g)===JSON.stringify(r.gates[name]),'MISSING_GATE');
    for(const [p,d] of Object.entries(g.evidence_hashes??{})) gate(hash(read(p))===d,'GATE_EVIDENCE_DRIFT');
  }
  gate(decisions.gates.institutional.status==='pending' && decisions.gates.local_technical_scope.public_launch_authorized===false,'LAUNCH_SCOPE');
  return r;
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    gate(process.argv.length===3 && ['--local','--record-local'].includes(process.argv[2]),'ARGUMENTS');
    let report;
    if(process.argv[2]==='--record-local') {await validatePhase7Approvals({plan:'07-32',stage:'implementation'});report=await currentEvidence();await validate(report);fs.writeFileSync(resolve(ROOT,OUTPUT),JSON.stringify(report,null,2)+'\n');}
    else report=await validate(json(OUTPUT));
    console.log(`[phase7] LOCAL VERIFIED:16 requirements mapped,40 decisions,35 prohibition dispositions; Maven${report.maven.surefire.tests+report.maven.failsafe.tests},Node${report.node.tests},Angular${report.angular.tests},browser26/26. PUBLIC LAUNCH BLOCKED.`);
  } catch(error) {console.error(`[phase7] ${error.message}`);process.exitCode=1;}
}
