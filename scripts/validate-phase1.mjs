#!/usr/bin/env node

/**
 * scripts/validate-phase1.mjs
 *
 * Orquestrador fail-fast do gate agregado da Fase 1.
 *
 * Este é o esqueleto criado pelo Plano 01-11 (Task 1).
 * Cada plano posterior (01-12 a 01-28) adicionará seu modo focal.
 * O gate integral final pertence ao Plano 01-16.
 *
 * Modos disponíveis:
 *   --infra    Valida profile daily (e full quando Task 2 estiver pronta)
 *   --all      Executa todos os modos disponíveis em sequência fail-fast
 *
 * Modos futuros (stubs):
 *   --session-ui (01-27) — store/layout autenticado
 *   --diagnostic-ui (01-28) — rotas E2E e ausência production
 *   (sem argumento = 01-16 gate integral)
 */

import { readdir, readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { runCaptured } from './lib/compose.mjs';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const devScript = resolve(scriptsDirectory, 'dev.mjs');
const projectRoot = resolve(scriptsDirectory, '..');
const specPath = resolve(projectRoot, 'backend', 'openapi', 'betufu.yaml');
const backendSources = resolve(projectRoot, 'backend', 'src', 'main', 'java');
const generatorScript = resolve(projectRoot, 'frontend', 'scripts', 'generate-api.mjs');

// ---------------------------------------------------------------------------
// Modos
// ---------------------------------------------------------------------------

async function modeInfra() {
  console.log('[validate-phase1] === Modo: --infra ===');
  console.log('[validate-phase1] 1/2: Executando: node scripts/dev.mjs verify daily');

  const dailyResult = await runCaptured(
    process.execPath,
    [devScript, 'verify', 'daily'],
    { timeoutMs: 600_000 }, // 10 minutos
  );

  if (dailyResult.stdout) console.log(dailyResult.stdout);
  if (dailyResult.stderr) console.error(dailyResult.stderr);

  if (dailyResult.exitCode !== 0) {
    console.error(`[validate-phase1] FALHA: verify daily retornou exit code ${dailyResult.exitCode}`);
    return false;
  }

  console.log('');
  console.log('[validate-phase1] 2/2: Executando: node scripts/dev.mjs verify full');

  const fullResult = await runCaptured(
    process.execPath,
    [devScript, 'verify', 'full'],
    { timeoutMs: 600_000 }, // 10 minutos
  );

  if (fullResult.stdout) console.log(fullResult.stdout);
  if (fullResult.stderr) console.error(fullResult.stderr);

  if (fullResult.exitCode !== 0) {
    console.error(`[validate-phase1] FALHA: verify full retornou exit code ${fullResult.exitCode}`);
    return false;
  }

  console.log('[validate-phase1] PASS: --infra (daily e full)');
  return true;
}

/**
 * Percorre os fontes Java e devolve todo path /api/v1 declarado em anotacao de mapping.
 *
 * A leitura e textual de proposito: o objetivo e comparar o que o codigo publica com o que o
 * contrato documenta, sem subir contexto Spring. Um mapping novo que ninguem documentou aparece
 * aqui mesmo que nenhum teste o exercite.
 */
async function declaredApiPaths(directory) {
  const found = new Set();
  const entries = await readdir(directory, { withFileTypes: true });

  for (const entry of entries) {
    const full = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      for (const path of await declaredApiPaths(full)) found.add(path);
      continue;
    }
    if (!entry.name.endsWith('.java')) continue;

    const source = await readFile(full, 'utf8');
    for (const match of source.matchAll(/"(\/api\/v1[^"]*)"/gu)) {
      const path = match[1];
      // Padroes com curinga sao matchers de seguranca, nao mappings publicados.
      // /api/v1/** existe em SecurityConfiguration e nao corresponde a nenhum path OpenAPI.
      if (path.includes('*') || path.includes('{')) continue;
      found.add(path);
    }
  }
  return found;
}

