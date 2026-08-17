#!/usr/bin/env node

/**
 * scripts/lib/compose.mjs
 *
 * Funções comuns de orquestração Docker Compose para o projeto BETUFU.
 * Usado por scripts/dev.mjs e scripts/validate-phase1.mjs.
 *
 * Princípios:
 * - shell:false sempre — comandos são arrays, nunca strings concatenadas
 * - Lock por lockfile O_CREAT|O_EXCL com PID+nonce — impede composição parcial
 * - Sanitização de saída — valores de variáveis nunca aparecem em stderr/stdout
 * - Cross-platform — Windows (PowerShell/cmd) e Linux sem shell-ismo
 */

import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { open, readFile, unlink, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptsDirectory, '..', '..');
const infraDirectory = resolve(projectRoot, 'infra');
const envFilePath = resolve(infraDirectory, '.env.example');
const composeFilePath = resolve(infraDirectory, 'compose.yaml');
const lockFilePath = resolve(projectRoot, '.betufu-dev.lock');

/**
 * Retorna o path absoluto para infra/.env.example.
 */
export function resolveEnvFile() {
  return envFilePath;
}

/**
 * Retorna o path absoluto para infra/compose.yaml.
 */
export function resolveComposeFile() {
  return composeFilePath;
}

/**
 * Retorna o path raiz do projeto.
 */
export function resolveProjectRoot() {
  return projectRoot;
}

/**
 * Monta os argumentos comuns do docker compose para um profile.
 *
 * @param {string} profile - 'daily' ou 'full'
 * @returns {string[]} argumentos para spawn
 */
export function composeArgs(profile) {
  return [
    'compose',
    '--env-file', envFilePath,
    '-f', composeFilePath,
    '--project-name', 'infra',
    '--profile', profile,
  ];
}

/**
 * Monta os argumentos do docker compose SEM profile (para comandos
 * que operam sobre todos os serviços do projeto, como ps/logs).
 *
 * @returns {string[]} argumentos para spawn
 */
export function composeArgsNoProfile() {
  return [
    'compose',
    '--env-file', envFilePath,
    '-f', composeFilePath,
    '--project-name', 'infra',
  ];
}

// ---------------------------------------------------------------------------
// Execução de comandos
// ---------------------------------------------------------------------------

/**
 * Executa um comando capturando stdout e stderr.
 * Sempre usa shell:false e propaga exit codes.
 *
 * @param {string} command - executável
 * @param {string[]} args - argumentos (array, nunca string)
 * @param {object} [options] - cwd, timeoutMs
 * @returns {Promise<{exitCode: number, stdout: string, stderr: string}>}
 */
export function runCaptured(command, args, options = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, {
      cwd: options.cwd ?? projectRoot,
      shell: false,
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    const stdoutChunks = [];
    const stderrChunks = [];
    let settled = false;

    const finish = (fn, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      fn(value);
    };

    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      finish(rejectPromise, new Error('Comando excedeu o prazo.'));
    }, options.timeoutMs ?? 120_000);
    if (timer.unref) timer.unref();

    child.stdout.on('data', (chunk) => stdoutChunks.push(chunk));
    child.stderr.on('data', (chunk) => stderrChunks.push(chunk));

    child.once('error', (error) => {
      finish(rejectPromise, new Error(`Comando não iniciou: ${error.message}`));
    });

    child.once('close', (code) => {
      const stdout = Buffer.concat(stdoutChunks).toString('utf8').trim();
      const stderr = Buffer.concat(stderrChunks).toString('utf8').trim();
      finish(resolvePromise, { exitCode: code ?? 1, stdout, stderr });
    });
  });
}

/**
 * Executa um comando com stdio herdado (o usuário vê a saída direto).
 * Retorna o exit code.
 *
 * @param {string} command
 * @param {string[]} args
 * @param {object} [options]
 * @returns {Promise<number>} exit code
 */
export function runInherited(command, args, options = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, {
      cwd: options.cwd ?? projectRoot,
      shell: false,
      windowsHide: true,
      stdio: 'inherit',
    });

    child.once('error', (error) => {
      rejectPromise(new Error(`Comando não iniciou: ${error.message}`));
    });

    child.once('close', (code) => {
      resolvePromise(code ?? 1);
    });
  });
}

