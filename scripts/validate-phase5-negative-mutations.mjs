#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
export const defaultProjectRoot = resolve(scriptsDirectory, '..');

export function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

export function replaceUnique(source, anchor, replacement, rule) {
  const occurrences = source.split(anchor).length - 1;
  if (occurrences !== 1) {
    throw new Error(`[${rule}] expected one anchor, found ${occurrences}`);
  }
  return source.replace(anchor, replacement);
}

export function mutateAllowNegativeBalance(source) {
  return replaceUnique(
    source,
    'CONSTRAINT ck_credit_account_balance_non_negative CHECK (balance >= 0.00)',
    'CONSTRAINT ck_credit_account_balance_non_negative CHECK (balance >= -1000.00)',
    'M_NEGATIVE_BALANCE',
  );
}

export function mutateAllowLedgerDelete(source) {
  return replaceUnique(
    source,
    'BEFORE UPDATE OR DELETE ON public.credit_ledger_entry',
    'BEFORE UPDATE ON public.credit_ledger_entry',
    'M_LEDGER_DELETE',
  );
}

export function mutateAllowSelectionDelete(source) {
  return replaceUnique(
    source,
    'BEFORE UPDATE OR DELETE ON public.prediction_selection',
    'BEFORE UPDATE ON public.prediction_selection',
    'M_SELECTION_DELETE',
  );
}

export function mutateGrantInactiveEnrollment(source) {
  const anchor = "WHERE enrollment.status = 'ATIVO'";
  const occurrences = source.split(anchor).length - 1;
  if (occurrences !== 2) {
    throw new Error(`[M_GRANT_FILTER] expected two anchors, found ${occurrences}`);
  }
  return source.replaceAll(anchor, "WHERE enrollment.status = 'LIBERADO'");
}

export function mutateAcceptDivergentReceipt(source) {
  return replaceUnique(
    source,
    'if (!existing.get().payloadDigest().equals(digest)) {',
    'if (false && !existing.get().payloadDigest().equals(digest)) {',
    'M_DIVERGENT_RECEIPT',
  );
}

export function mutateBreakLedgerSourceLink(source) {
  return replaceUnique(
    source,
    'ledger.debit(identityId, prepared.stake(), predictionId);',
    'ledger.debit(identityId, prepared.stake(), UUID.randomUUID());',
    'M_SOURCE_LINK',
  );
}

export function mutateDropCompetitionScope(source) {
  return replaceUnique(
    source,
    'eligibility.isEligible(identityId, snapshot.competitionId())',
    'eligibility.isEligible(identityId, null)',
    'M_COMPETITION_SCOPE',
  );
}

const CERTIFICATION_COMMAND =
  'node scripts/maven.mjs -f backend/pom.xml -Dtest=Phase5CertificationIT clean test';
const CERTIFICATION_REPORT =
  'backend/target/surefire-reports/TEST-br.com.betufu.phase5.Phase5CertificationIT.xml';
const CERTIFICATION_TIMEOUT_MS = 120_000;