async function modeContract() {
  console.log('[validate-phase1] === Modo: --contract ===');
  console.log('[validate-phase1] 1/2: paridade entre mappings /api/v1 e o contrato publicado');

  const spec = await readFile(specPath, 'utf8');
  const declared = [...await declaredApiPaths(backendSources)].sort();
  const missing = declared.filter((path) => !spec.includes(`
  ${path}:`));

  for (const path of declared) {
    console.log(`[validate-phase1]   ${missing.includes(path) ? 'AUSENTE ' : 'ok      '} ${path}`);
  }

  if (missing.length > 0) {
    console.error(
      `[validate-phase1] FALHA: ${missing.length} mapping(s) sem path no contrato: ${missing.join(', ')}`,
    );
    return false;
  }

  console.log('');
  console.log('[validate-phase1] 2/2: drift do cliente gerado (generate-api --check)');

  const checkResult = await runCaptured(
    process.execPath,
    [generatorScript, '--check'],
    { cwd: resolve(projectRoot, 'frontend'), timeoutMs: 600_000 },
  );

  if (checkResult.stdout) console.log(checkResult.stdout);
  if (checkResult.stderr) console.error(checkResult.stderr);

  if (checkResult.exitCode !== 0) {
    console.error(
      `[validate-phase1] FALHA: generate-api --check retornou exit code ${checkResult.exitCode}`,
    );
    return false;
  }

  console.log('[validate-phase1] PASS: --contract (paridade e ausencia de drift)');
  return true;
}

async function collectFiles(directory) {
  const files = [];
  try {
    const entries = await readdir(directory, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = resolve(directory, entry.name);
      if (entry.isDirectory()) {
        files.push(...await collectFiles(fullPath));
      } else {
        files.push(fullPath);
      }
    }
  } catch {
    // Diretório pode não existir
  }
  return files;
}

export async function validateSessionUiContract(rootDir = projectRoot) {
  const sessionDir = resolve(rootDir, 'frontend', 'src', 'app', 'core', 'session');
  const layoutDir = resolve(rootDir, 'frontend', 'src', 'app', 'layout');
  const routesDevFile = resolve(rootDir, 'frontend', 'src', 'app', 'app.routes.development.ts');
  const routesProdFile = resolve(rootDir, 'frontend', 'src', 'app', 'app.routes.production.ts');

  const targetFiles = (await Promise.all([collectFiles(sessionDir), collectFiles(layoutDir)]))
    .flat()
    .filter((f) => f.endsWith('.ts') || f.endsWith('.html'))
    .filter((f) => !f.endsWith('.spec.ts'));

  const prohibitedStorage = ['localStorage', 'sessionStorage', 'indexedDB', 'document.cookie', 'Bearer '];
  const prohibitedLoops = ['setInterval', 'timer(', 'interval(', 'retry(', 'retryWhen('];

  for (const file of targetFiles) {
    const source = await readFile(file, 'utf8');
    for (const token of prohibitedStorage) {
      if (source.includes(token)) {
        console.error(`[validate-phase1] FALHA: ${file} contem token proibido: '${token}'`);
        return false;
      }
    }
    for (const token of prohibitedLoops) {
      if (source.includes(token)) {
        console.error(`[validate-phase1] FALHA: ${file} contem chamada de polling/retry: '${token}'`);
        return false;
      }
    }
  }

  const routesDevSource = await readFile(routesDevFile, 'utf8');
  const routesProdSource = await readFile(routesProdFile, 'utf8');

  if (!routesDevSource.includes('AuthenticatedLayoutComponent')) {
    console.error('[validate-phase1] FALHA: app.routes.development.ts nao encapsula rotas sob AuthenticatedLayoutComponent');
    return false;
  }

  if (routesProdSource.includes('AuthenticatedLayoutComponent') || routesProdSource.includes('TracerPageComponent')) {
    console.error('[validate-phase1] FALHA: app.routes.production.ts contem componentes de desenvolvimento/diagnostico');
    return false;
  }

  return true;
}

