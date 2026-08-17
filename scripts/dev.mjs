#!/usr/bin/env node

/**
 * scripts/dev.mjs
 *
 * CLI cross-platform para ambiente local BETUFU.
 *
 * Subcomandos:
 *   up <profile>      — sobe serviços e espera healthy
 *   verify <profile>  — sobe, testa idempotência, redirects, lock e volumes
 *   status            — mostra estado dos containers
 *   stop              — para containers SEM remover volumes
 *   logs [service]    — exibe logs dos serviços
 *
 * Profiles: daily | full
 *
 * Padrões:
 * - shell:false sempre (D-13)
 * - Exit code propagado
 * - Lock por projeto impede composição parcial e concorrência indevida
 * - Saída sanitizada — valores de variáveis nunca aparecem
 */

import { constants } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { mkdtemp, writeFile, readFile, unlink, open, rm } from 'node:fs/promises';
import {
  acquireLock,
  composeArgs,
  composeArgsNoProfile,
  delay,
  readEnvPairs,
  resolveProjectRoot,
  runCaptured,
  runInherited,
  sanitizeOutput,
  waitHealthy,
} from './lib/compose.mjs';

const VALID_PROFILES = new Set(['daily', 'full']);
const EXPECTED_DAILY_SERVICES = 5;
const EXPECTED_FULL_SERVICES = 7;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function usage() {
  console.error(`
Uso: node scripts/dev.mjs <comando> [opções]

Comandos:
  up <profile>      Sobe serviços e espera healthy (daily | full)
  verify <profile>  Sobe, verifica idempotência, redirects, lock e volumes
  status            Mostra estado dos containers
  stop              Para containers sem remover volumes
  logs [service]    Exibe logs (opcionalmente de um serviço)

Exemplos:
  node scripts/dev.mjs up daily
  node scripts/dev.mjs verify daily
  node scripts/dev.mjs verify full
  node scripts/dev.mjs stop
  node scripts/dev.mjs logs postgres
`.trim());
}

function fail(message) {
  console.error(`[dev] ERRO: ${message}`);
  process.exitCode = 1;
}

function info(message) {
  console.log(`[dev] ${message}`);
}

function validateProfile(profile) {
  if (!profile) {
    fail('Profile obrigatório. Use: daily ou full');
    usage();
    return false;
  }
  if (!VALID_PROFILES.has(profile)) {
    fail(`Profile inválido: ${profile}. Use: daily ou full`);
    return false;
  }
  return true;
}

// ---------------------------------------------------------------------------
// Subcomando: up
// ---------------------------------------------------------------------------

async function commandUp(profile) {
  if (!validateProfile(profile)) return;

  const lock = await acquireLock();
  info(`Lock adquirido (nonce: ${lock.nonce}).`);

  try {
    info(`Subindo serviços com profile "${profile}"...`);
    const args = [...composeArgs(profile), 'up', '-d', '--wait'];
    if (profile === 'full') {
      args.splice(args.indexOf('up') + 1, 0, '--build');
    }
    const exitCode = await runInherited('docker', args);

    if (exitCode !== 0) {
      fail(`docker compose up falhou com exit code ${exitCode}.`);
      return;
    }

    info('Aguardando healthchecks...');
    await waitHealthy(profile, { timeoutMs: profile === 'full' ? 360_000 : 300_000 });
    info('Todos os serviços estão healthy.');
  } finally {
    await lock.release();
    info('Lock liberado.');
  }
}

// ---------------------------------------------------------------------------
// Subcomando: verify
// ---------------------------------------------------------------------------

async function commandVerify(profile) {
  if (!validateProfile(profile)) return;

  if (profile === 'daily') {
    await commandVerifyDaily();
  } else if (profile === 'full') {
    await commandVerifyFull();
  }
}

