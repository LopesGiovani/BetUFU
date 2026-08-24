#!/usr/bin/env node

import { readFile, lstat } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
export const defaultProjectRoot = resolve(scriptsDirectory, '..');

export const PATHS = Object.freeze({
  runbook: '.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-PASSWORD-RESET-RECOVERY.md',
  controller: 'backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventRecoveryController.java',
  response: 'backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventRecoveryResponse.java',
  securityConfig: 'backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java',
  eventSecurityConfig: 'backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventSecurityConfiguration.java',
  lookupContract: 'backend/src/main/java/br/com/betufu/platform/identity/OperationalIdentityLookup.java',
  maintenance: 'backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventMaintenanceConfiguration.java',
});

export class PasswordResetRecoveryValidationError extends Error {
  constructor(rule, message) {
    super(`${rule}: ${message}`);
    this.name = 'PasswordResetRecoveryValidationError';
    this.rule = rule;
  }
}

function contractError(rule, message) {
  throw new PasswordResetRecoveryValidationError(rule, message);
}

function expect(condition, rule, message) {
  if (!condition) {
    contractError(rule, message);
  }
}

function normalizeText(source) {
  return source.replace(/\r\n?/gu, '\n');
}

function stripJavaComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//gu, '')
    .replace(/(^|[^:])\/\/[^\n]*/gu, '$1');
}

function methodBody(source, methodName) {
  const method = new RegExp(`\\b${methodName}\\s*\\(`, 'u').exec(source);
  if (!method) return null;

  const parametersStart = source.indexOf('(', method.index);
  let parenthesesDepth = 0;
  let parametersEnd = -1;
  for (let index = parametersStart; index < source.length; index += 1) {
    if (source[index] === '(') parenthesesDepth += 1;
    if (source[index] === ')') parenthesesDepth -= 1;
    if (parenthesesDepth === 0) {
      parametersEnd = index;
      break;
    }
  }
  if (parametersEnd < 0) return null;

  const openingBrace = source.indexOf('{', parametersEnd);
  if (openingBrace < 0) return null;
  let depth = 0;
  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1;
    if (source[index] === '}') depth -= 1;
    if (depth === 0) return source.slice(openingBrace + 1, index);
  }
  return null;
}

