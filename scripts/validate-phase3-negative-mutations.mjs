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

function replaceUniqueRegex(source, pattern, replacement, rule) {
  const globalPattern = new RegExp(pattern.source, `${pattern.flags.replace('g', '')}g`);
  const matches = [...source.matchAll(globalPattern)];
  if (matches.length !== 1) {
    throw new Error(`[${rule}] expected one anchor, found ${matches.length}`);
  }
  return source.replace(pattern, replacement);
}

function mutateHeroComposition(source) {
  return replaceUniqueRegex(
    source,
    /  isCompositionInteractive\(index: number\): boolean \{\r?\n    return this\.activeIndex\(\) === index;\r?\n  \}/u,
    '  isCompositionInteractive(index: number): boolean {\n    return true;\n  }',
    'M1',
  );
}

function mutateScopeMatch(source) {
  return replaceUniqueRegex(
    source,
    /\(requestedCompetitionId == null \|\| grant\.scopeRef\(\)\.equals\(requestedCompetitionId\)\)/u,
    'true',
    'M2',
  );
}

function mutatePublicAthleteSchema(source) {
  const schema = replaceUniqueRegex(
    source,
    /^    PublicAthleteDetail:\r?\n[\s\S]*?^    SportsAthleteCreateRequest:/mu,
    (block) =>
      replaceUniqueRegex(
        block,
        /^        slug:\r?\n          type: string\r?\n/mu,
        (slug) => `${slug}        matricula:\n          type: string\n`,
        'M3.slug',
      ),
    'M3.schema',
  );
  return schema;
}

export const MUTATIONS = Object.freeze([
  Object.freeze({
    id: 'M1',
    path: 'frontend/src/app/shared/sports/auto-event-hero.component.ts',
    mutate: mutateHeroComposition,
    command:
      'npm --prefix frontend run test -- --watch=false --include src/app/shared/sports/auto-event-hero.component.spec.ts',
    timeoutMs: 120_000,
    expectedOutput: /\[M1\] keeps inactive composition inert and outside tab order/u,
  }),
  Object.freeze({
    id: 'M2',
    path: 'backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java',
    mutate: mutateScopeMatch,
    command:
      'node scripts/maven.mjs -f backend/pom.xml verify -Dit.test=OperationalCapabilitiesIT#scopeA_doesNotAuthorizeScopeB',
    fallbackCommand:
      'mvnw.cmd -f backend/pom.xml verify -Dit.test=OperationalCapabilitiesIT#scopeA_doesNotAuthorizeScopeB',
    timeoutMs: 180_000,
    expectedOutput: /OperationalCapabilitiesIT\.scopeA_doesNotAuthorizeScopeB/u,
  }),
  Object.freeze({
    id: 'M3',
    path: 'backend/openapi/betufu.yaml',
    mutate: mutatePublicAthleteSchema,
    command: 'node frontend/scripts/generate-api.mjs --check',
    timeoutMs: 60_000,
    expectedOutput: /PUBLIC_SCHEMA_FORBIDDEN_FIELD: matricula/u,
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

async function main() {
  let failures = 0;
  for (const mutation of MUTATIONS) {
    try {
      const evidence = await runMutationCase(defaultProjectRoot, mutation);
      console.log(`${mutation.id}: expected red; SHA-256 unchanged (${evidence.beforeHash})`);
    } catch (error) {
      failures += 1;
      console.error(error instanceof Error ? error.message : String(error));
    }
  }
  if (failures > 0) {
    process.exitCode = 1;
  }
}

const invokedAsScript = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedAsScript) {
  await main();
}