async function commandVerifyDaily() {
  info('=== Verificação do profile "daily" ===');
  info('');

  // Lock global para cobrir todo o ciclo de verificação sem janelas TOCTOU
  const verifyLock = await acquireLock();
  info(`Lock de verificação global adquirido (nonce: ${verifyLock.nonce}).`);

  try {
    // 1. Subir serviços
    info('Etapa 1/6: Subindo serviços...');
    const upExit = await runInherited('docker', [...composeArgs('daily'), 'up', '-d', '--wait']);
    if (upExit !== 0) {
      fail(`docker compose up falhou com exit code ${upExit}.`);
      return;
    }
    await waitHealthy('daily');
    info('Todos os 5 serviços estão healthy.');

    // 2. Teste de idempotência
    info('');
    info('Etapa 2/6: Verificando idempotência (segundo up)...');
    const beforeResult = await runCaptured(
      'docker',
      [...composeArgs('daily'), 'ps', '--format', 'json'],
    );
    if (beforeResult.exitCode !== 0) {
      fail('Não foi possível inspecionar containers antes do segundo up.');
      return;
    }
    const beforeIds = parseContainerIds(beforeResult.stdout);
    if (beforeIds.size !== EXPECTED_DAILY_SERVICES) {
      fail(`Esperado ${EXPECTED_DAILY_SERVICES} serviços no profile daily, mas encontrado ${beforeIds.size}.`);
      return;
    }

    const up2Exit = await runInherited('docker', [...composeArgs('daily'), 'up', '-d', '--wait']);
    if (up2Exit !== 0) {
      fail(`Segundo up falhou com exit code ${up2Exit}.`);
      return;
    }
    await waitHealthy('daily');

    const afterResult = await runCaptured(
      'docker',
      [...composeArgs('daily'), 'ps', '--format', 'json'],
    );
    if (afterResult.exitCode !== 0) {
      fail('Não foi possível inspecionar containers após segundo up.');
      return;
    }
    const afterIds = parseContainerIds(afterResult.stdout);

    if (!sameEntries(beforeIds, afterIds)) {
      fail(
        'Idempotência falhou: containers foram recriados no segundo up. ' +
        `Antes: ${JSON.stringify([...beforeIds])}, Depois: ${JSON.stringify([...afterIds])}`
      );
      return;
    }
    info('PASS: Containers idênticos após segundo up (idempotência de 5 serviços).');

    // 3. Teste de sobrevivência de volumes — marcador PostgreSQL
    info('');
    info('Etapa 3/6: Verificando sobrevivência de volumes (down + up)...');
    const marker = `betufu_test_${Date.now()}`;
    if (!/^[a-z0-9_]+$/.test(marker)) {
      fail('Marcador de teste contém caracteres inesperados — abortando.');
      return;
    }

    let pgContainer = await findServiceContainer('postgres', 'daily');
    if (!pgContainer) {
      fail('Container PostgreSQL não encontrado para teste de volumes.');
      return;
    }

    const envPairs = await readEnvPairs();
    const envObj = Object.fromEntries(envPairs);

    const writeResult = await runCaptured(
      'docker',
      [
        'exec', pgContainer,
        'psql', '-U', 'postgres', '-d', 'postgres',
        '-c', `CREATE TABLE IF NOT EXISTS _betufu_volume_test (marker TEXT); INSERT INTO _betufu_volume_test (marker) VALUES ('${marker}');`,
      ],
      { timeoutMs: 30_000 },
    );
    if (writeResult.exitCode !== 0) {
      fail(`Escrita do marcador falhou: ${sanitizeOutput(writeResult.stderr, envObj)}`);
      return;
    }

    info('Destruindo containers com compose down (volumes preservados)...');
    const downExit = await runInherited('docker', [...composeArgs('daily'), 'down']);
    if (downExit !== 0) {
      fail(`Compose down falhou com exit code ${downExit}.`);
      return;
    }
    await delay(2_000);

    info('Recriando containers...');
    const up3Exit = await runInherited('docker', [...composeArgs('daily'), 'up', '-d', '--wait']);
    if (up3Exit !== 0) {
      fail(`Compose up após down falhou com exit code ${up3Exit}.`);
      return;
    }
    await waitHealthy('daily');

    pgContainer = await findServiceContainer('postgres', 'daily');
    if (!pgContainer) {
      fail('Container PostgreSQL não encontrado após recriação.');
      return;
    }

    const readResult = await runCaptured(
      'docker',
      [
        'exec', pgContainer,
        'psql', '-U', 'postgres', '-d', 'postgres',
        '-tAc', `SELECT marker FROM _betufu_volume_test WHERE marker = '${marker}';`,
      ],
      { timeoutMs: 30_000 },
    );

    await runCaptured(
      'docker',
      [
        'exec', pgContainer,
        'psql', '-U', 'postgres', '-d', 'postgres',
        '-c', 'DROP TABLE IF EXISTS _betufu_volume_test;',
      ],
      { timeoutMs: 10_000 },
    );

    if (readResult.exitCode !== 0 || readResult.stdout.trim() !== marker) {
      fail(
        `Sobrevivência de volume falhou: marcador "${marker}" não encontrado após down/up. ` +
        `Lido: "${readResult.stdout.trim()}"`
      );
      return;
    }
    info('PASS: Marcador PostgreSQL sobreviveu ao down/up (volume nomeado preserva dados).');

    // 4. Teste de variável ausente — mutação real do env-file
    info('');
    info('Etapa 4/6: Verificando falha de variável ausente (mutação real)...');
    const [removedKey] = envPairs.keys();
    info(`Removendo "${removedKey}" do env-file temporário...`);

    const mutatedLines = [];
    for (const [key, value] of envPairs) {
      if (key !== removedKey) {
        mutatedLines.push(`${key}=${value}`);
      }
    }
    const mutatedContent = mutatedLines.join('\n') + '\n';

    const tempDir = await mkdtemp(join(tmpdir(), 'betufu-env-test-'));
    const tempEnvPath = join(tempDir, 'env');
    try {
      await writeFile(tempEnvPath, mutatedContent, { encoding: 'utf8', mode: 0o600 });

      const testResult = await runCaptured(
        'docker',
        [
          'compose',
          '--env-file', tempEnvPath,
          '-f', resolve(resolveProjectRoot(), 'infra', 'compose.yaml'),
          '--project-name', 'infra',
          '--profile', 'daily',
          'config', '--quiet',
        ],
        { timeoutMs: 30_000 },
      );

      if (testResult.exitCode === 0) {
        fail(
          `Compose config passou mesmo sem a variável "${removedKey}" — ` +
          'o mecanismo ${VAR:?msg} não protege essa chave.'
        );
        return;
      }

      const stderr = testResult.stderr;
      if (!stderr.includes(removedKey)) {
        fail(
          `Compose falhou mas stderr não menciona "${removedKey}". ` +
          `Stderr: ${stderr.substring(0, 300)}`
        );
        return;
      }

      const SENSITIVE_PATTERN = /PASSWORD|SECRET|KEY|TOKEN/i;
      const sensitiveEntries = [...envPairs.entries()]
        .filter(([key]) => SENSITIVE_PATTERN.test(key))
        .filter(([, value]) => value.length > 0);

      for (const [key, value] of sensitiveEntries) {
        if (stderr.includes(value)) {
          fail(
            `SEGURANÇA: stderr vazou o valor da variável sensível "${key}" ` +
            `ao reportar chave ausente. Chave testada: "${removedKey}".`
          );
          return;
        }
      }

      info(`Compose falhou corretamente sem "${removedKey}": exit code ${testResult.exitCode}.`);
      info('Stderr menciona a chave ausente e não contém nenhum valor sensível.');
      info('PASS: Falha de variável ausente nomeia chave sem vazar valor.');
    } finally {
      try { await rm(tempDir, { recursive: true, force: true }); } catch { /* ignorar */ }
    }

    // 5. Teste empírico de concorrência — [EDGE OPS-01/concurrency] e [EDGE OPS-02/concurrency]
    info('');
    info('Etapa 5/6: Verificando concorrência empírica cross-profile (lock ativo vs up daily/full)...');
    {
      const concurrentDaily = await runCaptured(
        process.execPath,
        [resolve(resolveProjectRoot(), 'scripts', 'dev.mjs'), 'up', 'daily'],
        { timeoutMs: 15_000 },
      );
      if (concurrentDaily.exitCode === 0) {
        fail('Concorrência falhou: processo concorrente up daily adquiriu lock que já estava em uso.');
        return;
      }
      if (!concurrentDaily.stderr.includes('Outra instância está rodando')) {
        fail(`Processo concorrente daily falhou mas stderr não indicou erro de lock: "${concurrentDaily.stderr}".`);
        return;
      }

      const concurrentFull = await runCaptured(
        process.execPath,
        [resolve(resolveProjectRoot(), 'scripts', 'dev.mjs'), 'up', 'full'],
        { timeoutMs: 15_000 },
      );
      if (concurrentFull.exitCode === 0) {
        fail('Concorrência falhou: processo concorrente up full adquiriu lock que já estava em uso.');
        return;
      }
      if (!concurrentFull.stderr.includes('Outra instância está rodando')) {
        fail(`Processo concorrente full falhou mas stderr não indicou erro de lock: "${concurrentFull.stderr}".`);
        return;
      }
      info('PASS: Concorrência cross-profile (daily e full) bloqueada com sucesso pelo lockfile (exit codes !== 0).');
    }

    // 6. Teste empírico de encoding — [EDGE OPS-02/encoding]
    info('');
    info('Etapa 6/6: Verificando tratamento de caminhos, variáveis e logs com caracteres Unicode (encoding)...');
    {
      // Valida sanitizeOutput da BETUFU com chaves e valores Unicode
      const unicodeEnvMap = {
        BETUFU_SENHA_TESTE_ÁÉÍÓÚ: 'segredo_confidencial_ação_✓',
      };
      const rawLog = 'Iniciando BETUFU_SENHA_TESTE_ÁÉÍÓÚ com valor: segredo_confidencial_ação_✓ na máquina local';
      const sanitized = sanitizeOutput(rawLog, unicodeEnvMap);
      if (sanitized.includes('segredo_confidencial_ação_✓')) {
        fail('Sanitização falhou ao mascarar segredo contendo caracteres Unicode.');
        return;
      }
      if (!sanitized.includes('BETUFU_SENHA_TESTE_ÁÉÍÓÚ') || !sanitized.includes('[REDACTED:BETUFU_SENHA_TESTE_ÁÉÍÓÚ]')) {
        fail(`Sanitização não aplicou máscara esperada: "${sanitized}"`);
        return;
      }

      // Valida manipulação de caminhos e lockfiles sob diretório temporário Unicode
      const unicodeTempDir = await mkdtemp(join(tmpdir(), 'betufu-ação-têst-✓-'));
      const unicodeLockFile = join(unicodeTempDir, 'betufu-lock-têst-ação.lock');
      try {
        const fileHandle = await open(unicodeLockFile, constants.O_CREAT | constants.O_EXCL | constants.O_WRONLY, 0o600);
        await fileHandle.writeFile(`pid=${process.pid}\nnonce=unicode_test_✓\n`, 'utf8');
        await fileHandle.close();

        const content = await readFile(unicodeLockFile, 'utf8');
        if (!content.includes('unicode_test_✓')) {
          fail('Falha ao validar leitura de lockfile em caminho Unicode.');
          return;
        }
        await unlink(unicodeLockFile);
        info('PASS: Manipulação de caminhos, logs e sanitização com caracteres Unicode (UTF-8) validada.');
      } finally {
        try { await rm(unicodeTempDir, { recursive: true, force: true }); } catch { /* ignorar */ }
      }
    }
  } finally {
    await verifyLock.release();
    info('Lock de verificação liberado.');
  }

  info('');
  info('=== Verificação do profile "daily" concluída com sucesso ===');
}

