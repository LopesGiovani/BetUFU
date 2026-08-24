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
 * Prefixo reservado a canais maquina-a-maquina. Fica FORA do contrato publico consumido pelo
 * cliente Angular: publicar o endpoint interno no OpenAPI geraria metodo no SPA para uma rota
 * que so aceita envelope HMAC. Sua seguranca continua governada pelos validadores dedicados
 * (validate-runtime-prerequisites) e pela cadeia stateless propria.
 */
const INTERNAL_PATH_PREFIX = '/api/v1/internal/';

const MAPPING_ANNOTATION = /@(?:Get|Post|Put|Patch|Delete|Request)Mapping\s*\(/gu;

/** Le o bloco entre parenteses balanceados a partir do indice de abertura. */
export function readBalanced(source, openIndex) {
  let depth = 0;
  for (let i = openIndex; i < source.length; i += 1) {
    const ch = source[i];
    if (ch === '"') {
      i += 1;
      while (i < source.length && source[i] !== '"') {
        if (source[i] === '\\') i += 1;
        i += 1;
      }
      continue;
    }
    if (ch === '(') depth += 1;
    else if (ch === ')') {
      depth -= 1;
      if (depth === 0) return source.slice(openIndex + 1, i);
    }
  }
  return '';
}

/** Divide por virgulas de topo, ignorando virgulas dentro de chaves, parenteses ou strings. */
function splitTopLevel(text) {
  const parts = [];
  let depth = 0;
  let current = '';
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '"') {
      current += ch;
      i += 1;
      while (i < text.length && text[i] !== '"') {
        if (text[i] === '\\') { current += text[i]; i += 1; }
        current += text[i];
        i += 1;
      }
      current += '"';
      continue;
    }
    if (ch === '{' || ch === '(') depth += 1;
    if (ch === '}' || ch === ')') depth -= 1;
    if (ch === ',' && depth === 0) { parts.push(current); current = ''; continue; }
    current += ch;
  }
  if (current.trim()) parts.push(current);
  return parts.map((part) => part.trim()).filter(Boolean);
}

/**
 * Resolve uma expressao de path para o texto final.
 *
 * Aceita literal, constante `static final String` do proprio arquivo e concatenacao simples
 * dessas duas formas. Devolve null quando algum termo nao for resolvivel, para que uma
 * expressao dinamica nunca vire um path inventado.
 */
export function resolvePathExpression(expression, constants) {
  let result = '';
  for (const term of expression.split('+').map((item) => item.trim())) {
    const literal = term.match(/^"([^"]*)"$/u);
    if (literal) { result += literal[1]; continue; }
    if (Object.hasOwn(constants, term)) { result += constants[term]; continue; }
    return null;
  }
  return result;
}

/**
 * Extrai de um fonte Java os paths publicados por anotacao de mapping.
 *
 * Somente anotacao conta. Constante de prefixo, matcher de seguranca e literal solto em filtro
 * NAO sao mappings: a versao anterior coletava qualquer literal "/api/v1..." e por isso acusava
 * ROLES_BASE_PATH (prefixo), o endpoint interno M2M e literais do ValidActivityFilter como se
 * fossem endpoints sem contrato.
 */
export function extractMappedPaths(source) {
  const constants = {};
  for (const match of source.matchAll(
    /static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"/gu,
  )) {
    constants[match[1]] = match[2];
  }

  const found = new Set();
  MAPPING_ANNOTATION.lastIndex = 0;
  for (const match of source.matchAll(MAPPING_ANNOTATION)) {
    const block = readBalanced(source, match.index + match[0].length - 1);
    if (!block.trim()) continue;

    const attributes = splitTopLevel(block);
    let expression = null;
    for (const attribute of attributes) {
      const named = attribute.match(/^(?:path|value)\s*=\s*([\s\S]+)$/u);
      if (named) { expression = named[1].trim(); break; }
    }
    // Forma posicional: @PostMapping("/api/v1/x") — o unico atributo e o proprio path.
    if (expression === null && attributes.length === 1 && !attributes[0].includes('=')) {
      expression = attributes[0];
    }
    if (expression === null) continue;

    const array = expression.match(/^\{([\s\S]*)\}$/u);
    const items = array ? splitTopLevel(array[1]) : [expression];
    for (const item of items) {
      const resolved = resolvePathExpression(item, constants);
      if (resolved === null || !resolved.startsWith('/api/v1')) continue;
      if (resolved.startsWith(INTERNAL_PATH_PREFIX)) continue;
      found.add(resolved);
    }
  }
  return found;
}