// ---------------------------------------------------------------------------
// Lock de concorrência
// ---------------------------------------------------------------------------

/**
 * Adquire um lock exclusivo por projeto usando O_CREAT|O_EXCL.
 *
 * Conceito: O_EXCL faz o sistema operacional falhar atomicamente se o arquivo
 * já existir. Isso impede que duas instâncias do CLI operem simultaneamente.
 * O arquivo contém PID + nonce para que um lock órfão possa ser identificado.
 *
 * @returns {Promise<{release: () => Promise<void>, nonce: string}>}
 * @throws {Error} se o lock já estiver adquirido
 */
export async function acquireLock() {
  const nonce = randomUUID();
  const content = JSON.stringify({
    pid: process.pid,
    nonce,
    timestamp: new Date().toISOString(),
  });

  let handle;
  try {
    // flag 'wx' é equivalente a O_WRONLY | O_CREAT | O_EXCL
    // falha atomicamente se o arquivo já existir
    handle = await open(lockFilePath, 'wx');
    await handle.writeFile(content, 'utf8');
    await handle.close();
    handle = undefined;
  } catch (error) {
    // Se o handle foi aberto mas a escrita falhou, fechar
    if (handle !== undefined) {
      try { await handle.close(); } catch { /* ignorar */ }
      handle = undefined;
    }

    if (error.code === 'EEXIST') {
      // Lock já existe — verificar se o processo dono ainda roda
      let lockData;
      try {
        lockData = JSON.parse(await readFile(lockFilePath, 'utf8'));
      } catch {
        // Lock corrompido — pode ser órfão
        throw new Error(
          `Lock já adquirido (${lockFilePath}). ` +
          'Se nenhuma outra instância está rodando, remova o arquivo manualmente.'
        );
      }

      // Verificar se o PID dono ainda existe
      let ownerAlive = false;
      try {
        // process.kill(pid, 0) não mata — só verifica se o processo existe
        process.kill(lockData.pid, 0);
        ownerAlive = true;
      } catch {
        ownerAlive = false;
      }

      if (ownerAlive) {
        throw new Error(
          `Outra instância está rodando (PID ${lockData.pid}, ` +
          `nonce ${lockData.nonce}, desde ${lockData.timestamp}). ` +
          'Aguarde ou encerre a outra instância.'
        );
      }

      // PID morto — lock órfão. Remover e tentar novamente UMA vez.
      try {
        await unlink(lockFilePath);
      } catch {
        throw new Error(
          `Lock órfão não pôde ser removido (${lockFilePath}). ` +
          'Remova manualmente.'
        );
      }

      // Tentar de novo (uma única vez, sem recursão)
      try {
        handle = await open(lockFilePath, 'wx');
        await handle.writeFile(content, 'utf8');
        await handle.close();
        handle = undefined;
      } catch (retryError) {
        if (handle !== undefined) {
          try { await handle.close(); } catch { /* ignorar */ }
          handle = undefined;
        }
        throw new Error(
          `Não foi possível adquirir o lock após remoção do órfão: ${retryError.message}`
        );
      }
    } else {
      throw error;
    }
  }

  const release = async () => {
    try {
      // Ler o lock e verificar que ainda é nosso antes de remover
      const currentContent = JSON.parse(await readFile(lockFilePath, 'utf8'));
      if (currentContent.nonce === nonce && currentContent.pid === process.pid) {
        await unlink(lockFilePath);
      }
    } catch {
      // Se o arquivo já não existe ou mudou, não é erro
    }
  };

  return { release, nonce };
}

// ---------------------------------------------------------------------------
// Sanitização de saída
// ---------------------------------------------------------------------------

/**
 * Redata valores de variáveis de ambiente na saída para evitar
 * vazamento de segredos.
 *
 * @param {string} text - texto a sanitizar
 * @param {Record<string, string>} envVars - variáveis com valores a redatar
 * @returns {string} texto sanitizado
 */
export function sanitizeOutput(text, envVars) {
  let result = text;
  for (const [key, value] of Object.entries(envVars)) {
    if (value && value.length > 0) {
      // Substituir todas as ocorrências do valor pela chave redatada
      // Escapar caracteres especiais de regex no valor
      const escaped = value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      result = result.replace(new RegExp(escaped, 'g'), `[REDACTED:${key}]`);
    }
  }
  return result;
}

