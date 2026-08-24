import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

/**
 * Guarda de fixture-mudou.
 *
 * Uma prova negativa so vale se a mutacao realmente removeu a propriedade. Quando o padrao nao
 * casa — por reformatacao, por acento, por parenteses — o texto sai identico, o validator
 * continua aprovando com razao, e a prova falha por motivo errado. Sem esta guarda o defeito
 * volta silencioso na proxima vez que alguem editar o arquivo alvo.
 */
function assertMutationApplied(original, mutated, rule) {
  assert.notEqual(
    mutated,
    original,
    `mutação inerte para ${rule}: o padrão não casou e a fixture saiu idêntica ao arquivo real`,
  );
}
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

import {
  defaultProjectRoot,
  PATHS,
  validateControllerSource,
  validatePasswordResetRecovery,
  validateResponseSource,
  validateRunbookContent,
  validateSecurityConfigurations,
  validateLookupContractSource,
  validateMaintenanceSource,
} from './validate-password-reset-recovery.mjs';

function removeGuardFromHandler(source, handlerName) {
  const start = source.indexOf(`${handlerName}(`);
  assert.notEqual(start, -1, `handler ${handlerName} deve existir na fixture`);
  const nextHandler = source.indexOf('\n    /**', start + handlerName.length);
  const end = nextHandler === -1 ? source.length : nextHandler;
  const segment = source.slice(start, end);
  const mutatedSegment = segment.replace(/\brequireGlobalAdmin\s*\(/u, 'removedGuard(');
  assertMutationApplied(segment, mutatedSegment, `CONTROLLER_HANDLER_GUARD:${handlerName}`);
  return source.slice(0, start) + mutatedSegment + source.slice(end);
}

describe('validate-password-reset-recovery', () => {
  it('valida com sucesso os artefatos atuais do repositório', async () => {
    const result = await validatePasswordResetRecovery(defaultProjectRoot);
    assert.equal(result, true);
  });

  describe('validateRunbookContent', () => {
    it('aceita runbook completo em conformidade com o contrato', async () => {
      const content = await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8');
      assert.doesNotThrow(() => validateRunbookContent(content));
    });

    it('rejeita runbook vazio', () => {
      assert.throws(
        () => validateRunbookContent(''),
        (err) => err.rule === 'RUNBOOK_EMPTY',
      );
    });

    it('rejeita runbook sem declaração de Owner principal', async () => {
      const content = (await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8'))
        .replace(/Owner Principal/gu, 'Responsavel Ausente')
        .replace(/ADMINISTRADOR_GERAL/gu, 'OUTRO_PAPEL');
      assert.throws(
        () => validateRunbookContent(content),
        (err) => err.rule === 'RUNBOOK_OWNER',
      );
    });

    it('rejeita runbook sem declaração de Backup operacional', async () => {
      const content = (await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8'))
        .replace(/Backup Operacional/gu, 'Segundo Operador')
        .replace(/backup/giu, 'reserva');
      assert.throws(
        () => validateRunbookContent(content),
        (err) => err.rule === 'RUNBOOK_BACKUP',
      );
    });

    it('rejeita runbook sem alertas de FAILED ou quinta tentativa', async () => {
      const content = (await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8'))
        .replace(/TERMINATION_FAILED/gu, 'ERRO_GENERICO');
      assert.throws(
        () => validateRunbookContent(content),
        (err) => err.rule === 'RUNBOOK_ALERTS',
      );
    });

    it('rejeita runbook sem declaração de chaves current e previous', async () => {
      const original = await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8');
      // A regra aceita current/previous E a forma em portugues corrente/anterior. Neutralizar
      // so o ingles deixa a propriedade viva e torna a mutacao inerte: o validator continua
      // aprovando e a prova falha por motivo errado, sem que nada esteja quebrado.
      const content = original
        .replace(/current/giu, 'unica')
        .replace(/previous/giu, 'unica')
        .replace(/corrente/giu, 'unica')
        .replace(/anterior/giu, 'unica');
      assertMutationApplied(original, content, 'RUNBOOK_HMAC_ROTATION');
      assert.throws(
        () => validateRunbookContent(content),
        (err) => err.rule === 'RUNBOOK_HMAC_ROTATION',
      );
    });

    it('rejeita runbook sem janela de replay de 5 minutos', async () => {
      const original = await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8');
      // A regra tambem aceita a abreviacao "5 min"; sem neutraliza-la a mutacao e inerte.
      const content = original
        .replace(/5\s*minutos?/giu, '10 horas')
        .replace(/5\s*min\b/giu, '10 h')
        .replace(/±\s*5/gu, '±10');
      assertMutationApplied(original, content, 'RUNBOOK_REPLAY_WINDOW');
      assert.throws(
        () => validateRunbookContent(content),
        (err) => err.rule === 'RUNBOOK_REPLAY_WINDOW',
      );
    });

    it('rejeita runbook sem retenção de 30 dias para DELIVERED', async () => {
      const content = (await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8'))
        .replace(/30 dias/gu, '7 dias')
        .replace(/now - 30d/gu, 'now - 7d');
      assert.throws(
        () => validateRunbookContent(content),
        (err) => err.rule === 'RUNBOOK_RETENTION',
      );
    });

    it('rejeita runbook sem menção ao checkpoint 02-29', async () => {
      const content = (await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8'))
        .replace(/02-29/gu, '99-99');
      assert.throws(
        () => validateRunbookContent(content),
        (err) => err.rule === 'RUNBOOK_CHECKPOINT',
      );
    });

    it('rejeita runbook contendo segredos ou chaves privadas reais', async () => {
      const content = (await readFile(resolve(defaultProjectRoot, PATHS.runbook), 'utf8'))
        + '\n-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----\n';
      assert.throws(
        () => validateRunbookContent(content),
        (err) => err.rule === 'RUNBOOK_SECRETS_FORBIDDEN',
      );
    });
  });

  describe('validateControllerSource', () => {
    it('aceita controller em conformidade', async () => {
      const source = await readFile(resolve(defaultProjectRoot, PATHS.controller), 'utf8');
      assert.doesNotThrow(() => validateControllerSource(source));
    });

    it('rejeita controller que importa br.com.betufu.identity', async () => {
      const source = (await readFile(resolve(defaultProjectRoot, PATHS.controller), 'utf8'))
        + '\nimport br.com.betufu.identity.internal.LocalIdentity;\n';
      assert.throws(
        () => validateControllerSource(source),
        (err) => err.rule === 'CONTROLLER_MODULAR_ISOLATION',
      );
    });

    it('rejeita controller sem guarda de ADMINISTRADOR_GERAL global', async () => {
      const source = (await readFile(resolve(defaultProjectRoot, PATHS.controller), 'utf8'))
        // scopeRef() == null, COM parenteses: sem eles o regex casa apenas as ocorrencias em
        // comentario e o codigo real permanece intacto — mutacao inerte.
        .replace(/scopeRef\(\)\s*==\s*null/gu, 'true');
      assert.throws(
        () => validateControllerSource(source),
        (err) => err.rule === 'CONTROLLER_GLOBAL_ADMIN_GUARD',
      );
    });

    it('rejeita controller fora do pacote correto', async () => {
      const source = (await readFile(resolve(defaultProjectRoot, PATHS.controller), 'utf8'))
        .replace(/package br\.com\.betufu\.platform\.session\.passwordreset;/u, 'package br.com.betufu.other;');
      assert.throws(
        () => validateControllerSource(source),
        (err) => err.rule === 'CONTROLLER_PACKAGE',
      );
    });

    for (const [name, pattern] of [
      ['listar FAILED', /@GetMapping\s*\(\s*path\s*=\s*FAILED_EVENTS_PATH/u],
      ['consultar por id', /@GetMapping\s*\(\s*path\s*=\s*RECOVERY_BASE_PATH\s*\+\s*"\/\{sourceEventId\}"/u],
      ['reprocessar', /@PostMapping\s*\(\s*path\s*=\s*REPROCESS_EVENT_PATH/u],
    ]) {
      it(`rejeita controller sem mapping canonico de ${name}`, async () => {
        const original = await readFile(resolve(defaultProjectRoot, PATHS.controller), 'utf8');
        const mutated = original.replace(pattern, '@Deprecated(');
        assertMutationApplied(original, mutated, `CONTROLLER_CANONICAL_MAPPINGS:${name}`);
        assert.throws(
          () => validateControllerSource(mutated),
          (err) => err.rule === 'CONTROLLER_CANONICAL_MAPPINGS',
        );
      });
    }

    for (const handler of ['listFailedEvents', 'getEvent', 'reprocessFailedEvent']) {
      it(`rejeita ${handler} sem chamada da segunda guarda`, async () => {
        const original = await readFile(resolve(defaultProjectRoot, PATHS.controller), 'utf8');
        const mutated = removeGuardFromHandler(original, handler);
        assert.throws(
          () => validateControllerSource(mutated),
          (err) => err.rule === 'CONTROLLER_HANDLER_GUARD',
        );
      });
    }

    it('rejeita alias adicional mesmo mantendo as tres rotas canonicas', async () => {
      const original = await readFile(resolve(defaultProjectRoot, PATHS.controller), 'utf8');
      const mutated = original.replace(
        /\n\}\s*$/u,
        '\n    @GetMapping(path = RECOVERY_BASE_PATH + "/alias")\n    public void alias() {}\n}\n',
      );
      assertMutationApplied(original, mutated, 'CONTROLLER_CANONICAL_MAPPINGS:alias');
      assert.throws(
        () => validateControllerSource(mutated),
        (err) => err.rule === 'CONTROLLER_CANONICAL_MAPPINGS',
      );
    });
  });

  describe('validateLookupContractSource', () => {
    it('le e valida o contrato platform-owned realmente consumido', async () => {
      const source = await readFile(resolve(defaultProjectRoot, PATHS.lookupContract), 'utf8');
      assert.doesNotThrow(() => validateLookupContractSource(source));
    });
  });

  describe('validateMaintenanceSource', () => {
    it('prova acionador fail-closed com recovery, cleanup e monitoramento', async () => {
      const source = await readFile(resolve(defaultProjectRoot, PATHS.maintenance), 'utf8');
      assert.doesNotThrow(() => validateMaintenanceSource(source));
      for (const operation of ['recoverPendingEvents', 'cleanupDeliveredOlderThan', 'countByStatus']) {
        const mutated = source.replace(new RegExp(`\\.${operation}\\s*\\(`, 'u'), `.removed${operation}(`);
        assertMutationApplied(source, mutated, `MAINTENANCE_OPERATION:${operation}`);
        assert.throws(
          () => validateMaintenanceSource(mutated),
          (err) => err.rule === 'MAINTENANCE_OPERATION',
        );
      }
    });
  });

  describe('validateResponseSource', () => {
    it('aceita response em conformidade', async () => {
      const source = await readFile(resolve(defaultProjectRoot, PATHS.response), 'utf8');
      assert.doesNotThrow(() => validateResponseSource(source));
    });

    it('rejeita response com campo obrigatório ausente', async () => {
      const source = (await readFile(resolve(defaultProjectRoot, PATHS.response), 'utf8'))
        .replace(/actorIdentityId/gu, 'actorRemoved');
      assert.throws(
        () => validateResponseSource(source),
        (err) => err.rule === 'RESPONSE_FIELD',
      );
    });
  });

  describe('validateSecurityConfigurations', () => {
    it('aceita configurações de segurança corretas', async () => {
      const securitySource = await readFile(resolve(defaultProjectRoot, PATHS.securityConfig), 'utf8');
      const eventSecuritySource = await readFile(resolve(defaultProjectRoot, PATHS.eventSecurityConfig), 'utf8');
      assert.doesNotThrow(() => validateSecurityConfigurations(securitySource, eventSecuritySource));
    });

    it('rejeita configuração se matcher de recovery estiver após o matcher genérico', async () => {
      const originalSecurity = await readFile(resolve(defaultProjectRoot, PATHS.securityConfig), 'utf8');
      // Regex em vez de string literal: o texto exato depende de indentacao e de comentarios
      // vizinhos, entao um replace literal vira no-op silencioso quando o arquivo e reformatado.
      const removed = originalSecurity.replace(
        /\.requestMatchers\s*\(\s*"\/api\/v1\/operations\/password-reset-events\/\*\*"\s*\)\s*\.hasRole\s*\(\s*"ADMINISTRADOR_GERAL"\s*\)/u,
        '',
      );
      assertMutationApplied(originalSecurity, removed, 'SECURITY_MATCHER_ORDER');
      const securitySource = removed
        + '\n.requestMatchers("/api/v1/operations/password-reset-events/**").hasRole("ADMINISTRADOR_GERAL")';
      const eventSecuritySource = await readFile(resolve(defaultProjectRoot, PATHS.eventSecurityConfig), 'utf8');
      assert.throws(
        () => validateSecurityConfigurations(securitySource, eventSecuritySource),
        (err) => err.rule === 'SECURITY_MATCHER_ORDER',
      );
    });
  });
});