/**
 * Percorre os fontes Java e devolve todo path /api/v1 publicado por anotacao de mapping.
 *
 * A leitura e textual de proposito: o objetivo e comparar o que o codigo publica com o que o
 * contrato documenta, sem subir contexto Spring. Um mapping novo que ninguem documentou aparece
 * aqui mesmo que nenhum teste o exercite. Rotas parametrizadas sao preservadas, porque o
 * contrato OpenAPI as declara com a mesma grafia `{param}`.
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
    for (const path of extractMappedPaths(source)) found.add(path);
  }
  return found;
}

export function extractOpenApiPaths(spec) {
  const found = new Set();
  for (const match of spec.matchAll(/^  (\/api\/v1[^:]*):\s*$/gmu)) {
    found.add(match[1]);
  }
  return found;
}

export function compareApiPaths(declaredPaths, publishedPaths) {
  const declared = new Set(declaredPaths);
  const published = new Set(publishedPaths);
  return {
    missingFromContract: [...declared].filter((path) => !published.has(path)).sort(),
    missingFromRuntime: [...published].filter((path) => !declared.has(path)).sort(),
  };
}

async function modeContract() {
  console.log('[validate-phase1] === Modo: --contract ===');
  console.log('[validate-phase1] 1/2: paridade entre mappings /api/v1 e o contrato publicado');

  const spec = await readFile(specPath, 'utf8');
  const declared = [...await declaredApiPaths(backendSources)].sort();
  const published = [...extractOpenApiPaths(spec)].sort();
  const parity = compareApiPaths(declared, published);

  for (const path of declared) {
    console.log(`[validate-phase1]   ${parity.missingFromContract.includes(path) ? 'AUSENTE ' : 'ok      '} ${path}`);
  }

  if (parity.missingFromContract.length > 0 || parity.missingFromRuntime.length > 0) {
    console.error(
      `[validate-phase1] FALHA: paridade bidirecional quebrada; `
        + `sem contrato=[${parity.missingFromContract.join(', ')}]; `
        + `sem runtime=[${parity.missingFromRuntime.join(', ')}]`,
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
  if (buildResult.exitCode !== 0) {
    console.error(`[validate-phase1] FALHA: ng build production retornou exit code ${buildResult.exitCode}`);
    return false;
  }

  console.log('[validate-phase1] PASS: --session-ui (store, layout, testes e build production)');
  return true;
}

export async function validateDiagnosticUiContract(rootPath) {
  const routesDevFile = resolve(rootPath, 'frontend', 'src', 'app', 'app.routes.development.ts');
  const routesProdFile = resolve(rootPath, 'frontend', 'src', 'app', 'app.routes.production.ts');
  const diagnosticDir = resolve(rootPath, 'frontend', 'src', 'app', 'features', 'diagnostic');

  const prohibitedTokens = ['localStorage', 'sessionStorage', 'document.cookie', 'indexedDB', 'Bearer '];
  const prohibitedLoops = ['setInterval', 'interval('];

  const diagnosticFiles = (await collectFiles(diagnosticDir))
    .filter((f) => f.endsWith('.ts') || f.endsWith('.html'))
    .filter((f) => !f.endsWith('.spec.ts'));
  if (diagnosticFiles.length === 0) {
    console.error(`[validate-phase1] FALHA: nenhum arquivo de feature diagnostic encontrado em ${diagnosticDir}`);
    return false;
  }

  for (const file of diagnosticFiles) {
    const source = await readFile(file, 'utf8');
    for (const token of prohibitedTokens) {
      if (source.includes(token)) {
        console.error(`[validate-phase1] FALHA: ${file} contem token proibido: '${token}'`);
        return false;
      }
    }
    for (const token of prohibitedLoops) {
      if (source.includes(token)) {
        console.error(`[validate-phase1] FALHA: ${file} contem chamada de polling continuo: '${token}'`);
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

  if (!routesDevSource.includes('DiagnosticPageComponent') || !routesDevSource.includes("'diagnostic'")) {
    console.error('[validate-phase1] FALHA: app.routes.development.ts nao registra a rota /diagnostic com DiagnosticPageComponent');
    return false;
  }

  if (
    routesProdSource.includes('AuthenticatedLayoutComponent') ||
    routesProdSource.includes('TracerPageComponent') ||
    routesProdSource.includes('DiagnosticPageComponent') ||
    routesProdSource.includes("'tracer'") ||
    routesProdSource.includes("'diagnostic'")
  ) {
    console.error('[validate-phase1] FALHA: app.routes.production.ts contem componentes ou rotas de desenvolvimento/diagnostico');
    return false;
  }

  return true;
}

async function verifyProductionBundleAbsence(distDirectory) {
  const files = await collectFiles(distDirectory);
  if (files.length === 0) {
    console.error(`[validate-phase1] FALHA: nenhum arquivo gerado em ${distDirectory}`);
    return false;
  }

  for (const file of files) {
    if (!file.endsWith('.js') && !file.endsWith('.html') && !file.endsWith('.json')) {
      continue;
    }
    const content = await readFile(file, 'utf8');
    if (content.includes('DiagnosticPageComponent') || content.includes('TracerPageComponent')) {
      console.error(`[validate-phase1] FALHA: ${file} no bundle de producao vaza componentes tecnicos de desenvolvimento`);
      return false;
    }
  }

  return true;
}

async function modeDiagnosticUi() {
  console.log('[validate-phase1] === Modo: --diagnostic-ui ===');
  console.log('[validate-phase1] 1/3: verificando contratos de rotas build-specific e regras de seguranca de diagnostico');

  const contractOk = await validateDiagnosticUiContract(projectRoot);
  if (!contractOk) {
    return false;
  }

  console.log('[validate-phase1] 2/3: verificando testes unitarios de frontend (Vitest)');
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

  console.log('[validate-phase1] 3/3: compilando bundle de producao e verificando ausencia estrita de rotas tecnicas');
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

  const distDir = resolve(projectRoot, 'frontend', 'dist', 'betufu');
  const absenceOk = await verifyProductionBundleAbsence(distDir);
  if (!absenceOk) {
    return false;
  }

  console.log('[validate-phase1] PASS: --diagnostic-ui (contratos de rota, testes e bundle production sem vazamentos)');
  return true;
}

export function resolveBackendMavenArgs(goal = 'verify') {
  if (goal !== 'verify') {
    throw new Error(
      `Goal Maven inválido para o Gate da Fase 1: "${goal}". O Gate Nyquist exige "verify" para cobrir testes unitários (Surefire) e de integração (Failsafe).`
    );
  }
  return ['-f', 'backend/pom.xml', goal];
}

async function modeBackend() {
  console.log('[validate-phase1] === Modo: --backend (Maven & Spring Modulith / OIDC) ===');
  const mavenScript = resolve(projectRoot, 'scripts', 'maven.mjs');
  const mavenArgs = resolveBackendMavenArgs('verify');
  const result = await runCaptured(
    process.execPath,
    [mavenScript, ...mavenArgs],
    { cwd: projectRoot, timeoutMs: 600_000 },
  );

  if (result.stdout) console.log(result.stdout);
  if (result.stderr) console.error(result.stderr);

  if (result.exitCode !== 0) {
    console.error(`[validate-phase1] FALHA: testes Maven retornaram exit code ${result.exitCode}`);
    return false;
  }

  console.log('[validate-phase1] PASS: --backend (testes Maven, Spring Modulith, OIDC e dados)');
  return true;
}

export async function main() {
  const args = process.argv.slice(2);
  const modes = new Set(args.filter((arg) => arg.startsWith('--')));

  // Sem argumentos: executa o gate integral completo da Fase 1 (Plano 01-16)
  const isFullGate = modes.size === 0;
  const runAll = isFullGate || modes.has('--all');

  const availableModes = [
    { flag: '--infra', fn: modeInfra },
    { flag: '--contract', fn: modeContract },
    { flag: '--session-ui', fn: modeSessionUi },
    { flag: '--diagnostic-ui', fn: modeDiagnosticUi },
    { flag: '--backend', fn: modeBackend },
  ];

  let failed = false;

  const knownFlags = new Set(availableModes.map((m) => m.flag).concat(['--all']));
  for (const flag of modes) {
    if (!knownFlags.has(flag)) {
      console.error(`[validate-phase1] Flag desconhecida: ${flag}`);
      console.error('[validate-phase1] Flags disponíveis: --infra, --contract, --session-ui, --diagnostic-ui, --backend, --all');
      failed = true;
    }
  }

  if (!failed) {
    try {
      if (isFullGate) {
        console.log('[validate-phase1] ========================================================');
        console.log('[validate-phase1] === GATE INTEGRAL DA FASE 1 (Plano 01-16 Nyquist)   ===');
        console.log('[validate-phase1] ========================================================');
      }

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
    console.log('[validate-phase1] === Gate da Fase 1: TODOS OS MODOS PASSARAM COM SUCESSO ===');
  }
}

const isMain = process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url;
if (isMain) {
  void main();
}
