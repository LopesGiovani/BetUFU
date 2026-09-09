#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
export const defaultProjectRoot = resolve(scriptsDirectory, '..');

export function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

export function replaceUniqueRegex(source, pattern, replacement, rule) {
  const globalPattern = new RegExp(pattern.source, `${pattern.flags.replace('g', '')}g`);
  const matches = [...source.matchAll(globalPattern)];
  if (matches.length !== 1) {
    throw new Error(`[${rule}] expected one anchor, found ${matches.length}`);
  }
  return source.replace(pattern, replacement);
}

export function mutateIncludeNumericWalkover(source) {
  return replaceUniqueRegex(
    source,
    /    if \(!result\.resultKind\(\)\.feedsRating\(\)\) \{\r?\n      return false;\r?\n    \}\r?\n    if \(result\.resultKind\(\) != SportsResultKindForOdds\.SPORTING_RESULT\) \{\r?\n      return false;\r?\n    \}\r?\n    if \(!result\.resultKind\(\)\.hasSportsScore\(\)\) \{\r?\n      return false;\r?\n    \}/u,
    '    if (result.resultKind() == SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER) {\n      return true;\n    }\n    if (result.resultKind() != SportsResultKindForOdds.SPORTING_RESULT) {\n      return false;\n    }',
    'M_WO_NUMERIC',
  );
}

export function mutateInsertBettingVolume(source) {
  return replaceUniqueRegex(
    source,
    /        \+ ",\s*\\"confidenceBand\\":\\""\r?\n        \+ confidence\.band\(\)/u,
    '        + "\\",\\"confidenceBand\\":\\""\n        + confidence.band()\n        + "\\",\\"volume\\":100000"',
    'M_VOLUME',
  );
}

export function mutateAlterKFactorThreshold(source) {
  return replaceUniqueRegex(
    source,
    /  public static final BigDecimal PROVISIONAL_K = new BigDecimal\("32"\);/u,
    '  public static final BigDecimal PROVISIONAL_K = new BigDecimal("16");',
    'M_K_FACTOR',
  );
}

export function mutateAlterEditionRegression(source) {
  return replaceUniqueRegex(
    source,
    /  public static final BigDecimal EDITION_REGRESSION = new BigDecimal\("0\.5"\);/u,
    '  public static final BigDecimal EDITION_REGRESSION = new BigDecimal("0.0");',
    'M_REGRESSION',
  );
}

export function mutateAlterMarginRounding(source) {
  return replaceUniqueRegex(
    source,
    /  public static final int DECIMAL_PLACES = 2;/u,
    '  public static final int DECIMAL_PLACES = 4;',
    'M_ROUNDING',
  );
}

export function mutateRemovePersistedScope(source) {
  return replaceUniqueRegex(
    source,
    /    if \(persistedCompetitionId == null\) \{\r?\n      return AuthorizationDecision\.denied\("PERSISTED_SCOPE_MISSING"\);\r?\n    \}/u,
    '    if (false) {\n      return AuthorizationDecision.denied("PERSISTED_SCOPE_MISSING");\n    }',
    'M_SCOPE',
  );
}