async function commandVerifyFull() {
  info('=== Verificação do profile "full" ===');
  info('');

  // Lock global para cobrir todo o ciclo de verificação full
  const verifyLock = await acquireLock();
  info(`Lock de verificação global adquirido (nonce: ${verifyLock.nonce}).`);

  try {
    // 1. Subir composição completa (7 serviços com build)
    info('Etapa 1/5: Subindo composição full (7 serviços com build)...');
    const buildExit = await runInherited('docker', [...composeArgs('full'), 'build']);
    if (buildExit !== 0) {
      fail(`docker compose build falhou com exit code ${buildExit}.`);
      return;
    }
    const upExit = await runInherited('docker', [...composeArgs('full'), 'up', '-d', '--wait']);
    if (upExit !== 0) {
      fail(`docker compose up falhou com exit code ${upExit}.`);
      return;
    }
    info('Aguardando healthchecks...');
    await waitHealthy('full', { timeoutMs: 360_000 });
    info('Todos os 7 serviços estão healthy.');

    // 2. Liveness do backend e Redirect OIDC 302 exato via Nginx
    info('');
    info('Etapa 2/5: Verificando liveness do backend e redirect OIDC 302 via Nginx...');
    try {
      // 2a. Liveness /api/v1/session
      const resSession = await fetch('http://127.0.0.1:4200/api/v1/session', {
        headers: { Host: 'localhost:4200' },
        signal: AbortSignal.timeout(15_000),
      });
      if (resSession.status !== 200) {
        fail(`Backend via Nginx retornou HTTP ${resSession.status} em /api/v1/session.`);
        return;
      }
      const sessionData = await resSession.json();
      if (sessionData.authenticated !== false) {
        fail(`Contrato de sessão anônima inesperado: ${JSON.stringify(sessionData)}`);
        return;
      }
      info('PASS: Backend respondeu através do Nginx em /api/v1/session (200 OK, unauthenticated).');

      // 2b. Redirect OIDC 302 exato para Keycloak com origem pública
      const resAuth = await fetch('http://127.0.0.1:4200/oauth2/authorization/keycloak', {
        headers: { Host: 'localhost:4200' },
        redirect: 'manual',
        signal: AbortSignal.timeout(15_000),
      });
      if (resAuth.status !== 302) {
        fail(`Endpoint /oauth2/authorization/keycloak retornou status HTTP ${resAuth.status} em vez de 302.`);
        return;
      }
      const location = resAuth.headers.get('location') || '';
      if (!location.startsWith('http://localhost:8081/realms/betufu-local/protocol/openid-connect/auth')) {
        fail(`Redirect OIDC com Location inválida: "${location}". Esperado início com "http://localhost:8081/realms/betufu-local/protocol/openid-connect/auth".`);
        return;
      }
      const hasRedirectUri =
        location.includes('redirect_uri=http://localhost:4200/login/oauth2/code/keycloak') ||
        location.includes('redirect_uri=http://127.0.0.1:4200/login/oauth2/code/keycloak') ||
        location.includes('login%2Foauth2%2Fcode%2Fkeycloak');
      const hasClientId = location.includes('client_id=betufu-bff');
      const hasResponseType = location.includes('response_type=code');

      if (!hasClientId || !hasRedirectUri || !hasResponseType) {
        fail(`Redirect OIDC não contém client_id=betufu-bff, response_type=code ou redirect_uri público correto: "${location}".`);
        return;
      }
      info('PASS: Redirect OIDC exato comprovado (HTTP 302 -> Keycloak com origem pública http://localhost:4200).');
    } catch (error) {
      fail(`Falha ao verificar backend e redirects via Nginx: ${error.message}`);
      return;
    }

    // 3. Frontend Angular servido pelo Nginx + SPA Fallback + Paridade Regex
    info('');
    info('Etapa 3/5: Verificando frontend Angular, SPA fallback e paridade regex...');
    try {
      // 3a. Raiz Angular com <app-root>
      const resRoot = await fetch('http://127.0.0.1:4200/', {
        headers: { Host: 'localhost:4200' },
        signal: AbortSignal.timeout(10_000),
      });
      if (resRoot.status !== 200) {
        fail(`Frontend Nginx retornou HTTP ${resRoot.status} na raiz.`);
        return;
      }
      const htmlText = await resRoot.text();
      if (!htmlText.includes('<app-root>') && !htmlText.includes('app-root')) {
        fail('Resposta da raiz não contém elemento Angular <app-root>.');
        return;
      }

      // 3b. SPA routing fallback para rota inexistente
      const resSpa = await fetch('http://127.0.0.1:4200/rota-spa-teste-fallback', {
        headers: { Host: 'localhost:4200' },
        signal: AbortSignal.timeout(10_000),
      });
      if (resSpa.status !== 200) {
        fail(`SPA fallback retornou HTTP ${resSpa.status} em vez de 200.`);
        return;
      }

      // 3c. Paridade Regex: rotas como /apidocs ou /logins caem no SPA fallback e não no backend
      const resApidocs = await fetch('http://127.0.0.1:4200/apidocs', {
        headers: { Host: 'localhost:4200' },
        signal: AbortSignal.timeout(10_000),
      });
      const apidocsText = await resApidocs.text();
      if (resApidocs.status !== 200 || !apidocsText.includes('<app-root>')) {
        fail('Paridade regex falhou: /apidocs não caiu no SPA fallback do frontend.');
        return;
      }

      info('PASS: Frontend Angular servido com sucesso, SPA fallback e paridade regex comprovados.');
    } catch (error) {
      fail(`Falha ao testar frontend Angular: ${error.message}`);
      return;
    }

    // 4. Regras de segurança Nginx (arquivos ocultos, source maps, no-cache BFF)
    info('');
    info('Etapa 4/5: Verificando regras de segurança Nginx (segredos, source maps, no-cache)...');
    try {
      // 4a. Arquivos ocultos (.env, .git)
      const resHidden = await fetch('http://127.0.0.1:4200/.env', {
        headers: { Host: 'localhost:4200' },
        signal: AbortSignal.timeout(10_000),
      });
      if (resHidden.status !== 404 && resHidden.status !== 403) {
        fail(`Nginx serviu arquivo oculto /.env com status ${resHidden.status}`);
        return;
      }

      // 4b. Source maps (.map)
      const resMap = await fetch('http://127.0.0.1:4200/main.js.map', {
        headers: { Host: 'localhost:4200' },
        signal: AbortSignal.timeout(10_000),
      });
      if (resMap.status !== 404 && resMap.status !== 403) {
        fail(`Nginx serviu source map com status ${resMap.status}`);
        return;
      }

      // 4c. Cache-Control no-store em rotas estáticas Nginx (index.html) e BFF
      const resIndex = await fetch('http://127.0.0.1:4200/', {
        headers: { Host: 'localhost:4200' },
        signal: AbortSignal.timeout(10_000),
      });
      const cacheControlIndex = resIndex.headers.get('cache-control') || '';
      if (!cacheControlIndex.includes('no-store') && !cacheControlIndex.includes('no-cache')) {
        fail(`Header Cache-Control em resposta Nginx (index.html) inseguro: "${cacheControlIndex}"`);
        return;
      }

      const resBff = await fetch('http://127.0.0.1:4200/api/v1/session', {
        headers: { Host: 'localhost:4200' },
        signal: AbortSignal.timeout(10_000),
      });
      const cacheControlBff = resBff.headers.get('cache-control') || '';
      if (!cacheControlBff.includes('no-store') && !cacheControlBff.includes('no-cache')) {
        fail(`Header Cache-Control em endpoint BFF inseguro: "${cacheControlBff}"`);
        return;
      }

      info('PASS: Segurança Nginx comprovada (bloqueio de segredos/maps e no-cache Nginx/BFF).');
    } catch (error) {
      fail(`Falha ao testar segurança Nginx: ${error.message}`);
      return;
    }

    // 5. Idempotência do profile "full" (7 serviços)
    info('');
    info('Etapa 5/5: Verificando idempotência do profile "full"...');
    {
      const beforeResult = await runCaptured(
        'docker',
        [...composeArgs('full'), 'ps', '--format', 'json'],
      );
      if (beforeResult.exitCode !== 0) {
        fail('Não foi possível inspecionar containers antes do segundo up em full.');
        return;
      }
      const beforeIds = parseContainerIds(beforeResult.stdout, 'full');
      if (beforeIds.size !== EXPECTED_FULL_SERVICES) {
        fail(`Esperado ${EXPECTED_FULL_SERVICES} serviços no profile full, mas encontrado ${beforeIds.size}.`);
        return;
      }

      const up2Exit = await runInherited(
        'docker',
        [...composeArgs('full'), 'up', '-d', '--wait'],
      );
      if (up2Exit !== 0) {
        fail(`Segundo up em full falhou com exit code ${up2Exit}.`);
        return;
      }
      await waitHealthy('full');

      const afterResult = await runCaptured(
        'docker',
        [...composeArgs('full'), 'ps', '--format', 'json'],
      );
      if (afterResult.exitCode !== 0) {
        fail('Não foi possível inspecionar containers após segundo up em full.');
        return;
      }
      const afterIds = parseContainerIds(afterResult.stdout, 'full');

      if (!sameEntries(beforeIds, afterIds)) {
        fail(
          'Idempotência em full falhou: containers foram recriados no segundo up. ' +
          `Antes: ${JSON.stringify([...beforeIds])}, Depois: ${JSON.stringify([...afterIds])}`
        );
        return;
      }
      info('PASS: Containers full idênticos após segundo up (idempotência de 7 serviços).');
    }
  } finally {
    await verifyLock.release();
    info('Lock de verificação liberado.');
  }

  info('');
  info('=== Verificação do profile "full" concluída com sucesso ===');
}