async function modeSessionUi() {
  console.log('[validate-phase1] === Modo: --session-ui ===');
  console.log('[validate-phase1] 1/3: verificando isolamento OIDC e contratos de UI de sessao');

  const contractOk = await validateSessionUiContract(projectRoot);
  if (!contractOk) {
    return false;
  }

  console.log('[validate-phase1] 2/3: verificando testes unitarios de sessao e layout (Vitest)');
  const ngCli = resolve(projectRoot, 'frontend', 'node_modules', '@angular', 'cli', 'bin', 'ng.js');
  const testResult = await runCaptured(
    process.execPath,
    [ngCli, 'test', '--watch=false'],
    { cwd: resolve(projectRoot, 'frontend'), timeoutMs: 120_000 },
  );

  if (testResult.stdout) console.log(testResult.stdout);
  if (testResult.stderr) console.error(testResult.stderr);

  if (testResult.exitCode !== 0) {
    console.error(`[validate-phase1] FALHA: testes unitarios de frontend retornaram exit code ${testResult.exitCode}`);
    return false;
  }

  console.log('[validate-phase1] 3/3: verificando compilacao do bundle de frontend (ng build)');
  const buildResult = await runCaptured(
    process.execPath,
    [ngCli, 'build', '--configuration', 'production'],
    { cwd: resolve(projectRoot, 'frontend'), timeoutMs: 120_000 },
  );

  if (buildResult.stdout) console.log(buildResult.stdout);
  if (buildResult.stderr) console.error(buildResult.stderr);

  if (buildResult.exitCode !== 0) {
    console.error(`[validate-phase1] FALHA: ng build production retornou exit code ${buildResult.exitCode}`);
    return false;
  }

  console.log('[validate-phase1] PASS: --session-ui (store, layout, testes e build production)');
  return true;
}

export async function main() {
  const args = process.argv.slice(2);
  const modes = new Set(args.filter((arg) => arg.startsWith('--')));

  // Sem argumentos: reservado para o gate integral no Plano 01-16
  if (modes.size === 0) {
    console.error('[validate-phase1] Especifique um modo: --infra ou --all');
    console.error('[validate-phase1] (A execução sem argumentos será o gate integral final no Plano 01-16).');
    process.exit(1);
  }

  const runAll = modes.has('--all');

  const availableModes = [
    { flag: '--infra', fn: modeInfra },
    // Modos futuros serão adicionados aqui pelos planos 01-12 a 01-28:
    { flag: '--contract', fn: modeContract },
    { flag: '--session-ui', fn: modeSessionUi },
    // { flag: '--diagnostic-ui', fn: modeDiagnosticUi },
  ];

  let failed = false;

  const knownFlags = new Set(availableModes.map((m) => m.flag).concat(['--all']));
  for (const flag of modes) {
    if (!knownFlags.has(flag)) {
      console.error(`[validate-phase1] Flag desconhecida: ${flag}`);
      console.error('[validate-phase1] Flags disponíveis: --infra, --all');
      failed = true;
    }
  }

  if (!failed) {
    try {
      for (const mode of availableModes) {
        if (runAll || modes.has(mode.flag)) {
          const success = await mode.fn();
          if (!success) {
            failed = true;
            console.error(`[validate-phase1] Parado na primeira falha: ${mode.flag}`);
            break; // fail-fast
          }
        }
      }
    } catch (error) {
      console.error(`[validate-phase1] ERRO: ${error instanceof Error ? error.message : String(error)}`);
      failed = true;
    }
  }

  if (failed) {
    process.exitCode = 1;
  } else {
    console.log('');
    console.log('[validate-phase1] === Todos os modos passaram ===');
  }
}

const isMain = process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url;
if (isMain) {
  void main();
}