export function mutateAllowSelfApproval(source) {
  return replaceUniqueRegex(
    source,
    /    if \(approverId\.equals\(proposal\.proposerId\(\)\)\) \{/u,
    '    if (false && approverId.equals(proposal.proposerId())) {',
    'M_SELF_APPROVAL',
  );
}

export function mutateBypassSuspendedState(source) {
  return replaceUniqueRegex(
    source,
    /(  public void requireProposalAllowed\([\s\S]*?)    if \(state != MarketState\.SUSPENDED\) \{/u,
    '$1    if (false && state != MarketState.SUSPENDED) {',
    'M_FINAL_STATE',
  );
}

export function mutateRemoveImmutabilityTrigger(source) {
  return replaceUniqueRegex(
    source,
    /CREATE OR REPLACE FUNCTION public\.prevent_odds_history_modification\(\)/u,
    'CREATE OR REPLACE FUNCTION public.allow_odds_history_modification()',
    'M_IMMUTABILITY',
  );
}

export function mutateExposePublicActor(source) {
  return replaceUniqueRegex(
    source,
    /      PublicOddsExplanation explanation\) \{/u,
    '      PublicOddsExplanation explanation,\n      UUID actorId) {',
    'M_PUBLIC_ALLOWLIST',
  );
}

export function mutateGeneratedClient(source) {
  return replaceUniqueRegex(
    source,
    /    explanation: PublicOddsExplanation;/u,
    '    explanation: PublicOddsExplanation;\n    actorId?: string;',
    'M_GENERATED_CLIENT',
  );
}

export const MUTATIONS = Object.freeze([
  Object.freeze({
    id: 'M_WO_NUMERIC',
    name: 'inclusao_wo_numerico',
    description: 'Inclusao indevida de W.O. administrativo no filtro de resultados esportivos',
    threatId: 'T-04-TAXONOMY',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/model/OddsInputFilter.java',
    mutate: mutateIncludeNumericWalkover,
    command:
      'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsResultTaxonomyTest test',
    fallbackCommand:
      'mvnw.cmd -f backend/pom.xml -Dtest=OddsResultTaxonomyTest test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_VOLUME',
    name: 'insercao_volume_apostas',
    description: 'Insercao de campo de volume de apostas no snapshot do kernel de pricing',
    threatId: 'T-04-VOLUME',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/model/OddsPricingKernel.java',
    mutate: mutateInsertBettingVolume,
    command:
      'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsPricingVolumeIndependenceTest test',
    fallbackCommand:
      'mvnw.cmd -f backend/pom.xml -Dtest=OddsPricingVolumeIndependenceTest test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_K_FACTOR',
    name: 'alteracao_limiar_k',
    description: 'Alteracao do fator K provisorio de 32 para 16 no motor Elo',
    threatId: 'T-04-CALIBRATION',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/model/EloRatingEngine.java',
    mutate: mutateAlterKFactorThreshold,
    command:
      'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsPricingVolumeIndependenceTest test',
    fallbackCommand:
      'mvnw.cmd -f backend/pom.xml -Dtest=OddsPricingVolumeIndependenceTest test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_REGRESSION',
    name: 'alteracao_regressao_edicao',
    description: 'Alteracao da taxa de regressao inter-edicao de 0.5 para 0.0 no motor Elo',
    threatId: 'T-04-CALIBRATION',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/model/EloRatingEngine.java',
    mutate: mutateAlterEditionRegression,
    command:
      'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsReplayCorrectionTest test',
    fallbackCommand:
      'mvnw.cmd -f backend/pom.xml -Dtest=OddsReplayCorrectionTest test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_ROUNDING',
    name: 'alteracao_arredondamento_margem',
    description: 'Alteracao do numero de casas decimais da margem de 2 para 4 casas',
    threatId: 'T-04-CALIBRATION',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/model/MarginCalculator.java',
    mutate: mutateAlterMarginRounding,
    command:
      'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsPricingVolumeIndependenceTest test',
    fallbackCommand:
      'mvnw.cmd -f backend/pom.xml -Dtest=OddsPricingVolumeIndependenceTest test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_SCOPE',
    name: 'remocao_escopo_persistido',
    description: 'Remocao da rejeicao de comandos sem escopo persistido',
    threatId: 'T-04-SCOPE',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/governance/OddsAuthorizationService.java',
    mutate: mutateRemovePersistedScope,
    command: 'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsAuthorizationIT test',
    fallbackCommand: 'mvnw.cmd -f backend/pom.xml -Dtest=OddsAuthorizationIT test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_SELF_APPROVAL',
    name: 'permissao_autoaprovacao',
    description: 'Permissao indevida de o proponente aprovar o proprio override',
    threatId: 'T-04-SELF-APPROVAL',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/override/OddsOverridePolicy.java',
    mutate: mutateAllowSelfApproval,
    command: 'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsOverrideIT test',
    fallbackCommand: 'mvnw.cmd -f backend/pom.xml -Dtest=OddsOverrideIT test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_FINAL_STATE',
    name: 'bypass_estado_suspenso',
    description: 'Permissao de proposta fora do estado SUSPENDED, incluindo estados finais',
    threatId: 'T-04-RACE',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/override/OddsOverridePolicy.java',
    mutate: mutateBypassSuspendedState,
    command: 'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsOverrideIT test',
    fallbackCommand: 'mvnw.cmd -f backend/pom.xml -Dtest=OddsOverrideIT test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_IMMUTABILITY',
    name: 'remocao_trigger_imutabilidade',
    description: 'Remocao da funcao comum que bloqueia UPDATE e DELETE no historico de odds',
    threatId: 'T-04-REWRITE',
    path: 'backend/src/main/resources/db/migration/V25__create_odds_market_lineage.sql',
    mutate: mutateRemoveImmutabilityTrigger,
    command: 'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsPersistenceIT test',
    fallbackCommand: 'mvnw.cmd -f backend/pom.xml -Dtest=OddsPersistenceIT test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_PUBLIC_ALLOWLIST',
    name: 'exposicao_actor_publico',
    description: 'Exposicao indevida de actorId na projecao publica de odds',
    threatId: 'T-04-PII',
    path: 'backend/src/main/java/br/com/betufu/odds/internal/publicview/OddsPublicViewModels.java',
    mutate: mutateExposePublicActor,
    command: 'node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsPublicPrivacyIT test',
    fallbackCommand: 'mvnw.cmd -f backend/pom.xml -Dtest=OddsPublicPrivacyIT test',
    timeoutMs: 120_000,
    expectedOutput: /BUILD FAILURE/u,
  }),
  Object.freeze({
    id: 'M_GENERATED_CLIENT',
    name: 'edicao_manual_cliente_gerado',
    description: 'Edicao manual de um modelo TypeScript governado',
    threatId: 'T-04-SUPPLY-CHAIN',
    path: 'frontend/src/app/api/generated/model/public-odds-view.ts',
    mutate: mutateGeneratedClient,
    command: 'node scripts/validate-angular-route-boundary.mjs',
    timeoutMs: 120_000,
    expectedOutput: /GENERATED_MANIFEST_DIGEST/u,
  }),
]);

export function runCommand(command, cwd, timeoutMs) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, {
      cwd,
      shell: true,
      windowsHide: true,
    });
    let stdout = '';
    let stderr = '';
    let timedOut = false;
    let settled = false;
    const timer = setTimeout(() => {
      timedOut = true;
      child.kill();
    }, timeoutMs);

    child.stdout?.on('data', (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr?.on('data', (chunk) => {
      stderr += chunk.toString();
    });
    child.once('error', (error) => {
      clearTimeout(timer);
      if (!settled) {
        settled = true;
        reject(error);
      }
    });
    child.once('close', (code, signal) => {
      clearTimeout(timer);
      if (!settled) {
        settled = true;
        resolvePromise({ code, signal, stdout, stderr, timedOut });
      }
    });
  });
}

function combinedOutput(result) {
  return `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
}

export async function runMutationCase(projectRoot, mutation, options = {}) {
  const runner = options.runner ?? runCommand;
  const filePath = resolve(projectRoot, mutation.path);
  const originalBytes = await readFile(filePath);
  const beforeHash = sha256(originalBytes);
  let mutationStarted = false;

  try {
    const mutatedSource = mutation.mutate(originalBytes.toString('utf8'));
    if (mutatedSource === originalBytes.toString('utf8')) {
      throw new Error(`[${mutation.id}] mutation did not change source`);
    }
    mutationStarted = true;
    await writeFile(filePath, mutatedSource, 'utf8');

    let result = await runner(mutation.command, projectRoot, mutation.timeoutMs);
    if (
      mutation.fallbackCommand &&
      result?.code === 2 &&
      /argumento Maven recusado/u.test(combinedOutput(result))
    ) {
      result = await runner(mutation.fallbackCommand, projectRoot, mutation.timeoutMs);
    }
    if (result?.timedOut) {
      throw new Error(`[${mutation.id}] MUTATION_TIMEOUT after ${mutation.timeoutMs}ms`);
    }
    if (result?.code === 0) {
      throw new Error(`[${mutation.id}] MUTATION_GREEN: mutated contract stayed green`);
    }
    if (!mutation.expectedOutput.test(combinedOutput(result))) {
      throw new Error(`[${mutation.id}] MUTATION_SIGNATURE: expected discriminant was not observed`);
    }

    return {
      id: mutation.id,
      name: mutation.name ?? mutation.id,
      threatId: mutation.threatId ?? null,
      status: 'expected-red',
      exitCode: result.code,
      beforeHash,
      afterHash: beforeHash,
    };
  } finally {
    if (mutationStarted) {
      await writeFile(filePath, originalBytes);
    }
    const restoredBytes = await readFile(filePath);
    const afterHash = sha256(restoredBytes);
    if (afterHash !== beforeHash) {
      throw new Error(`[${mutation.id}] MUTATION_RESTORE: before=${beforeHash} after=${afterHash}`);
    }
  }
}

export async function runMutationSuite(projectRoot, mutations = MUTATIONS, options = {}) {
  if (!mutations || mutations.length === 0) {
    throw new Error('Nenhuma mutacao definida no harness.');
  }

  const results = [];
  let failures = 0;
  for (const mutation of mutations) {
    try {
      const evidence = await runMutationCase(projectRoot, mutation, options);
      results.push(evidence);
      if (options.logger) {
        options.logger(`${mutation.id} (${mutation.name ?? 'cenario'}): expected red; SHA-256 unchanged (${evidence.beforeHash})`);
      }
    } catch (error) {
      failures += 1;
      const message = error instanceof Error ? error.message : String(error);
      if (options.logger) {
        options.logger(`FAIL ${mutation.id}: ${message}`);
      }
      results.push({
        id: mutation.id,
        name: mutation.name ?? mutation.id,
        status: 'failed',
        error: message,
      });
    }
  }

  if (results.length === 0 || results.every((r) => r.status !== 'expected-red')) {
    throw new Error('Nenhum sinal vermelho valido foi executado pelo harness.');
  }

  if (failures > 0) {
    throw new Error(`Harness falhou com ${failures} falha(s) em ${mutations.length} mutacao(oes).`);
  }

  return results;
}

async function main() {
  try {
    const results = await runMutationSuite(defaultProjectRoot, MUTATIONS, {
      logger: (msg) => console.log(msg),
    });
    console.log(`\nSucesso: todas as ${results.length} mutacoes foram eliminadas com sinal vermelho discriminante.`);
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}

const invokedAsScript = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedAsScript) {
  await main();
}