export const MUTATIONS = Object.freeze([
  Object.freeze({
    id: 'M_NEGATIVE_BALANCE',
    threatId: 'T-05-BALANCE-RACE',
    path: 'backend/src/main/resources/db/migration/V29__create_virtual_credit_ledger.sql',
    mutate: mutateAllowNegativeBalance,
    command: CERTIFICATION_COMMAND,
    expectedOutput: /BUILD FAILURE/u,
    reportPath: CERTIFICATION_REPORT,
    expectedReport: /negative balance must remain blocked/u,
    timeoutMs: CERTIFICATION_TIMEOUT_MS,
  }),
  Object.freeze({
    id: 'M_LEDGER_DELETE',
    threatId: 'T-05-CERT-GAP',
    path: 'backend/src/main/resources/db/migration/V29__create_virtual_credit_ledger.sql',
    mutate: mutateAllowLedgerDelete,
    command: CERTIFICATION_COMMAND,
    expectedOutput: /BUILD FAILURE/u,
    reportPath: CERTIFICATION_REPORT,
    expectedReport: /ledger DELETE must remain blocked/u,
    timeoutMs: CERTIFICATION_TIMEOUT_MS,
  }),
  Object.freeze({
    id: 'M_SELECTION_DELETE',
    threatId: 'T-05-CERT-GAP',
    path: 'backend/src/main/resources/db/migration/V30__create_prediction_records.sql',
    mutate: mutateAllowSelectionDelete,
    command: CERTIFICATION_COMMAND,
    expectedOutput: /BUILD FAILURE/u,
    reportPath: CERTIFICATION_REPORT,
    expectedReport: /prediction selection DELETE must remain blocked/u,
    timeoutMs: CERTIFICATION_TIMEOUT_MS,
  }),
  Object.freeze({
    id: 'M_GRANT_FILTER',
    threatId: 'T-05-LEGACY-NO-GRANT',
    path: 'backend/src/main/resources/db/migration/V32__backfill_eligible_virtual_credit_grants.sql',
    mutate: mutateGrantInactiveEnrollment,
    command: CERTIFICATION_COMMAND,
    expectedOutput: /BUILD FAILURE/u,
    reportPath: CERTIFICATION_REPORT,
    expectedReport: /active enrollment must receive initial grant/u,
    timeoutMs: CERTIFICATION_TIMEOUT_MS,
  }),
  Object.freeze({
    id: 'M_DIVERGENT_RECEIPT',
    threatId: 'T-05-IDEMP-DIVERGENT',
    path: 'backend/src/main/java/br/com/betufu/betting/internal/PredictionService.java',
    mutate: mutateAcceptDivergentReceipt,
    command: CERTIFICATION_COMMAND,
    expectedOutput: /BUILD FAILURE/u,
    reportPath: CERTIFICATION_REPORT,
    expectedReport: /divergent idempotency payload must fail/u,
    timeoutMs: CERTIFICATION_TIMEOUT_MS,
  }),
  Object.freeze({
    id: 'M_SOURCE_LINK',
    threatId: 'T-05-PARTIAL-DEBIT',
    path: 'backend/src/main/java/br/com/betufu/betting/internal/PredictionService.java',
    mutate: mutateBreakLedgerSourceLink,
    command: CERTIFICATION_COMMAND,
    expectedOutput: /BUILD FAILURE/u,
    reportPath: CERTIFICATION_REPORT,
    expectedReport: /debit source_id must equal predictionId/u,
    timeoutMs: CERTIFICATION_TIMEOUT_MS,
  }),
  Object.freeze({
    id: 'M_COMPETITION_SCOPE',
    threatId: 'T-05-SCOPE-BYPASS',
    path: 'backend/src/main/java/br/com/betufu/betting/internal/PredictionService.java',
    mutate: mutateDropCompetitionScope,
    command: CERTIFICATION_COMMAND,
    expectedOutput: /BUILD FAILURE/u,
    reportPath: CERTIFICATION_REPORT,
    expectedReport: /competition scope must reach eligibility policy/u,
    timeoutMs: CERTIFICATION_TIMEOUT_MS,
  }),
]);

export function runCommand(command, cwd, timeoutMs) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, {
      cwd,
      shell: true,
      windowsHide: true,
      detached: process.platform !== 'win32',
    });
    let stdout = '';
    let stderr = '';
    let timedOut = false;
    let settled = false;
    let termination = Promise.resolve();
    const timer = setTimeout(() => {
      timedOut = true;
      termination = terminateProcessTree(child);
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
    child.once('close', async (code, signal) => {
      clearTimeout(timer);
      try {
        await termination;
        if (!settled) {
          settled = true;
          resolvePromise({ code, signal, stdout, stderr, timedOut });
        }
      } catch (error) {
        if (!settled) {
          settled = true;
          reject(error);
        }
      }
    });
  });
}

function terminateProcessTree(child) {
  if (!child.pid) {
    return Promise.reject(new Error('COMMAND_PROCESS_ID_UNAVAILABLE'));
  }
  if (process.platform === 'win32') {
    return new Promise((resolvePromise, reject) => {
      const killer = spawn(
        'taskkill',
        ['/PID', String(child.pid), '/T', '/F'],
        { windowsHide: true, stdio: 'ignore' },
      );
      killer.once('error', reject);
      killer.once('close', (code) => {
        if (code === 0) {
          resolvePromise();
        } else {
          reject(new Error(`COMMAND_TREE_TERMINATION_FAILED:${code}`));
        }
      });
    });
  }
  try {
    process.kill(-child.pid, 'SIGKILL');
    return Promise.resolve();
  } catch (error) {
    return Promise.reject(error);
  }
}

