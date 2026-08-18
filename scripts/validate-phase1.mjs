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
import { fileURLToPath } from 'node:url';
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

// ---------------------------------------------------------------------------
// Parsing de argumentos
// ---------------------------------------------------------------------------

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
  // { flag: '--session-ui', fn: modeSessionUi },
  // { flag: '--diagnostic-ui', fn: modeDiagnosticUi },
];

// ---------------------------------------------------------------------------
// Execução fail-fast
// ---------------------------------------------------------------------------

let failed = false;

// P2-3: validar flags ANTES de executar qualquer modo.
// Isso evita rodar --infra (até 10 min, sobe Docker) para só depois
// reportar que --typo é inválida.
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
