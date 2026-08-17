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
 *   --contract   (01-26) — drift OpenAPI
 *   --session-ui (01-27) — store/layout autenticado
 *   --diagnostic-ui (01-28) — rotas E2E e ausência production
 *   (sem argumento = 01-16 gate integral)
 */

import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runCaptured } from './lib/compose.mjs';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const devScript = resolve(scriptsDirectory, 'dev.mjs');

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
  // { flag: '--contract', fn: modeContract },
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