export async function runMutationCase(projectRoot, mutation, runner = runCommand) {
  const path = resolve(projectRoot, mutation.path);
  const original = await readFile(path);
  const beforeHash = sha256(original);
  let mutated = false;
  try {
    const changed = mutation.mutate(original.toString('utf8'));
    if (changed === original.toString('utf8')) {
      throw new Error(`[${mutation.id}] mutation did not change source`);
    }
    await writeFile(path, changed, 'utf8');
    mutated = true;
    const reportPath = mutation.reportPath
      ? resolve(projectRoot, mutation.reportPath)
      : null;
    if (reportPath) {
      await rm(reportPath, { force: true });
    }
    const result = await runner(mutation.command, projectRoot, mutation.timeoutMs);
    const output = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
    if (result.timedOut) {
      throw new Error(`[${mutation.id}] MUTATION_TIMEOUT after ${mutation.timeoutMs}ms`);
    }
    if (result.code === 0) {
      throw new Error(`[${mutation.id}] MUTATION_GREEN`);
    }
    if (!mutation.expectedOutput.test(output)) {
      throw new Error(`[${mutation.id}] MUTATION_SIGNATURE`);
    }
    if (reportPath) {
      let report;
      try {
        report = await readFile(reportPath, 'utf8');
      } catch {
        throw new Error(`[${mutation.id}] MUTATION_REPORT_MISSING`);
      }
      if (!mutation.expectedReport?.test(report)) {
        throw new Error(`[${mutation.id}] MUTATION_REPORT_SIGNATURE`);
      }
    }
    return { id: mutation.id, status: 'expected-red', beforeHash };
  } finally {
    if (mutated) {
      await writeFile(path, original);
    }
    const afterHash = sha256(await readFile(path));
    if (afterHash !== beforeHash) {
      throw new Error(`[${mutation.id}] MUTATION_RESTORE`);
    }
  }
}

export async function runMutationSuite(projectRoot, mutations = MUTATIONS, options = {}) {
  if (mutations.length === 0) {
    throw new Error('Nenhuma mutacao definida no harness.');
  }
  const results = [];
  for (const mutation of mutations) {
    const result = await runMutationCase(projectRoot, mutation, options.runner);
    results.push(result);
    options.logger?.(`${mutation.id}: expected red; SHA-256 restored ${result.beforeHash}`);
  }
  return results;
}

export async function certifyRestoredBuild(projectRoot, runner = runCommand) {
  const result = await runner(
    CERTIFICATION_COMMAND,
    projectRoot,
    CERTIFICATION_TIMEOUT_MS,
  );
  const output = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
  if (result.code !== 0 || !/BUILD SUCCESS/u.test(output)) {
    throw new Error('RESTORED_BUILD_FAILED');
  }
  return { status: 'restored-green' };
}

export async function runCertifiedMutationSuite(
  projectRoot,
  mutations = MUTATIONS,
  options = {},
) {
  let results;
  let mutationError;
  try {
    results = await runMutationSuite(projectRoot, mutations, {
      logger: options.logger,
      runner: options.runner,
    });
  } catch (error) {
    mutationError = error;
  }

  let restorationError;
  try {
    await certifyRestoredBuild(
      projectRoot,
      options.restoreRunner ?? options.runner,
    );
  } catch (error) {
    restorationError = error;
  }

  if (mutationError && restorationError) {
    throw new AggregateError(
      [mutationError, restorationError],
      'MUTATION_AND_RESTORE_FAILED',
    );
  }
  if (mutationError) {
    throw mutationError;
  }
  if (restorationError) {
    throw restorationError;
  }
  return results;
}

async function main() {
  try {
    const results = await runCertifiedMutationSuite(defaultProjectRoot, MUTATIONS, {
      logger: (message) => console.log(message),
    });
    console.log('Restored source and bytecode certification: green.');
    console.log(`Sucesso: ${results.length} mutacoes discriminantes eliminadas.`);
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main();
}