const DAILY_SERVICES = new Set(['postgres', 'redis', 'keycloak', 'seaweedfs', 'mailpit']);
const FULL_SERVICES = new Set(['postgres', 'redis', 'keycloak', 'seaweedfs', 'mailpit', 'backend', 'frontend']);

/**
 * Espera até que todos os serviços do profile estejam healthy.
 *
 * @param {string} profile - 'daily' ou 'full'
 * @param {object} [options] - timeoutMs, signal
 * @returns {Promise<void>}
 */
export async function waitHealthy(profile, options = {}) {
  const allowed = profile === 'full' ? FULL_SERVICES : DAILY_SERVICES;
  const timeoutMs = options.timeoutMs ?? 300_000; // 5 minutos
  const deadline = Date.now() + timeoutMs;
  const pollIntervalMs = 3_000;

  while (Date.now() < deadline) {
    const result = await runCaptured(
      'docker',
      [...composeArgs(profile), 'ps', '--format', 'json'],
      { timeoutMs: 30_000 },
    );

    if (result.exitCode !== 0) {
      // Compose ainda não respondeu — esperar
      await delay(pollIntervalMs);
      continue;
    }

    const trimmed = result.stdout.trim();
    let services = [];
    if (trimmed.startsWith('[')) {
      try {
        services = JSON.parse(trimmed);
      } catch {
        services = [];
      }
    } else {
      for (const line of trimmed.split('\n').filter((l) => l.trim() !== '')) {
        try {
          services.push(JSON.parse(line));
        } catch {
          // ignorar linhas não-JSON
        }
      }
    }

    const profileServices = services.filter((s) => allowed.has(s.Service ?? s.Name));
    if (profileServices.length < allowed.size) {
      await delay(pollIntervalMs);
      continue;
    }

    let allHealthy = true;
    let anyExited = false;

    for (const service of profileServices) {
      const health = (service.Health ?? '').toLowerCase();
      const state = (service.State ?? '').toLowerCase();

      if (state === 'exited' || state === 'dead') {
        anyExited = true;
        allHealthy = false;
        break;
      }
      if (health !== 'healthy') {
        allHealthy = false;
      }
    }

    if (anyExited) {
      throw new Error(
        'Um ou mais serviços do profile encerraram inesperadamente. ' +
        'Use "node scripts/dev.mjs logs" para inspecionar.'
      );
    }

    if (allHealthy && profileServices.length === allowed.size) {
      return;
    }

    await delay(pollIntervalMs);
  }

  throw new Error(
    `Serviços do profile "${profile}" não ficaram healthy em ${Math.round(timeoutMs / 1000)}s. ` +
    'Use "node scripts/dev.mjs logs" para inspecionar.'
  );
}

/**
 * Pausa assíncrona.
 *
 * @param {number} ms
 * @returns {Promise<void>}
 */
export function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ---------------------------------------------------------------------------
// Variáveis de ambiente (parsing seguro, sem imprimir valores)
// ---------------------------------------------------------------------------

/**
 * Lê o .env.example e retorna as chaves obrigatórias.
 * Nunca imprime valores.
 *
 * @returns {Promise<string[]>} lista de nomes de variáveis
 */
export async function readEnvKeys() {
  const pairs = await readEnvPairs();
  return [...pairs.keys()];
}

/**
 * Lê o .env.example e retorna um Map de chave → valor.
 * Os valores são usados internamente para sanitização — nunca são impressos.
 *
 * @returns {Promise<Map<string, string>>} mapa de variáveis
 */
export async function readEnvPairs() {
  let content;
  try {
    content = await readFile(envFilePath, 'utf8');
  } catch {
    throw new Error(`Arquivo de ambiente ausente: ${envFilePath}`);
  }

  const pairs = new Map();
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed === '' || trimmed.startsWith('#')) continue;
    const eqIndex = trimmed.indexOf('=');
    if (eqIndex === -1) continue;
    const key = trimmed.substring(0, eqIndex);
    if (!/^[A-Z][A-Z0-9_]*$/u.test(key)) continue;
    const value = trimmed.substring(eqIndex + 1);
    pairs.set(key, value);
  }
  return pairs;
}