// ---------------------------------------------------------------------------
// Subcomando: status
// ---------------------------------------------------------------------------

async function commandStatus() {
  const exitCode = await runInherited(
    'docker',
    ['compose', ...composeArgsNoProfile().slice(1), '--profile', 'daily', '--profile', 'full', 'ps', '--all'],
  );
  process.exitCode = exitCode;
}

// ---------------------------------------------------------------------------
// Subcomando: stop
// ---------------------------------------------------------------------------

async function commandStop() {
  info('Parando serviços (volumes preservados)...');
  const exitCode = await runInherited(
    'docker',
    ['compose', ...composeArgsNoProfile().slice(1), '--profile', 'daily', '--profile', 'full', 'stop'],
  );
  if (exitCode === 0) {
    info('Serviços parados. Volumes preservados.');
  } else {
    fail(`docker compose stop falhou com exit code ${exitCode}.`);
  }
}

// ---------------------------------------------------------------------------
// Subcomando: logs
// ---------------------------------------------------------------------------

async function commandLogs(service) {
  const args = ['compose', ...composeArgsNoProfile().slice(1), '--profile', 'daily', '--profile', 'full', 'logs', '--tail', '100'];
  if (service) {
    args.push(service);
  }
  const envPairs = await readEnvPairs();
  const envObj = Object.fromEntries(envPairs);
  const result = await runCaptured('docker', args, { timeoutMs: 60_000 });
  if (result.stdout) {
    console.log(sanitizeOutput(result.stdout, envObj));
  }
  if (result.stderr) {
    console.error(sanitizeOutput(result.stderr, envObj));
  }
  process.exitCode = result.exitCode;
}