export function validateRunbookContent(content) {
  expect(typeof content === 'string' && content.trim().length > 0, 'RUNBOOK_EMPTY', 'runbook nao pode ser vazio.');

  // 1. Owner principal
  expect(
    /owner|respons[aá]vel/iu.test(content) && /ADMINISTRADOR_GERAL/u.test(content),
    'RUNBOOK_OWNER',
    'runbook deve declarar explicitamente o Owner principal (Administrador Geral).',
  );

  // 2. Backup operacional
  expect(
    /backup/iu.test(content) && /(?:substituto|suporte|infraestrutura)/iu.test(content),
    'RUNBOOK_BACKUP',
    'runbook deve declarar explicitamente o Backup operacional / substituto.',
  );

  // 3. Monitoramento e Alertas
  expect(
    /alerta/iu.test(content) && /FAILED/u.test(content) && /(?:5|quinta)/iu.test(content) && /TERMINATION_FAILED/u.test(content),
    'RUNBOOK_ALERTS',
    'runbook deve declarar alertas para eventos FAILED, 5 falhas consecutivas e erro TERMINATION_FAILED.',
  );

  // 4. Rotação HMAC: current + previous
  expect(
    /(?:current.*previous|current\+previous|corrente.*anterior)/iu.test(content),
    'RUNBOOK_HMAC_ROTATION',
    'runbook deve declarar suporte a exatamente duas chaves HMAC: current e previous.',
  );

  // 5. Janela de Replay: 5 minutos
  expect(
    /(?:5\s*(?:minutos?|min)|±\s*5)/iu.test(content),
    'RUNBOOK_REPLAY_WINDOW',
    'runbook deve declarar a janela de tolerância de 5 minutos (±5 min).',
  );

  // 6. Retenção: 30 dias para DELIVERED, FAILED até resolução
  expect(
    /(?:30\s*(?:dias?|d))/iu.test(content) && /DELIVERED/u.test(content) && /FAILED/u.test(content),
    'RUNBOOK_RETENTION',
    'runbook deve declarar retenção de 30 dias para DELIVERED e permanência de FAILED até resolução autorizada.',
  );

  // 7. Checkpoint 02-29
  expect(
    /02-29/u.test(content),
    'RUNBOOK_CHECKPOINT',
    'runbook deve declarar o checkpoint de ativação do Plano 02-29.',
  );

  // 8. Diagnóstico e Replay
  expect(
    /(?:diagn[oó]stico|reprocess|replay)/iu.test(content),
    'RUNBOOK_DIAGNOSTIC',
    'runbook deve documentar procedimentos de diagnóstico e reprocessamento.',
  );

  // 9. Escalonamento
  expect(
    /(?:escalonamento|escalation|conting[eê]ncia)/iu.test(content),
    'RUNBOOK_ESCALATION',
    'runbook deve documentar matriz de escalonamento e contingência.',
  );

  // 10. Proibição de segredos reais
  expect(
    !/-----BEGIN (?:RSA )?PRIVATE KEY-----/u.test(content)
      && !/(?:password|secret)\s*[:=]\s*['"][A-Za-z0-9+/=]{16,}['"]/iu.test(content),
    'RUNBOOK_SECRETS_FORBIDDEN',
    'runbook nao pode conter segredos, senhas reais ou chaves privadas.',
  );
}

export function validateControllerSource(source) {
  expect(typeof source === 'string' && source.trim().length > 0, 'CONTROLLER_EMPTY', 'fonte do controller nao pode ser vazio.');

  // Package correto
  expect(
    /package\s+br\.com\.betufu\.platform\.session\.passwordreset\s*;/u.test(source),
    'CONTROLLER_PACKAGE',
    'controller deve pertencer ao package br.com.betufu.platform.session.passwordreset.',
  );

  // Anotações obrigatórias
  expect(
    /@RestController/u.test(source),
    'CONTROLLER_REST',
    'controller deve ser anotado com @RestController.',
  );
  expect(
    /@Profile\s*\(\s*["']runtime\s*\|\s*integrated["']\s*\)/u.test(source),
    'CONTROLLER_PROFILE',
    'controller deve ter profile @Profile("runtime | integrated").',
  );

  const code = stripJavaComments(source);
  const mappings = [...code.matchAll(/@(GetMapping|PostMapping)\s*\(\s*(?:path\s*=\s*)?([^,\n)]+)/gu)]
    .map((match) => `${match[1]}:${match[2].replace(/\s+/gu, '')}`);
  const expectedMappings = [
    'GetMapping:FAILED_EVENTS_PATH',
    'GetMapping:RECOVERY_BASE_PATH+"/{sourceEventId}"',
    'PostMapping:REPROCESS_EVENT_PATH',
  ];
  expect(
    mappings.length === expectedMappings.length
      && expectedMappings.every((mapping) => mappings.includes(mapping)),
    'CONTROLLER_CANONICAL_MAPPINGS',
    'controller deve declarar exatamente os tres mappings canonicos, sem aliases.',
  );

  for (const handler of ['listFailedEvents', 'getEvent', 'reprocessFailedEvent']) {
    const body = methodBody(code, handler);
    expect(
      body !== null && /\brequireGlobalAdmin\s*\(/u.test(body),
      'CONTROLLER_HANDLER_GUARD',
      `handler ${handler} deve executar requireGlobalAdmin no proprio corpo.`,
    );
  }

  // Segunda guarda: OperationalIdentityLookup e verificação global de ADMINISTRADOR_GERAL
  expect(
    /\bOperationalIdentityLookup\b/u.test(source),
    'CONTROLLER_IDENTITY_LOOKUP',
    'controller deve utilizar exclusivamente OperationalIdentityLookup.',
  );
  expect(
    // Exige a forma de CODIGO — scopeRef() com parenteses. A forma sem parenteses so aparece
    // em javadoc e comentario deste arquivo; aceita-la deixava o guard satisfeito por texto
    // explicativo, de modo que remover a checagem real nao o derrubava. Guard satisfeito por
    // comentario nao e guard.
    /ADMINISTRADOR_GERAL/u.test(source) && /scopeRef\s*\(\s*\)\s*==\s*null/u.test(source),
    'CONTROLLER_GLOBAL_ADMIN_GUARD',
    'controller deve verificar concessao global de ADMINISTRADOR_GERAL (com scopeRef == null).',
  );

  // Isolamento modular: Proibição de imports de br.com.betufu.identity
  expect(
    !/^import\s+br\.com\.betufu\.identity/mu.test(source),
    'CONTROLLER_MODULAR_ISOLATION',
    'controller nao pode importar nenhuma classe de br.com.betufu.identity.',
  );
}

export function validateLookupContractSource(source) {
  expect(
    typeof source === 'string' && source.trim().length > 0,
    'LOOKUP_CONTRACT_EMPTY',
    'contrato OperationalIdentityLookup nao pode ser vazio.',
  );
  expect(
    /public\s+interface\s+OperationalIdentityLookup/u.test(source)
      && /Optional\s*<\s*OperationalIdentity\s*>\s+lookupIdentity\s*\(/u.test(source),
    'LOOKUP_CONTRACT_METHOD',
    'OperationalIdentityLookup deve expor lookupIdentity sem importar identity.internal.',
  );
  expect(
    /record\s+OperationalIdentity\s*\(/u.test(source)
      && /record\s+GrantedRole\s*\(/u.test(source)
      && !/^import\s+br\.com\.betufu\.identity/mu.test(source),
    'LOOKUP_CONTRACT_NEUTRAL_TYPES',
    'contrato deve usar apenas projecoes neutras pertencentes ao platform.',
  );
}

export function validateMaintenanceSource(source) {
  expect(
    /@ConditionalOnProperty\s*\([\s\S]*?name\s*=\s*"maintenance-enabled"[\s\S]*?havingValue\s*=\s*"true"/u.test(source),
    'MAINTENANCE_FAIL_CLOSED',
    'driver operacional deve nascer desabilitado e exigir maintenance-enabled=true.',
  );
  expect(/@Scheduled\s*\(/u.test(source), 'MAINTENANCE_SCHEDULED', 'driver deve possuir acionador @Scheduled real.');
  for (const call of ['recoverPendingEvents', 'cleanupDeliveredOlderThan', 'countByStatus']) {
    expect(
      new RegExp(`\\.${call}\\s*\\(`, 'u').test(source),
      'MAINTENANCE_OPERATION',
      `driver deve executar ${call} no ciclo de producao.`,
    );
  }
  expect(
    /betufu\.password_reset\.inbox\.failed/u.test(source),
    'MAINTENANCE_FAILED_METRIC',
    'driver deve publicar metrica agregada de FAILED sem tags sensiveis.',
  );
}

export function validateResponseSource(source) {
  expect(typeof source === 'string' && source.trim().length > 0, 'RESPONSE_EMPTY', 'fonte do response nao pode ser vazio.');

  expect(
    /package\s+br\.com\.betufu\.platform\.session\.passwordreset\s*;/u.test(source),
    'RESPONSE_PACKAGE',
    'response deve pertencer ao package br.com.betufu.platform.session.passwordreset.',
  );

  expect(
    /record\s+PasswordResetEventRecoveryResponse/u.test(source),
    'RESPONSE_RECORD',
    'PasswordResetEventRecoveryResponse deve ser declarado como record.',
  );

  for (const field of ['sourceEventId', 'status', 'effectExecuted', 'terminatedSessionCount', 'actorIdentityId', 'processedAt']) {
    expect(
      new RegExp(`\\b${field}\\b`, 'u').test(source),
      'RESPONSE_FIELD',
      `PasswordResetEventRecoveryResponse deve conter o campo ${field}.`,
    );
  }
}

export function validateSecurityConfigurations(securitySource, eventSecuritySource) {
  // 1. SecurityConfiguration.java
  expect(
    /\.requestMatchers\s*\(\s*["']\/api\/v1\/operations\/password-reset-events\/\*\*["']\s*\)\s*\.hasRole\s*\(\s*["']ADMINISTRADOR_GERAL["']\s*\)/u.test(securitySource),
    'SECURITY_MATCHER_RECOVERY',
    'SecurityConfiguration deve proteger /api/v1/operations/password-reset-events/** com hasRole("ADMINISTRADOR_GERAL").',
  );

  // A ordem e comparada entre CHAMADAS de requestMatchers, nunca entre ocorrencias do texto
  // cru: o proprio arquivo cita "/api/v1/**" em comentario ANTES do matcher do recovery, para
  // explicar por que a ordem importa. Casar o comentario faria este guard reprovar uma cadeia
  // correta — defeito da familia "guard casa texto em vez de propriedade".
  const recoveryMatcherIndex = securitySource.search(
    /\.requestMatchers\s*\(\s*["']\/api\/v1\/operations\/password-reset-events\/\*\*["']\s*\)/u,
  );
  const broadApiMatcherIndex = securitySource.search(
    /\.requestMatchers\s*\(\s*["']\/api\/v1\/\*\*["']\s*\)/u,
  );
  expect(
    recoveryMatcherIndex > 0 && broadApiMatcherIndex > recoveryMatcherIndex,
    'SECURITY_MATCHER_ORDER',
    'matcher de /api/v1/operations/password-reset-events/** deve preceder /api/v1/**.',
  );

  // 2. PasswordResetEventSecurityConfiguration.java (cadeia dedicada M2M)
  expect(
    /SessionCreationPolicy\.STATELESS/u.test(eventSecuritySource),
    'EVENT_SECURITY_STATELESS',
    'cadeia dedicada do evento deve ser estritamente STATELESS.',
  );
  expect(
    /\.securityMatcher\s*\(\s*PasswordResetEventEndpoint\.ENDPOINT_PATH\s*\)/u.test(eventSecuritySource),
    'EVENT_SECURITY_MATCHER',
    'cadeia dedicada do evento deve conter securityMatcher restrito ao endpoint exato.',
  );
}

export async function validatePasswordResetRecovery(projectRoot = defaultProjectRoot) {
  async function readText(relativePath) {
    const fullPath = resolve(projectRoot, relativePath);
    try {
      const stat = await lstat(fullPath);
      expect(stat.isFile() && !stat.isSymbolicLink(), 'FILE_NOT_REGULAR', `${relativePath} deve ser arquivo regular.`);
      return normalizeText(await readFile(fullPath, 'utf8'));
    } catch (err) {
      if (err instanceof PasswordResetRecoveryValidationError) throw err;
      contractError('FILE_READ_ERROR', `nao foi possivel ler ${relativePath}: ${err.message}`);
    }
  }

  const [
    runbook,
    controller,
    response,
    securityConfig,
    eventSecurityConfig,
    lookupContract,
    maintenance,
  ] = await Promise.all([
    readText(PATHS.runbook),
    readText(PATHS.controller),
    readText(PATHS.response),
    readText(PATHS.securityConfig),
    readText(PATHS.eventSecurityConfig),
    readText(PATHS.lookupContract),
    readText(PATHS.maintenance),
  ]);

  validateRunbookContent(runbook);
  validateControllerSource(controller);
  validateResponseSource(response);
  validateSecurityConfigurations(securityConfig, eventSecurityConfig);
  validateLookupContractSource(lookupContract);
  validateMaintenanceSource(maintenance);

  return true;
}

async function main() {
  try {
    await validatePasswordResetRecovery();
    console.log('[validate-password-reset-recovery] OK: runbook, recovery controller e configuracoes estao em conformidade.');
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`[validate-password-reset-recovery] ERRO: ${message}`);
    process.exitCode = 1;
  }
}

const invokedAsScript = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedAsScript) {
  await main();
}
