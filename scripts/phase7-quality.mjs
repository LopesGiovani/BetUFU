#!/usr/bin/env node
import { spawn, execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, writeFile, lstat } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(import.meta.dirname, '..');
const manifests = ['backend/pom.xml', 'frontend/package-lock.json'];
const dependencyCommand = 'node scripts/maven.mjs -f backend/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.10.0:tree -DoutputType=json -DoutputFile=target/phase7-dependencies.json';
export const qualityCommands = Object.freeze([
  'node scripts/maven.mjs -f backend/pom.xml verify',
  'node --test scripts/*.test.mjs',
  'npm --prefix frontend test -- --watch=false',
  'npm --prefix frontend run generate:api:check',
  'npm --prefix frontend run lint',
  'npm --prefix frontend run build:production',
  'node scripts/phase7-load.mjs --profile infra/load/phase7-load-profile.json --isolated',
  'git diff --check',
]);
const requireGate = (condition, code) => { if (!condition) throw Error(`BLOCKED: ${code}`); };
const hash = bytes => createHash('sha256').update(bytes).digest('hex');

async function shellCommand(command) {
  requireGate([...qualityCommands, dependencyCommand].includes(command), 'QUALITY_COMMAND');
  console.log(`[phase7-quality] ${command}`);
  // Only closed literal commands reach the shell; no CLI, URL or secret interpolation.
  await new Promise((done, reject) => {
    const child = spawn(command, { cwd: ROOT, shell: true, windowsHide: true, stdio: 'inherit',
      env: { ...process.env, VITEST_MAX_WORKERS: '2' } });
    child.on('error', reject);
    child.on('close', code => code === 0 ? done() : reject(Error(`BLOCKED: QUALITY_EXIT_${code}`)));
  });
}
export async function runCommands(execute = shellCommand) {
  for (const command of qualityCommands) await execute(command);
}
export async function manifestDigests() {
  return Object.fromEntries(await Promise.all(manifests.map(async p => [p, hash(await readFile(resolve(ROOT, p)))])));
}
export async function sourceDigest() {
  const paths = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'],
    { cwd: ROOT, encoding: 'utf8' }).split('\0').filter(p =>
    /^(backend\/(src\/|openapi\/|pom\.xml|Dockerfile)|frontend\/(src\/|e2e\/|scripts\/|package|angular\.json)|scripts\/|infra\/|\.github\/|pom\.xml$)/.test(p));
  const values = [];
  for (const p of [...new Set(paths)].sort()) {
    requireGate(!(await lstat(resolve(ROOT, p))).isSymbolicLink(), 'SOURCE_SYMLINK');
    values.push([p, hash(await readFile(resolve(ROOT, p)))]);
  }
  return hash(JSON.stringify(values));
}
export function assertSecurityReport(report, inputs, now = Date.now()) {
  requireGate(report?.schema_version === 1 && report.status === 'passed', 'SECURITY_REPORT');
  const age = now - Date.parse(report.produced_at);
  requireGate(Number.isFinite(age) && age >= -300_000 && age <= 86_400_000, 'SECURITY_STALE');
  requireGate(JSON.stringify(report.inputs) === JSON.stringify(inputs) && Object.keys(inputs).length > 0, 'SECURITY_DRIFT');
  requireGate(Array.isArray(report.scans) && ['Maven', 'npm'].every(ecosystem =>
    report.scans.some(scan => scan.ecosystem === ecosystem && scan.status === 'passed' &&
      Number.isSafeInteger(scan.packages) && scan.packages > 0)), 'SECURITY_COVERAGE');
  requireGate(Array.isArray(report.findings), 'SECURITY_FINDINGS');
  requireGate(report.findings.length === 0, 'CONFIRMED_VULNERABILITY');
}
export async function scanSecurity() {
  const inputs = await manifestDigests();
  const report = { schema_version: 1, status: 'failed', produced_at: new Date().toISOString(),
    head: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: ROOT, encoding: 'utf8' }).trim(),
    inputs, scans: [], findings: [], public_launch_authorized: false,
    method: 'Resolved Maven/npm versions queried against OSV; no severity exemptions',
    limitations: ['Dependency vulnerability scan; not an external application pentest or institutional approval'] };
  try {
    await shellCommand(dependencyCommand);
    const tree = JSON.parse(await readFile(resolve(ROOT, 'backend/target/phase7-dependencies.json'), 'utf8'));
    const java = [];
    const walk = node => { for (const child of node.children ?? []) {
      requireGate(child.groupId && child.artifactId && child.version, 'DEPENDENCY_INVENTORY');
      java.push({ package: { ecosystem: 'Maven', name: `${child.groupId}:${child.artifactId}` }, version: child.version }); walk(child);
    } };
    walk(tree);
    const lock = JSON.parse(await readFile(resolve(ROOT, 'frontend/package-lock.json'), 'utf8'));
    const npm = Object.entries(lock.packages).filter(([p, v]) => p && !v.link).map(([p, v]) => {
      requireGate(typeof v.version === 'string', 'DEPENDENCY_INVENTORY');
      return { package: { ecosystem: 'npm', name: v.name ?? p.split('node_modules/').at(-1) }, version: v.version };
    });
    for (const [ecosystem, packages] of [['Maven', java], ['npm', npm]]) {
      const queries = [...new Map(packages.map(p => [JSON.stringify(p), p])).values()];
      requireGate(queries.length > 0, 'EMPTY_SCAN');
      for (let offset = 0; offset < queries.length; offset += 100) {
        const batch = queries.slice(offset, offset + 100);
        const response = await fetch('https://api.osv.dev/v1/querybatch', { method: 'POST',
          headers: { 'content-type': 'application/json' }, body: JSON.stringify({ queries: batch }),
          signal: AbortSignal.timeout(30_000), redirect: 'error' });
        requireGate(response.ok, 'SCANNER_HTTP');
        const result = await response.json();
        requireGate(Array.isArray(result.results) && result.results.length === batch.length, 'SCANNER_RESPONSE');
        for (let index = 0; index < batch.length; index++) {
          const entry = result.results[index];
          requireGate(entry && typeof entry === 'object' && !entry.error &&
            (entry.vulns === undefined || Array.isArray(entry.vulns)), 'SCANNER_RESPONSE');
          for (const vuln of entry.vulns ?? []) {
            requireGate(typeof vuln.id === 'string' && vuln.id.length > 0, 'SCANNER_RESPONSE');
            const detailResponse = await fetch(`https://api.osv.dev/v1/vulns/${encodeURIComponent(vuln.id)}`, {
              signal: AbortSignal.timeout(30_000), redirect: 'error' });
            requireGate(detailResponse.ok, 'SCANNER_DETAIL');
            const detail = await detailResponse.json();
            requireGate(detail.id === vuln.id && Array.isArray(detail.affected), 'SCANNER_DETAIL');
            report.findings.push({ id: vuln.id, ecosystem, package: batch[index].package.name,
              version: batch[index].version, severity: detail.database_specific?.severity ?? 'UNKNOWN',
              summary: detail.summary, withdrawn: detail.withdrawn ?? null,
              reference: `https://osv.dev/vulnerability/${encodeURIComponent(vuln.id)}`,
              fixed_versions: [...new Set(detail.affected.filter(a => a.package?.ecosystem === ecosystem &&
                a.package?.name === batch[index].package.name).flatMap(a =>
                (a.ranges ?? []).flatMap(r => (r.events ?? []).filter(e => e.fixed).map(e => e.fixed))))],
              disposition: 'affected-version-match; exploitability-not-assessed' });
          }
        }
      }
      report.scans.push({ ecosystem, status: 'passed', packages: queries.length });
    }
    requireGate(JSON.stringify(await manifestDigests()) === JSON.stringify(inputs), 'SCAN_INPUT_CHANGED');
    report.status = report.findings.length === 0 ? 'passed' : 'failed';
  } catch {
    report.failure = 'Scanner or resolved inventory unavailable/incomplete; never equivalent to zero findings';
  }
  await writeFile(resolve(ROOT, 'docs/phase7-security-gate.json'), JSON.stringify(report, null, 2) + '\n');
  assertSecurityReport(report, inputs);
  console.log(`[phase7-quality] Security passed: ${report.scans.map(s => `${s.ecosystem}:${s.packages}`).join(',')}`);
  return report;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = process.argv.slice(2);
    requireGate(args.length <= 1 && (!args.length || ['--local', '--scan-security', '--publication'].includes(args[0])), 'QUALITY_ARGUMENTS');
    if (args[0] === '--scan-security') await scanSecurity();
    else if (args[0] === '--publication' || !args.length) {
      const { validatePublicationPrivacy } = await import('./validate-publication-privacy.mjs');
      await validatePublicationPrivacy();
      assertSecurityReport(JSON.parse(await readFile(resolve(ROOT, 'docs/phase7-security-gate.json'), 'utf8')), await manifestDigests());
    } else {
      await runCommands(); await scanSecurity();
      console.log('[phase7-quality] LOCAL TECHNICAL CHECKS PASSED; publication/public pilot remain BLOCKED.');
    }
  } catch (error) { console.error(`[phase7-quality] ${error.message}`); process.exitCode = 1; }
}