// ---------------------------------------------------------------------------
// Utilitários internos
// ---------------------------------------------------------------------------

const DAILY_SERVICES = new Set(['postgres', 'redis', 'keycloak', 'seaweedfs', 'mailpit']);
const FULL_SERVICES = new Set(['postgres', 'redis', 'keycloak', 'seaweedfs', 'mailpit', 'backend', 'frontend']);

/**
 * Parseia a saída de docker compose ps --format json e retorna
 * um Map de service → container ID filtrando pelo profile e estado running.
 */
function parseContainerIds(output, profile = 'daily') {
  const allowed = profile === 'full' ? FULL_SERVICES : DAILY_SERVICES;
  const entries = new Map();
  const trimmed = output.trim();

  const processObj = (obj) => {
    const serviceName = obj.Service ?? obj.Name;
    const id = obj.ID ?? obj.Id;
    const state = (obj.State ?? '').toLowerCase();
    if (serviceName && id && allowed.has(serviceName) && state === 'running') {
      entries.set(serviceName, id);
    }
  };

  if (trimmed.startsWith('[')) {
    try {
      const arr = JSON.parse(trimmed);
      for (const obj of arr) {
        processObj(obj);
      }
      return entries;
    } catch {
      // fallback para parsing linha a linha
    }
  }
  for (const line of trimmed.split('\n').filter((l) => l.trim() !== '')) {
    try {
      const obj = JSON.parse(line);
      processObj(obj);
    } catch {
      // Ignorar linhas não-JSON
    }
  }
  return entries;
}

/**
 * Compara dois Maps — mesmas chaves e mesmos valores.
 */
function sameEntries(a, b) {
  if (a.size === 0 || b.size === 0 || a.size !== b.size) return false;
  for (const [key, value] of a) {
    if (b.get(key) !== value) return false;
  }
  return true;
}

/**
 * Encontra o ID do container de um serviço.
 */
async function findServiceContainer(serviceName, profile) {
  const result = await runCaptured(
    'docker',
    [
      'ps', '-q',
      '--filter', 'label=com.docker.compose.project=infra',
      '--filter', `label=com.docker.compose.service=${serviceName}`,
    ],
    { timeoutMs: 15_000 },
  );
  if (result.exitCode !== 0) return null;
  const ids = result.stdout.split('\n').filter((l) => l.trim() !== '');
  return ids.length === 1 ? ids[0].trim() : null;
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

const [command, ...rest] = process.argv.slice(2);

try {
  switch (command) {
    case 'up':
      await commandUp(rest[0]);
      break;
    case 'verify':
      await commandVerify(rest[0]);
      break;
    case 'status':
      await commandStatus();
      break;
    case 'stop':
      await commandStop();
      break;
    case 'logs':
      await commandLogs(rest[0]);
      break;
    default:
      if (command) {
        fail(`Comando desconhecido: ${command}`);
      }
      usage();
      process.exitCode = command ? 1 : 0;
      break;
  }
} catch (error) {
  fail(error instanceof Error ? error.message : String(error));
}
