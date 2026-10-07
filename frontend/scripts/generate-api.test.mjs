import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { mkdir, mkdtemp, readdir, readFile, rm, stat, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { phase7PlanContract } from '../../scripts/validate-phase7-approvals.mjs';

import {
  APPROVED_GENERATED_DIGEST,
  APPROVED_ENGINE,
  APPROVED_GENERATED_FILES,
  PUBLIC_GENERATOR_API_TAGS,
  PUBLIC_GENERATOR_GLOBAL_PROPERTY,
  PUBLIC_GENERATOR_MODELS,
  GenerateApiContractError,
  assertApprovedGeneratedManifest,
  executeGenerateApi,
  hardenGeneratedTypeScript,
  inspectGeneratedOpenApiContract,
  inspectOpenApiContract,
  normalizeGeneratedText,
  readTreeManifest,
  sanitizeError,
  validateGeneratedTypeScript,
} from './generate-api.mjs';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptsDirectory, '..', '..');
const runnerPath = join(scriptsDirectory, 'generate-api.mjs');
const FIXED_NOW = '2026-08-11T20:00:00.000Z';
const FIXED_NONCE = '0123456789abcdef0123456789abcdef';
const RAW_BASE_SERVICE =
  "export class BaseService {\r\n    protected basePath = 'http://localhost';\r\n}\r\n";

const PRIVACY_OPERATIONS = [
  ['/api/v1/privacy/requests', 'post', 'requestPrivacyRight', 'PrivacyCaseReceipt'],
  ['/api/v1/privacy/me/cases', 'get', 'getOwnPrivacyCases', 'PrivacyCase'],
  ['/api/v1/privacy/requests/{caseId}', 'get', 'getPrivacyCase', 'PrivacyCase'],
  ['/api/v1/privacy/requests/{caseId}/responses', 'post', 'respondPrivacyCase', 'PrivacyResponseReceipt'],
  ['/api/v1/privacy/requests/{caseId}/exports', 'post', 'preparePrivacyExport', 'PrivacyExport'],
  ['/api/v1/privacy/requests/{caseId}/exports/latest', 'get', 'downloadPrivacyExport', 'PrivacyExport'],
];
const PRIVACY_FIELDS = {
  PrivacyRequest: ['subjectKind', 'subjectRef', 'requestKind', 'proofReference', 'verifiedChannel'],
  PrivacyCaseReceipt: ['caseId', 'state', 'requestedAt'],
  PrivacyCase: ['caseId', 'subjectKind', 'requestKind', 'state', 'requestedAt', 'timeline'],
  PrivacyTimelineItem: ['eventKind', 'occurredAt', 'responseText'],
  PrivacyResponseCommand: ['decision', 'responseText'],
  PrivacyResponseReceipt: ['caseId', 'state', 'eventKind', 'occurredAt'],
  PrivacyExport: ['exportId', 'data', 'blockedClasses', 'complete'],
  PrivacyRestrictedRecord: ['reference', 'fields'],
};
function assertPrivacyContract(source) {
  for (const [path, method, id, model] of PRIVACY_OPERATIONS) {
    const block = source.split(`  ${path}:\n`)[1]?.split(/\n  \S/u)[0];
    assert.ok(block, `missing ${path}`);
    assert.match(block, new RegExp(`^    ${method}:`, 'mu'));
    assert.match(block, new RegExp(`operationId: ${id}\\b`, 'u'));
    assert.match(block, /tags: \[PrivacyRights\]/u);
    assert.match(block, /security: \[\{cookieSession: \[\]\}\]/u);
    assert.match(block, /description: private, no-store/u);
    assert.match(block, /description: Cookie/u);
    assert.match(block, new RegExp(`#/components/schemas/${model}\\b`, 'u'));
  }
  for (const [name, fields] of Object.entries(PRIVACY_FIELDS)) {
    const block = source.split(`    ${name}:\n`)[1]?.split(/\n    \S/u)[0];
    assert.ok(block, `missing ${name}`);
    assert.match(block, /additionalProperties: false/u);
    assert.deepEqual(schemaFields(block), fields);
    assert.match(block, new RegExp(`required: \\[${fields.join(', ')}\\]`, 'u'));
  }
  const request = source.split('    PrivacyRequest:\n')[1]?.split(/\n    \S/u)[0];
  assert.match(request, /enum: \[NOTICE, ACCESS, CORRECTION, DELETION, OBJECTION, PSEUDONYMIZATION\]/u);
  assert.match(request, /type: \[string, "null"\], format: uuid/u);
  assert.match(request, /ACCOUNT requires null subjectRef and proofReference/u);
  assert.match(source, /decision: \{type: string, enum: \[APPROVE, REJECT, ANALYZE\]\}/u);
  assert.match(source, /maximum: 100, default: 50/u);
  assert.doesNotMatch(source, /^  \/api\/v1\/privacy\/operations\//mu);
  assert.doesNotMatch(source, /^  \/api\/v1\/privacy\/requests\/\{(?:id|caseId)\}\/export:/mu);
}

test('privacy contract mirrors real producers and keeps all reads session-private', async () => {
  const source = await readFile(join(projectRoot, 'backend/openapi/betufu.yaml'), 'utf8');
  assertPrivacyContract(source);
  const controller = await readFile(join(projectRoot, 'backend/src/main/java/br/com/betufu/privacy/internal/PrivacyRightsController.java'), 'utf8');
  assert.match(controller, /@RequestMapping\("\/api\/v1\/privacy"\)/u);
  for (const [path, method] of PRIVACY_OPERATIONS) {
    const annotation = method === 'get' ? 'GetMapping' : 'PostMapping';
    assert.ok(controller.includes(`@${annotation}("${path.replace('/api/v1/privacy', '')}")`));
  }
});

test('privacy contract rejects anonymous reads, forged owners, omitted nulls and route aliases', async () => {
  const source = await readFile(join(projectRoot, 'backend/openapi/betufu.yaml'), 'utf8');
  assertPrivacyContract(source);
  const mutants = [
    source.replace(/(operationId: getOwnPrivacyCases\n[\s\S]*?security: )\[\{cookieSession: \[\]\}\]/u, '$1[]'),
    source.replace('required: [subjectKind, subjectRef, requestKind, proofReference, verifiedChannel]', 'required: [subjectKind, requestKind, verifiedChannel]'),
    source.replace('    PrivacyCase:\n', '    PrivacyCase:\n      ownerIdentityId: {type: string}\n').replace('        timeline:', '        ownerIdentityId: {type: string}\n        timeline:'),
    source.replace('/api/v1/privacy/requests/{caseId}/responses:', '/api/v1/privacy/operations/requests/{caseId}/responses:'),
    source.replace('additionalProperties: false\n      required: [decision, responseText]', 'additionalProperties: true\n      required: [decision, responseText]'),
    source.replace('APPROVE, REJECT, ANALYZE', 'APPROVE, REJECT, ANALYZE, DELETE_NOW'),
  ];
  for (const mutant of mutants) {
    assert.notEqual(mutant, source, 'privacy mutation must change real bytes');
    assert.throws(() => assertPrivacyContract(mutant));
  }
});

test('privacy official generated client has exact private types and restricted JSON export', async () => {
  assert.ok(PUBLIC_GENERATOR_API_TAGS.includes('PrivacyRights'));
  for (const model of Object.keys(PRIVACY_FIELDS)) assert.ok(PUBLIC_GENERATOR_MODELS.includes(model));
  const service = await readFile(join(projectRoot, 'frontend/src/app/api/generated/api/privacy-rights.service.ts'), 'utf8');
  for (const [path, method, id] of PRIVACY_OPERATIONS) {
    assert.ok(service.includes(`public ${id}(`));
    assert.ok(service.includes(`this.httpClient.request<`) && service.includes(`'${method}'`));
    assert.ok(service.includes(path.replace('{caseId}', '${this.configuration.encodeParam({name: "caseId", value: caseId, in: "path", style: "simple", explode: false, dataType: "string", dataFormat: "uuid"})}')));
  }
  assert.doesNotMatch(service, /http:\/\/localhost|Authorization|\.set\('Cookie'/u);
  const exported = await readFile(join(projectRoot, 'frontend/src/app/api/generated/model/privacy-export.ts'), 'utf8');
  assert.match(exported, /data: \{ \[key: string\]: Array<PrivacyRestrictedRecord>; \}/u);
  const request = await readFile(join(projectRoot, 'frontend/src/app/api/generated/model/privacy-request.ts'), 'utf8');
  assert.match(request, /subjectRef: string \| null/u);
  assert.match(request, /proofReference: string \| null/u);
  assert.doesNotMatch(request, /ownerIdentityId|clientCommandId|details/u);
});

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex').toUpperCase();
}

function assertChatReadContract(source) {
  const routes = [
    ['/api/v1/chat/events/{eventId}', 'getEventChat', false, false],
    ['/api/v1/chat/events/{eventId}/changes', 'getEventChatChanges', false, true],
    ['/api/v1/chat/global', 'getGlobalChat', true, false],
    ['/api/v1/chat/global/changes', 'getGlobalChatChanges', true, true],
  ];
  for (const [path, operationId, global, changes] of routes) {
    const block = source.split(`  ${path}:\n`)[1]?.split(/\n  \S/u)[0];
    assert.ok(block, `missing ${path}`);
    assert.deepEqual(
      [...block.matchAll(/^    (\w+):/gmu)].map((m) => m[1]),
      ['get'],
    );
    assert.match(block, /tags: \[Chat\]/u);
    assert.match(block, new RegExp(`operationId: ${operationId}\\b`, 'u'));
    assert.match(block, global ? /security: \[\{cookieSession: \[\]\}\]/u : /security: \[\]/u);
    assert.match(block, /maximum: 100, default: 50/u);
    assert.match(block, changes ? /name: changeCursor/u : /name: historyCursor/u);
    assert.match(block, /Cache-Control:/u);
    assert.match(block, global ? /description: private, no-store/u : /description: no-store/u);
    assert.match(block, /description: Cookie/u);
    assert.match(block, /#\/components\/schemas\/ChatReadResponse/u);
    if (!global) assert.match(block, /name: eventId[\s\S]*?required: true/u);
  }
  for (const [name, fields] of [
    [
      'ChatMessage',
      ['messageId', 'participantId', 'nickname', 'text', 'postedAt', 'removed', 'changeCursor'],
    ],
    [
      'ChatReadResponse',
      ['state', 'kind', 'eventId', 'messages', 'historyCursor', 'changeCursor', 'deadline'],
    ],
  ]) {
    const block = source.split(`    ${name}:\n`)[1]?.split(/\n    \S/u)[0];
    assert.ok(block, `missing ${name}`);
    assert.match(block, /additionalProperties: false/u);
    assert.deepEqual(
      [...block.matchAll(/^        (\w+):/gmu)].map((m) => m[1]),
      fields,
    );
  }
  assert.equal(inspectOpenApiContract(source).schemas.includes('Channel'), false);
}

test('official chat read contract preserves session boundary and minimal projections', async () => {
  const source = await readFile(join(projectRoot, 'backend/openapi/betufu.yaml'), 'utf8');
  assertChatReadContract(source);
  assert.throws(() =>
    assertChatReadContract(
      source.replace(
        /(operationId: getGlobalChat\n[\s\S]*?security: )\[\{cookieSession: \[\]\}\]/u,
        '$1[]',
      ),
    ),
  );
  assert.throws(() =>
    assertChatReadContract(source.replace('        nickname: {type:', '        email: {type:')),
  );
});

test('official chat client preserves four relative GET reads alongside authenticated writes', async () => {
  assert.ok(PUBLIC_GENERATOR_API_TAGS.includes('Chat'));
  assert.ok(PUBLIC_GENERATOR_MODELS.includes('ChatMessage'));
  assert.ok(PUBLIC_GENERATOR_MODELS.includes('ChatReadResponse'));
  const generated = join(projectRoot, 'frontend/src/app/api/generated');
  const service = await readFile(join(generated, 'api/chat.service.ts'), 'utf8');
  for (const operation of [
    'getEventChat',
    'getEventChatChanges',
    'getGlobalChat',
    'getGlobalChatChanges',
  ])
    assert.match(service, new RegExp(`public ${operation}\\(`, 'u'));
  const requests = [...service.matchAll(/request<ChatReadResponse>\('([^']+)'/gu)];
  assert.deepEqual(
    requests.map((m) => m[1]),
    ['get', 'get', 'get', 'get'],
  );
  for (const operation of ['getEventChat', 'getEventChatChanges', 'getGlobalChat', 'getGlobalChatChanges'])
    assert.match(generatedOperation(service, operation), /let localVarPath = `\/api\/v1\/chat\//u);
  assert.doesNotMatch(service, /lookupCredential|['"](?:Authorization|Cookie)['"]/u);
  const message = await readFile(join(generated, 'model/chat-message.ts'), 'utf8');
  assert.match(message, /text\?: string \| null/u);
  assert.match(message, /participantId\?: string \| null/u);
  assert.doesNotMatch(message, /\b(?:email|providerId|authorId|enrollment|originalText)\??:/u);
});

function assertChatWriteContract(source) {
  for (const [route, operation] of [
    ['/api/v1/chat/events/{eventId}/messages', 'sendEventChatMessage'],
    ['/api/v1/chat/global/messages', 'sendGlobalChatMessage'],
  ]) {
    const block = source.split(`  ${route}:\n`)[1]?.split(/\n  \S/u)[0];
    assert.ok(block, `missing ${route}`);
    assert.match(block, new RegExp(`operationId: ${operation}\\b`, 'u'));
    assert.match(block, /security: \[\{cookieSession: \[\]\}\]/u);
    assert.match(block, /name: Idempotency-Key\n\s+in: header\n\s+required: true/u);
    assert.match(block, /Canonical lowercase UUID/u);
    assert.match(block, /#\/components\/schemas\/ChatSendRequest/u);
    assert.match(block, /#\/components\/schemas\/ChatCommandReceipt/u);
    assert.match(block, /"201":[\s\S]*?Committed immutable receipt/u);
    for (const status of ['400', '401', '403', '404', '409', '429'])
      assert.match(block, new RegExp(`"${status}":`, 'u'));
    assert.match(block, /Retry-After:[\s\S]*?minimum: 1/u);
    assert.match(block, /private, no-store/u);
  }
  for (const [name, fields, required] of [
    ['ChatSendRequest', ['text'], '[text]'],
    [
      'ChatCommandReceipt',
      ['messageId', 'postedAt', 'changeCursor'],
      '[messageId, postedAt, changeCursor]',
    ],
  ]) {
    const block = source.split(`    ${name}:\n`)[1]?.split(/\n    \S/u)[0];
    assert.ok(block, `missing ${name}`);
    assert.match(block, /additionalProperties: false/u);
    assert.ok(block.includes(`required: ${required}`));
    assert.deepEqual(
      [...block.matchAll(/^        (\w+):/gmu)].map((match) => match[1]),
      fields,
    );
  }
  const problem = source.split('    BetufuProblemDetails:\n')[1]?.split(/\n    \S/u)[0];
  assert.match(problem, /retryAt:\n\s+type: string\n\s+format: date-time/u);
}

test('official chat write contract matches runtime header, receipt, retries and private-field rejection', async () => {
  const source = await readFile(join(projectRoot, 'backend/openapi/betufu.yaml'), 'utf8');
  assertChatWriteContract(source);
  const mutants = [
    source.replace(
      /(operationId: sendGlobalChatMessage\n[\s\S]*?security: )\[\{cookieSession: \[\]\}\]/u,
      '$1[]',
    ),
    source.replace(
      /(operationId: sendGlobalChatMessage\n[\s\S]*?- name: )Idempotency-Key/u,
      '$1clientCommandId',
    ),
    source.replace('      required: [text]', '      required: [text, actorId]'),
    source.replace(
      '        text: {type: string, minLength: 1, maxLength: 280}',
      '        actorId: {type: string}\n        text: {type: string, minLength: 1, maxLength: 280}',
    ),
    source.replace(
      '        postedAt: {type: string, format: date-time}',
      '        acceptedAt: {type: string, format: date-time}',
    ),
    source.replace('        retryAt:\n', '        internalReason:\n'),
  ];
  for (const mutant of mutants) {
    assert.notEqual(mutant, source, 'mutation must change the real contract');
    assert.throws(() => assertChatWriteContract(mutant));
  }
});

function schemaFields(schema) {
  const properties = schema.split('      properties:\n')[1] ?? '';
  return [...properties.matchAll(/^        (\w+):/gmu)].map((match) => match[1]);
}

function generatedFields(source) {
  return [...source.matchAll(/^    (\w+)\??:/gmu)].map((match) => match[1]);
}

function assertEmergencyPauseContract(source) {
  const routes = [
    ['/api/v1/operations/emergency-pauses', 'get', 'listEmergencyPauses', true],
    ['/api/v1/operations/emergency-pauses', 'post', 'commandEmergencyPause', true],
    ['/api/v1/chat/events/{eventId}/availability', 'get', 'getEventChatAvailability', false],
    ['/api/v1/chat/global/availability', 'get', 'getGlobalChatAvailability', true],
    [
      '/api/v1/me/predictions/markets/{marketId}/availability',
      'get',
      'getPredictionMarketAvailability',
      false,
    ],
  ];
  for (const [path, method, operationId, session] of routes) {
    const route = source.split(`  ${path}:\n`)[1]?.split(/\n  \S/u)[0];
    assert.ok(route, `missing ${path}`);
    const operation = route.split(`    ${method}:\n`)[1]?.split(/\n    (?:get|post):/u)[0] ?? route;
    assert.match(operation, new RegExp(`operationId: ${operationId}\\b`, 'u'));
    assert.match(operation, /tags: \[EmergencyPauses\]/u);
    assert.match(operation, session ? /security: \[\{cookieSession: \[\]\}\]/u : /security: \[\]/u);
    assert.match(operation, new RegExp('#/components/schemas/' + (operationId.endsWith('Availability') ? 'EmergencyPauseAvailability' : 'EmergencyPauseState') + '\\b', 'u'));
    assert.doesNotMatch(operation, /name: (?:actorId|enrollmentId|scopeRef)/u);
  }
  const command = source.split('    EmergencyPauseCommand:\n')[1]?.split(/\n    \S/u)[0];
  const state = source.split('    EmergencyPauseState:\n')[1]?.split(/\n    \S/u)[0];
  assert.ok(command, 'missing EmergencyPauseCommand');
  assert.ok(state, 'missing EmergencyPauseState');
  assert.match(command, /additionalProperties: false/u);
  assert.match(command, /required: \[clientCommandId, kind, targetId, action, reason\]/u);
  assert.match(command, /enum: \[ACCOUNT, EVENT, MARKET, CHANNEL\]/u);
  assert.match(command, /enum: \[PAUSE, RESUME\]/u);
  assert.doesNotMatch(command, /^\s+(?:actorId|enrollmentId|scopeRef|arbitrary):/mu);
  const availability = source.split('    EmergencyPauseAvailability:\n')[1]?.split(/\n    \S/u)[0];
  assert.ok(availability, 'missing EmergencyPauseAvailability');
  for (const schema of [state, availability]) {
    assert.match(schema, /additionalProperties: false/u);
    assert.doesNotMatch(schema, /^\s+(?:view|status|actorId|enrollmentId|issuer|subject|matricula|internalEvidence):/mu);
  }
  assert.match(availability, /required: \[state, revision\]/u);
  assert.match(availability, /revision: \{type: \[string, "null"\], format: uuid\}/u);
  assert.deepEqual(schemaFields(availability), ['state', 'messagesPaused', 'predictionsPaused', 'revision']);
  assert.match(state, /required: \[factId, kind, targetId, action, reason, resumedPauseId, recordedAt\]/u);
  assert.deepEqual(schemaFields(state), ['factId', 'kind', 'targetId', 'action', 'reason', 'resumedPauseId', 'recordedAt']);
}

test('emergency pause contract separates admin commands from minimal public availability', async () => {
  const source = await readFile(join(projectRoot, 'backend/openapi/betufu.yaml'), 'utf8');
  assertEmergencyPauseContract(source);
  const mutations = [
    source.replace(
      /(operationId: getGlobalChatAvailability\n[\s\S]*?security: )\[\{cookieSession: \[\]\}\]/u,
      '$1[]',
    ),
    source.replace(
      'required: [clientCommandId, kind, targetId, action, reason]',
      'required: [clientCommandId, kind, targetId, action, reason, actorId]',
    ),
    source.replace(
      '        messagesPaused: {type: boolean}',
      '        actorId: {type: string}\n        messagesPaused: {type: boolean}',
    ),
    source.replace(
      '/api/v1/me/predictions/markets/{marketId}/availability',
      '/api/v1/predictions/markets/{marketId}/availability',
    ),
  ];
  for (const mutant of mutations) {
    assert.notEqual(mutant, source, 'availability mutation must change the real contract');
    assert.throws(() => assertEmergencyPauseContract(mutant));
  }
});

test('generated emergency pause client uses the producer routes and closed DTOs', async () => {
  assert.ok(PUBLIC_GENERATOR_API_TAGS.includes('EmergencyPauses'));
  assert.ok(PUBLIC_GENERATOR_MODELS.includes('EmergencyPauseCommand'));
  assert.ok(PUBLIC_GENERATOR_MODELS.includes('EmergencyPauseState'));
  assert.ok(PUBLIC_GENERATOR_MODELS.includes('EmergencyPauseAvailability'));
  const generated = join(projectRoot, 'frontend/src/app/api/generated');
  const service = await readFile(join(generated, 'api/emergency-pauses.service.ts'), 'utf8');
  for (const [operation, verb, response] of [
    ['listEmergencyPauses', 'get', 'Array<EmergencyPauseState>'],
    ['commandEmergencyPause', 'post', 'EmergencyPauseState'],
    ['getEventChatAvailability', 'get', 'EmergencyPauseAvailability'],
    ['getGlobalChatAvailability', 'get', 'EmergencyPauseAvailability'],
    ['getPredictionMarketAvailability', 'get', 'EmergencyPauseAvailability'],
  ]) {
    const op = generatedOperation(service, operation);
    assert.match(op, new RegExp(`request<${response}>\\('${verb}'`, 'u'));
    assert.doesNotMatch(op, /set\('(?:Authorization|Cookie)'/u);
  }
  assert.match(generatedOperation(service, 'getEventChatAvailability'), /api\/v1\/chat\/events\//u);
  assert.match(generatedOperation(service, 'getGlobalChatAvailability'), /api\/v1\/chat\/global\/availability/u);
  assert.match(generatedOperation(service, 'getPredictionMarketAvailability'), /api\/v1\/me\/predictions\/markets/u);
  const command = await readFile(join(generated, 'model/emergency-pause-command.ts'), 'utf8');
  const state = await readFile(join(generated, 'model/emergency-pause-state.ts'), 'utf8');
  assert.deepEqual(
    [...command.matchAll(/^    (\w+)\??:/gmu)].map((match) => match[1]),
    ['clientCommandId', 'kind', 'targetId', 'action', 'reason', 'resumedPauseId'],
  );
  const availability = await readFile(join(generated, 'model/emergency-pause-availability.ts'), 'utf8');
  assert.deepEqual(generatedFields(state), ['factId', 'kind', 'targetId', 'action', 'reason', 'resumedPauseId', 'recordedAt']);
  assert.deepEqual(generatedFields(availability), ['state', 'messagesPaused', 'predictionsPaused', 'revision']);
  assert.match(availability, /revision: string \| null/u);
  assert.doesNotMatch(availability, /\b(?:reason|targetId|factId|actorId)\??:/u);
});

test('emergency pause response fields match the actual Java producer records', async () => {
  const read = (path) => readFile(join(projectRoot, path), 'utf8');
  const [contract, chat, predictions, operations] = await Promise.all([
    read('backend/openapi/betufu.yaml'),
    read('backend/src/main/java/br/com/betufu/chat/internal/ChatReadModels.java'),
    read('backend/src/main/java/br/com/betufu/betting/internal/PredictionController.java'),
    read('backend/src/main/java/br/com/betufu/platform/operations/EmergencyPausePort.java'),
  ]);
  const fields = (source, record) => {
    const body = source.match(new RegExp(`record ${record}\\(([^)]+)\\)`, 'u'))?.[1];
    assert.ok(body, `missing producer record ${record}`);
    return body.split(',').map((field) => field.trim().split(/\s+/u).at(-1));
  };
  const availability = contract.split('    EmergencyPauseAvailability:\n')[1]?.split(/\n    \S/u)[0];
  const state = contract.split('    EmergencyPauseState:\n')[1]?.split(/\n    \S/u)[0];
  const availabilityFields = new Set([
    ...fields(chat, 'Availability'),
    ...fields(predictions, 'Availability'),
  ]);
  assert.deepEqual([...availabilityFields].sort(), schemaFields(availability).sort());
  assert.deepEqual(schemaFields(state), fields(operations, 'Receipt'));
});

test('official generated chat write client sends two relative POSTs and exposes exact public acknowledgements', async () => {
  const generated = join(projectRoot, 'frontend/src/app/api/generated');
  const service = await readFile(join(generated, 'api/chat.service.ts'), 'utf8');
  for (const operation of ['sendEventChatMessage', 'sendGlobalChatMessage'])
    assert.match(service, new RegExp(`public ${operation}\\(`, 'u'));
  assert.deepEqual(
    [...service.matchAll(/request<ChatCommandReceipt>\('([^']+)'/gu)].map((match) => match[1]),
    ['post', 'post'],
  );
  for (const name of ['sendEventChatMessage', 'sendGlobalChatMessage'])
    assert.match(generatedOperation(service, name), /localVarHeaders\.set\('Idempotency-Key', String\(idempotencyKey\)\)/u);
  assert.doesNotMatch(service, /lookupCredential|['"](?:Authorization|Cookie)['"]/u);
  const request = await readFile(join(generated, 'model/chat-send-request.ts'), 'utf8');
  const receipt = await readFile(join(generated, 'model/chat-command-receipt.ts'), 'utf8');
  assert.deepEqual(
    [...request.matchAll(/^    (\w+)\??:/gmu)].map((match) => match[1]),
    ['text'],
  );
  assert.deepEqual(
    [...receipt.matchAll(/^    (\w+)\??:/gmu)].map((match) => match[1]),
    ['messageId', 'postedAt', 'changeCursor'],
  );
  assert.match(
    await readFile(join(generated, 'model/betufu-problem-details.ts'), 'utf8'),
    /retryAt\?: string/u,
  );
  assert.ok(PUBLIC_GENERATOR_MODELS.includes('ChatSendRequest'));
  assert.ok(PUBLIC_GENERATOR_MODELS.includes('ChatCommandReceipt'));
});

function generatedOperation(service, name) {
  const start = service.indexOf(`    public ${name}(`);
  assert.notEqual(start, -1, `missing generated ${name}`);
  const end = service.indexOf('\n    /**', start);
  return service.slice(start, end < 0 ? service.length : end);
}

function assertChatCommunityContract(source) {
  const report = source.split('  /api/v1/chat/messages/{messageId}/reports:\n')[1]?.split(/\n  \S/u)[0];
  assert.ok(report, 'missing real message report route');
  assert.match(report, /operationId: reportChatMessage/u);
  assert.match(report, /security: \[\{cookieSession: \[\]\}\]/u);
  assert.match(report, /name: Idempotency-Key\n\s+in: header\n\s+required: true/u);
  assert.match(report, /#\/components\/schemas\/ChatReportCommand/u);
  assert.match(report, /#\/components\/schemas\/ChatReportReceipt/u);
  assert.match(report, /"201":/u);
  assert.doesNotMatch(report, /#\/components\/schemas\/ChatCommandReceipt/u);
  const mutes = source.split('  /api/v1/chat/me/mutes:\n')[1]?.split(/\n  \S/u)[0];
  assert.ok(mutes, 'missing own account mute list');
  assert.match(mutes, /security: \[\{cookieSession: \[\]\}\]/u);
  assert.match(mutes, /private, no-store/u);
  const target = source.split('  /api/v1/chat/me/mutes/{participantId}:\n')[1]?.split(/\n  \S/u)[0];
  assert.ok(target, 'missing own account mute command route');
  for (const name of ['getAccountChatMute', 'setAccountChatMute', 'removeAccountChatMute'])
    assert.match(target, new RegExp(`operationId: ${name}\\b`, 'u'));
  assert.equal((target.match(/security: \[\{cookieSession: \[\]\}\]/gu) ?? []).length, 3);
  assert.match(target, /name: If-Match\n\s+in: header\n\s+required: true/u);
  assert.match(target, /Quoted canonical account version from ETag/u);
  assert.match(target, /ETag:/u);
  assert.match(target, /#\/components\/schemas\/ChatMuteCommand/u);
  assert.match(target, /#\/components\/schemas\/ChatMuteState/u);
  for (const [name, fields] of [
    ['ChatReportCommand', ['reason']],
    ['ChatReportReceipt', ['reportId', 'reportedAt']],
    ['ChatMuteCommand', ['expectedVersion']],
    ['ChatMuteState', ['version', 'participantIds']],
  ]) {
    const block = source.split(`    ${name}:\n`)[1]?.split(/\n    \S/u)[0];
    assert.ok(block, `missing ${name}`);
    assert.match(block, /additionalProperties: false/u);
    assert.ok(block.includes(`required: [${fields.join(', ')}]`));
    assert.deepEqual([...block.matchAll(/^        (\w+):/gmu)].map(match => match[1]), fields);
  }
  const command = source.split('    ChatReportCommand:\n')[1]?.split(/\n    \S/u)[0];
  assert.match(command, /enum: \[DISCURSO_DE_ODIO, SPAM, OFENSA, ASSEDIO, FRAUDE\]/u);
}

test('community contract preserves private reports, canonical keys and account version conflicts', async () => {
  const source = await readFile(join(projectRoot, 'backend/openapi/betufu.yaml'), 'utf8');
  assertChatCommunityContract(source);
  for (const mutant of [
    source.replace(/(operationId: reportChatMessage\n[\s\S]*?security: )\[\{cookieSession: \[\]\}\]/u, '$1[]'),
    source.replace(/(operationId: reportChatMessage\n[\s\S]*?- name: )Idempotency-Key/u, '$1actorId'),
    source.replace('enum: [DISCURSO_DE_ODIO, SPAM, OFENSA, ASSEDIO, FRAUDE]', 'enum: [DISCURSO_DE_ODIO, SPAM, OFENSA, ASSEDIO, FRAUDE, OTHER]'),
    source.replace('      required: [reportId, reportedAt]', '      required: [reportId, reportedAt, reporterId]'),
    source.replace('        expectedVersion: {type: integer, format: int64, minimum: 0}', '        ownerId: {type: string}\n        expectedVersion: {type: integer, format: int64, minimum: 0}'),
    source.replace('        - name: If-Match', '        - name: clientVersion'),
    source.replace('  /api/v1/chat/me/mutes:', '  /api/v1/chat/accounts/{ownerId}/mutes:'),
  ]) {
    assert.notEqual(mutant, source, 'community mutation must change real source');
    assert.throws(() => assertChatCommunityContract(mutant));
  }
});

test('generated community commands use private relative routes and exact runtime headers', async () => {
  const service = await readFile(join(projectRoot, 'frontend/src/app/api/generated/api/chat.service.ts'), 'utf8');
  assert.match(generatedOperation(service, 'reportChatMessage'), /request<ChatReportReceipt>\('post'/u);
  assert.match(generatedOperation(service, 'reportChatMessage'), /localVarHeaders\.set\('Idempotency-Key', String\(idempotencyKey\)\)/u);
  assert.match(generatedOperation(service, 'getAccountChatMutes'), /request<ChatMuteState>\('get'/u);
  assert.match(generatedOperation(service, 'setAccountChatMute'), /request<ChatMuteState>\('put'/u);
  assert.match(generatedOperation(service, 'removeAccountChatMute'), /request<ChatMuteState>\('delete'/u);
  assert.match(generatedOperation(service, 'removeAccountChatMute'), /localVarHeaders\.set\('If-Match', String\(ifMatch\)\)/u);
  assert.doesNotMatch(service, /lookupCredential|['"](?:Authorization|Cookie)['"]/u);
  assert.doesNotMatch(service, /api\/v1\/chat\/accounts\//u);
});

test('generated community models contain no reporter, original text or supplied owner', async () => {
  const root = join(projectRoot, 'frontend/src/app/api/generated/model');
  for (const [file, model, fields] of [
    ['chat-report-command.ts', 'ChatReportCommand', ['reason']],
    ['chat-report-receipt.ts', 'ChatReportReceipt', ['reportId', 'reportedAt']],
    ['chat-mute-command.ts', 'ChatMuteCommand', ['expectedVersion']],
    ['chat-mute-state.ts', 'ChatMuteState', ['version', 'participantIds']],
  ]) {
    const source = await readFile(join(root, file), 'utf8');
    assert.deepEqual([...source.matchAll(/^    (\w+)\??:/gmu)].map(match => match[1]), fields);
    assert.ok(PUBLIC_GENERATOR_MODELS.includes(model));
  }
  assert.match(await readFile(join(root, 'chat-report-command.ts'), 'utf8'), /DISCURSO_DE_ODIO/u);
});

const MODERATION_SLICE = 4;
const moderationModels = [
  ['ChatModerationCommand', 'Command', ['clientCommandId', 'reason'], 1],
  ['ChatModerationCase', 'CaseSummary', ['caseId', 'messageId', 'eventId', 'competitionId', 'status', 'openedAt'], 1],
  ['ChatModerationCaseReceipt', 'CaseReceipt', ['caseId', 'status', 'recordedAt'], 1],
  ['ChatModerationOpenCommand', null, ['reportId'], 1],
  ['ChatModerationReportEvidence', 'ReportEvidence', ['reportId', 'reason', 'reportedAt'], 2],
  ['ChatModerationEvidence', 'ModerationEvidence', ['caseId', 'messageId', 'originalBody', 'reports'], 2],
  ['ChatModerationForwardReceipt', 'ForwardReceipt', ['caseId', 'forwardedAt'], 2],
  ['ChatSanctionCommand', 'SanctionCommand', ['clientCommandId', 'reason', 'kind'], 3],
  ['ChatSanctionSummary', 'SanctionSummary', ['sanctionId', 'kind', 'reason', 'effectiveFrom', 'effectiveUntil', 'active', 'reviewStatus'], 3],
  ['ChatSanctionSuggestion', 'SanctionSuggestion', ['caseId', 'suggestedKind'], 3],
  ['ChatReviewCommand', 'ReviewCommand', ['clientCommandId', 'reason', 'decision'], 4],
  ['ChatReviewReceipt', 'ReviewReceipt', ['reviewId', 'sanctionId', 'status', 'reason', 'recordedAt'], 4],
];
const moderationOperations = [
  ['/api/v1/chat/moderation/cases', 'get', 'getChatModerationCases', 'ChatModerationCase', null, 200, 1],
  ['/api/v1/chat/moderation/cases', 'post', 'openChatModerationCase', 'ChatModerationCaseReceipt', 'ChatModerationOpenCommand', 201, 1],
  ['/api/v1/chat/moderation/cases/{caseId}/remove', 'post', 'removeChatModerationMessage', 'ChatModerationCaseReceipt', 'ChatModerationCommand', 200, 1],
  ['/api/v1/chat/moderation/cases/{caseId}/forward', 'post', 'forwardChatModerationCase', 'ChatModerationForwardReceipt', 'ChatModerationCommand', 200, 2],
  ['/api/v1/chat/moderation/cases/{caseId}/evidence', 'get', 'getChatModerationEvidence', 'ChatModerationEvidence', null, 200, 2],
  ['/api/v1/chat/me/sanctions', 'get', 'getOwnChatSanctions', 'ChatSanctionSummary', null, 200, 3],
  ['/api/v1/chat/moderation/cases/{caseId}/sanctions/suggestion', 'get', 'suggestChatSanction', 'ChatSanctionSuggestion', null, 200, 3],
  ['/api/v1/chat/moderation/cases/{caseId}/sanctions', 'post', 'confirmChatSanction', 'ChatSanctionSummary', 'ChatSanctionCommand', 201, 3],
  ['/api/v1/chat/me/sanctions/{sanctionId}/reviews', 'post', 'requestChatSanctionReview', 'ChatReviewReceipt', 'ChatModerationCommand', 201, 4],
  ['/api/v1/chat/moderation/reviews', 'get', 'getPendingChatReviews', 'ChatReviewReceipt', null, 200, 4],
  ['/api/v1/chat/moderation/reviews/{reviewId}/decisions', 'post', 'decideChatSanctionReview', 'ChatReviewReceipt', 'ChatReviewCommand', 200, 4],
];

function assertModerationContract(source) {
  for (const [path, method, id, response, request, status, slice] of moderationOperations) {
    if (slice > MODERATION_SLICE) continue;
    const route = source.split(`  ${path}:\n`)[1]?.split(/\n  \S/u)[0];
    assert.ok(route, `missing ${path}`);
    const op = route.split(`    ${method}:\n`)[1]?.split(/\n    (?:get|post):/u)[0];
    assert.ok(op, `missing ${method} ${path}`);
    assert.match(op, new RegExp(`operationId: ${id}\\b`, 'u'));
    assert.match(op, /security: \[\{cookieSession: \[\]\}\]/u);
    assert.match(op, /private, no-store/u);
    assert.match(op, new RegExp(`"${status}":[\\s\\S]*?application/json:[\\s\\S]*?#/components/schemas/${response}\\b`, 'u'));
    if (request) assert.match(op, new RegExp(`requestBody:[\\s\\S]*?required: true[\\s\\S]*?#/components/schemas/${request}\\b`, 'u'));
    for (const code of [400, 401, 403, 404, 409]) assert.match(op, new RegExp(`"${code}":`, 'u'));
    assert.doesNotMatch(op, /ChatCommandReceipt|text\/plain|name: (?:actorId|ownerId|scopeRef)/u);
    if (id === 'getChatModerationEvidence') {
      assert.match(op, /name: reason\n\s+in: query\n\s+required: true/u);
      assert.match(op, /appends an audit fact/u);
    }
    if (['suggestChatSanction', 'confirmChatSanction', 'getPendingChatReviews', 'decideChatSanctionReview'].includes(id))
      assert.match(op, /summary: General administrator exclusively/u);
  }
  for (const [name, , fields, slice] of moderationModels) {
    if (slice > MODERATION_SLICE) continue;
    const model = source.split(`    ${name}:\n`)[1]?.split(/\n    \S/u)[0];
    assert.ok(model, `missing ${name}`);
    assert.match(model, /additionalProperties: false/u);
    assert.deepEqual([...model.matchAll(/^        (\w+):/gmu)].map(m => m[1]), fields);
    assert.match(model, new RegExp(`required: \\[${fields.join(', ')}\\]`, 'u'));
    assert.doesNotMatch(model, /reporterId|actorId|issuer|subject|email|hmac|scopeRef/u);
    if (name !== 'ChatModerationEvidence') assert.doesNotMatch(model, /originalBody/u);
    if (fields.includes('clientCommandId')) assert.match(model, /pattern: '\^\[0-9a-f\]/u);
    if (fields.includes('reason') && name !== 'ChatModerationReportEvidence') assert.match(model, /minLength: 1, maxLength: 280/u);
  }
  assert.doesNotMatch(source, /\/chat\/moderation\/messages\/\{.*\}\/removals|\/chat\/moderation\/cases\/\{.*\}\/escalations/u);
  if (MODERATION_SLICE >= 3) {
    assert.match(source, /effectiveUntil: \{type: \[string, "null"\], format: date-time\}/u);
    for (const name of ['ChatSanctionCommand', 'ChatSanctionSummary', 'ChatSanctionSuggestion'])
      assert.match(source.split(`    ${name}:\n`)[1]?.split(/\n    \S/u)[0], /CHAT_SUSPENSION_24H, CHAT_SUSPENSION_7D, CHAT_BAN/u);
  }
  if (MODERATION_SLICE >= 4) assert.match(source.split('    ChatReviewCommand:\n')[1]?.split(/\n    \S/u)[0], /enum: \[MAINTAIN, ANNUL\]/u);
}

test('moderation contract follows certified routes, distinct closed DTOs and private authority', async () => {
  const source = (await readFile(join(projectRoot, 'backend/openapi/betufu.yaml'), 'utf8')).replaceAll('\r\n', '\n');
  assertModerationContract(source);
  const mutations = [
    source.replace('operationId: openChatModerationCase', 'operationId: obsoleteAlias'),
    source.replace(/(operationId: getChatModerationCases\n[\s\S]*?security: )\[\{cookieSession: \[\]\}\]/u, '$1[]'),
    source.replace('      required: [reportId]\n', '      required: [reportId, actorId]\n'),
    source.replace('schema: {$ref: "#/components/schemas/ChatModerationCaseReceipt"}', 'schema: {$ref: "#/components/schemas/ChatCommandReceipt"}'),
    source.replace('        competitionId: {type:', '        reporterId: {type:'),
  ];
  if (MODERATION_SLICE >= 2) mutations.push(source.replace(/(name: reason\n\s+in: query\n\s+required: )true/u, '$1false'));
  if (MODERATION_SLICE >= 3) mutations.push(source.replace('General administrator exclusively confirms', 'Any moderator confirms'));
  if (MODERATION_SLICE >= 4) mutations.push(source.replace('enum: [MAINTAIN, ANNUL]', 'enum: [MAINTAIN, ANNUL, IGNORE]'));
  for (const mutant of mutations) {
    assert.notEqual(mutant, source, 'mutation must actually change the contract');
    assert.throws(() => assertModerationContract(mutant));
  }
});

test('moderation generated models match Java record fields and omit reporter identities', async () => {
  const java = await readFile(join(projectRoot, 'backend/src/main/java/br/com/betufu/chat/internal/ChatModerationModels.java'), 'utf8');
  for (const [name, record, fields, slice] of moderationModels) {
    if (slice > MODERATION_SLICE) continue;
    const file = name.replace(/([a-z])([A-Z])/gu, '$1-$2').toLowerCase() + '.ts';
    const model = await readFile(join(projectRoot, 'frontend/src/app/api/generated/model', file), 'utf8');
    assert.deepEqual([...model.matchAll(/^    (\w+)\??:/gmu)].map(m => m[1]), fields);
    assert.ok(PUBLIC_GENERATOR_MODELS.includes(name));
    if (record) {
      const signature = java.match(new RegExp(`record ${record}\\(([\\s\\S]*?)\\)`, 'u'))?.[1];
      assert.ok(signature, `missing producer record ${record}`);
      assert.deepEqual(signature.split(',').map(field => field.trim().split(/\s+/u).at(-1)), fields);
    }
  }
});

test('moderation generated service preserves request types, response types and mandatory evidence reason', async () => {
  const service = await readFile(join(projectRoot, 'frontend/src/app/api/generated/api/chat.service.ts'), 'utf8');
  for (const [path, method, id, response, request, , slice] of moderationOperations) {
    if (slice > MODERATION_SLICE) continue;
    const op = generatedOperation(service, id);
    const type = ['getChatModerationCases', 'getOwnChatSanctions', 'getPendingChatReviews'].includes(id) ? `Array<${response}>` : response;
    assert.ok(op.includes(`request<${type}>('${method}'`), id);
    const generatedPath = op.match(/localVarPath = `([^`]+)`/u)?.[1]
      ?.replace(/\$\{this\.configuration\.encodeParam\(\{name: "(\w+)"[^}]+\}\)\}/gu, '{$1}');
    assert.equal(generatedPath, path, id);
    if (request) assert.match(op, new RegExp(`body: ${request.charAt(0).toLowerCase() + request.slice(1)}\\b`, 'u'));
    if (id === 'getChatModerationEvidence') {
      assert.match(op, /reason === null \|\| reason === undefined/u);
      assert.match(op, /localVarQueryParameters.*|addToHttpParams/u);
      assert.match(op, /'reason'/u);
    }
    assert.doesNotMatch(op, /set\('(?:Authorization|Cookie)'/u);
  }
});

function testContract(engineBytes) {
  return Object.freeze({
    version: '7.24.0',
    path: 'tmp/openapi-generator/7.24.0/openapi-generator-cli-7.24.0.jar',
    size: engineBytes.length,
    sha256: sha256(engineBytes),
  });
}

function configuration(contract) {
  return {
    engine: { ...contract },
    generator: {
      name: 'typescript-angular',
      inputSpec: '../backend/openapi/betufu.yaml',
      target: 'src/app/api/generated',
      additionalProperties: {
        ngVersion: '22.1.0',
        tsVersion: '>=6.0.0 <6.1.0',
        rxjsVersion: '7.8.2',
        ngPackagrVersion: '22.1.0',
        zonejsVersion: '0.15.0',
        supportsES6: true,
        ngVersion21: true,
        providedIn: 'root',
        useSingleRequestParameter: true,
        fileNaming: 'kebab-case',
        modelPropertyNaming: 'original',
        disallowAdditionalPropertiesIfNotPresent: false,
        hideGenerationTimestamp: true,
      },
    },
  };
}

async function writeText(path, contents) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, contents, 'utf8');
}

async function writeGeneratedBaseService(outputDirectory, contents = RAW_BASE_SERVICE) {
  await writeText(join(outputDirectory, 'api.base.service.ts'), contents);
}

async function writeLock(workspace, owner) {
  const lockPath = join(workspace, 'pipeline.lock');
  await mkdir(lockPath, { recursive: true });
  await writeText(join(lockPath, 'owner.json'), `${JSON.stringify(owner)}\n`);
  return lockPath;
}

async function createFixture(
  label,
  { targetFiles = { 'legacy.ts': 'export const legacy = true;\n' } } = {},
) {
  const root = await mkdtemp(join(tmpdir(), `betufu-generate-api-${label}-`));
  const engineBytes = Buffer.from(`engine-${label}`, 'utf8');
  const contract = testContract(engineBytes);
  const frontendRoot = join(root, 'frontend');
  const target = join(frontendRoot, 'src', 'app', 'api', 'generated');
  const workspace = join(frontendRoot, 'tmp', 'openapi-generator');
  const enginePath = join(frontendRoot, ...contract.path.split('/'));

  await writeText(
    join(frontendRoot, 'openapitools.json'),
    `${JSON.stringify(configuration(contract), null, 2)}\n`,
  );
  await writeText(
    join(root, 'backend', 'openapi', 'betufu.yaml'),
    'openapi: 3.1.0\npaths:\n  /api/v1/tracer/identity/current:\n    put:\n      operationId: putCurrentIdentity\n',
  );
  await mkdir(dirname(enginePath), { recursive: true });
  await writeFile(enginePath, engineBytes);

  for (const [relativePath, contents] of Object.entries(targetFiles)) {
    await writeText(join(target, ...relativePath.split('/')), contents);
  }

  return { root, frontendRoot, target, workspace, enginePath, engineBytes, contract };
}

async function withFixture(label, callback, options) {
  const fixture = await createFixture(label, options);
  try {
    return await callback(fixture);
  } finally {
    await rm(fixture.root, { recursive: true, force: true });
  }
}

function adapters(overrides = {}) {
  return {
    generate: async ({ outputDirectory }) => {
      await writeGeneratedBaseService(outputDirectory);
      await writeText(
        join(outputDirectory, 'api', 'identity.service.ts'),
        'export const line = 1;\r\n',
      );
      await writeText(
        join(outputDirectory, 'model', 'current-identity-response.ts'),
        'export interface CurrentIdentityResponse {}',
      );
      await writeText(join(outputDirectory, 'README.md'), 'raw auxiliary output\n');
      await writeText(
        join(outputDirectory, 'package.json'),
        '{"scripts":{"prepare":"forbidden"}}\n',
      );
    },
    processStatus: async () => 'dead',
    volumeOf: () => 'fixture-volume',
    now: () => FIXED_NOW,
    nonce: () => FIXED_NONCE,
    pid: 4242,
    ...overrides,
  };
}

async function execute(fixture, options = {}) {
  return executeGenerateApi({
    projectRoot: fixture.root,
    contract: fixture.contract,
    adapters: adapters(options.adapters),
    generatedManifestContract: options.generatedManifestContract ?? null,
    mode: options.mode ?? 'generate',
    candidateDirectory: options.candidateDirectory ?? null,
    candidateSource: options.candidateSource ?? null,
    candidateApproval: options.candidateApproval ?? null,
  });
}

async function candidateFixture(fixture) {
  const phase = '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto';
  const directory = `${phase}/07-06-candidate`;
  const sourcePath = `${directory}/betufu.yaml`;
  const approvalPath = 'docs/phase7-approvals/07-06.json';
  const generatedPaths = ['api/candidate-chat.service.ts', 'model/candidate-chat-message.ts'].map(
    (path) => `frontend/src/app/api/generated/${path}`,
  );
  const metadata = [
    'inventory.json',
    'digest.sha256',
    'governed-before.sha256',
    'governed-after.sha256',
  ].map((path) => `${directory}/${path}`);
  const allPaths = [
    approvalPath,
    sourcePath,
    ...metadata,
    ...generatedPaths,
    'backend/openapi/betufu.yaml',
  ];
  const plan = `---\nplan: '06'\nfiles_modified:\n${allPaths.map((path) => `  - ${path}\n`).join('')}---\n<task type="auto"><files>${[sourcePath, ...metadata].join(', ')}</files><verify><automated>node scripts/validate-phase7-approvals.mjs --plan 07-06 --stage prepare-candidates</automated></verify></task>`;
  const source =
    'openapi: 3.1.0\ninfo: {title: Candidate, version: 1.0.0}\npaths:\n  /api/v1/chat/global:\n    get:\n      tags: [CandidateChat]\n      operationId: getGlobalChat\n      responses:\n        "200": {description: ok}\ncomponents:\n  schemas:\n    CandidateChatMessage:\n      type: object\n';
  await writeText(join(fixture.root, sourcePath), source);
  await writeText(join(fixture.root, phase, '07-06-PLAN.md'), plan);
  const decisions = JSON.stringify({ schema_version: 1, plan_gates: { '07-06': [] } });
  await writeText(join(fixture.root, 'docs/phase7-decisions.json'), decisions);
  const baselinePaths = [
    'frontend/openapitools.json',
    'backend/openapi/betufu.yaml',
    `frontend/${fixture.contract.path}`,
    ...(await readTreeManifest(fixture.target)).files.map(
      (path) => `frontend/src/app/api/generated/${path}`,
    ),
  ];
  const approval = {
    schema_version: 1,
    plan: '07-06',
    status: 'approved',
    authorized_stage: 'prepare-candidates',
    head: 'a'.repeat(40),
    human_approval: {
      source: 'owner',
      status: 'approved',
      reference: 'synthetic-unit-test-only',
      statement:
        'Synthetic record confined to this temporary test directory, never a real authorization.',
      plans: ['07-06'],
    },
    plan_sha256: sha256(plan).toLowerCase(),
    decisions_sha256: sha256(decisions).toLowerCase(),
    scope: phase7PlanContract(plan, '07-06', 'prepare-candidates'),
    baseline_hashes: Object.fromEntries(
      await Promise.all(
        baselinePaths.map(async (path) => [
          path,
          sha256(await readFile(join(fixture.root, path))).toLowerCase(),
        ]),
      ),
    ),
    candidate_hashes: {},
    candidate_scope: {
      source_sha256: sha256(source),
      tags: ['CandidateChat'],
      models: ['CandidateChatMessage'],
      generated_paths: generatedPaths,
      deleted_paths: [],
    },
  };
  await writeText(join(fixture.root, approvalPath), JSON.stringify(approval));
  return {
    directory,
    sourcePath,
    approvalPath,
    approval,
    options: {
      mode: 'candidate',
      candidateDirectory: directory,
      candidateSource: sourcePath,
      candidateApproval: approvalPath,
      adapters: {
        currentHead: 'a'.repeat(40),
        generate: async ({ outputDirectory }) => {
          for (const entry of (await readTreeManifest(fixture.target)).entries)
            await writeText(join(outputDirectory, entry.path), entry.bytes);
          await writeText(join(outputDirectory, 'api.base.service.ts'), RAW_BASE_SERVICE);
          await writeText(
            join(outputDirectory, 'api/candidate-chat.service.ts'),
            'export class ChatService {}\r\n',
          );
          await writeText(
            join(outputDirectory, 'model/candidate-chat-message.ts'),
            'export interface CandidateChatMessage {}\r\n',
          );
        },
      },
    },
  };
}

test('candidate source drives generation and persists complete bytes without deleting its YAML', async () => {
  await withFixture('phase7-candidate', async (fixture) => {
    await execute(fixture);
    const f = await candidateFixture(fixture);
    const source = await readFile(join(fixture.root, f.sourcePath));
    const before = await snapshotTree(fixture.target);
    let seen;
    const original = f.options.adapters.generate;
    f.options.adapters.generate = async (args) => {
      seen = await readFile(args.inputSpec);
      await original(args);
    };
    const result = await execute(fixture, f.options);
    assert.deepEqual(seen, source);
    assert.deepEqual(await readFile(join(fixture.root, f.sourcePath)), source);
    assert.equal(await snapshotTree(fixture.target), before);
    const inventory = JSON.parse(await readFile(join(fixture.root, f.directory, 'inventory.json')));
    assert.equal(inventory.source.sha256, sha256(source));
    assert.equal(inventory.digest, result.digest);
    const changed = inventory.changes.filter(
      (entry) =>
        entry.status !== 'unchanged' && entry.path.startsWith('frontend/src/app/api/generated/'),
    );
    assert.deepEqual(
      changed.map((entry) => entry.path).sort(),
      f.approval.candidate_scope.generated_paths.sort(),
    );
    for (const entry of changed) {
      assert.equal(entry.status, 'added');
      assert.equal(sha256(entry.after_content), entry.after_sha256);
      assert.equal(entry.after_content.includes('\r'), false);
    }
  });
});

for (const [label, mutation] of [
  [
    'pending approval',
    (f) => {
      f.approval.status = 'pending';
    },
  ],
  [
    'wrong source hash',
    (f) => {
      f.approval.candidate_scope.source_sha256 = '0'.repeat(64);
    },
  ],
  [
    'extra scope',
    (f) => {
      f.approval.candidate_scope.generated_paths.push(
        'frontend/src/app/api/generated/unapproved.ts',
      );
    },
  ],
  [
    'missing changed path',
    (f) => {
      f.approval.candidate_scope.generated_paths.pop();
    },
  ],
  [
    'unapproved model',
    (f) => {
      f.approval.candidate_scope.models = ['NotInSource'];
    },
  ],
  [
    'unapproved tag',
    (f) => {
      f.approval.candidate_scope.tags = ['NotInSource'];
    },
  ],
  [
    'fixture approval',
    (f) => {
      f.approval.fixture = true;
    },
  ],
  [
    'stale HEAD',
    (f) => {
      f.approval.head = 'b'.repeat(40);
    },
  ],
  [
    'incomplete governed baseline',
    (f) => {
      delete f.approval.baseline_hashes['backend/openapi/betufu.yaml'];
    },
  ],
])
  test(`candidate source rejects ${label}`, async () => {
    await withFixture(`candidate-${label.replaceAll(' ', '-')}`, async (fixture) => {
      await execute(fixture);
      const f = await candidateFixture(fixture);
      const before = await snapshotTree(fixture.target);
      mutation(f);
      await writeText(join(fixture.root, f.approvalPath), JSON.stringify(f.approval));
      await assert.rejects(
        execute(fixture, f.options),
        (error) => error instanceof GenerateApiContractError,
      );
      assert.equal(await snapshotTree(fixture.target), before);
    });
  });

test('candidate flags are rejected outside candidate mode', async () => {
  await withFixture('candidate-mode-guard', async (fixture) => {
    for (const mode of ['generate', 'check'])
      await assert.rejects(
        execute(fixture, {
          mode,
          candidateSource: 'source.yaml',
          candidateApproval: 'approval.json',
        }),
        (error) => error.rule === 'CANDIDATE_FLAGS',
      );
  });
});

for (const kind of ['escape', 'missing', 'directory', 'junction', 'governed-overlap'])
  test(`candidate source rejects ${kind} path`, async () => {
    await withFixture(`candidate-path-${kind}`, async (fixture) => {
      await execute(fixture);
      const f = await candidateFixture(fixture);
      if (kind === 'escape') f.options.candidateSource = '../outside.yaml';
      if (kind === 'missing') f.options.candidateSource += '.missing';
      if (kind === 'directory') f.options.candidateSource = f.directory;
      if (kind === 'governed-overlap') f.options.candidateDirectory = 'backend';
      if (kind === 'junction') {
        const link = join(fixture.root, 'candidate-link');
        await symlink(
          join(fixture.root, f.directory),
          link,
          process.platform === 'win32' ? 'junction' : 'dir',
        );
        f.options.candidateSource = 'candidate-link/betufu.yaml';
      }
      await assert.rejects(
        execute(fixture, f.options),
        (error) => error instanceof GenerateApiContractError,
      );
    });
  });

for (const kind of [
  'source',
  'configuration',
  'governed-api',
  'governed-tree',
  'approval',
  'unexpected-removal',
])
  test(`candidate detects ${kind} drift during generation`, async () => {
    await withFixture(`candidate-drift-${kind}`, async (fixture) => {
      await execute(fixture);
      const f = await candidateFixture(fixture);
      if (kind === 'unexpected-removal') {
        const deleted = 'frontend/src/app/api/generated/model/current-identity-response.ts';
        f.approval.candidate_scope.generated_paths.push(deleted);
        const planPath = join(
          fixture.root,
          '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-06-PLAN.md',
        );
        const plan = (await readFile(planPath, 'utf8')).replace(
          '\n---\n',
          `\n  - ${deleted}\n---\n`,
        );
        await writeText(planPath, plan);
        f.approval.plan_sha256 = sha256(plan).toLowerCase();
        await writeText(join(fixture.root, f.approvalPath), JSON.stringify(f.approval));
      }
      const generate = f.options.adapters.generate;
      f.options.adapters.generate = async (args) => {
        await generate(args);
        if (kind === 'source') await writeText(join(fixture.root, f.sourcePath), 'tampered\n');
        if (kind === 'configuration')
          await writeText(join(fixture.frontendRoot, 'openapitools.json'), '{}');
        if (kind === 'governed-api')
          await writeText(join(fixture.root, 'backend/openapi/betufu.yaml'), 'tampered\n');
        if (kind === 'governed-tree')
          await writeText(join(fixture.target, 'api/identity.service.ts'), 'tampered\n');
        if (kind === 'approval')
          await writeText(
            join(fixture.root, f.approvalPath),
            JSON.stringify({ ...f.approval, note: 'changed in flight' }),
          );
        if (kind === 'unexpected-removal') {
          await rm(join(args.outputDirectory, 'model/current-identity-response.ts'));
        }
      };
      await assert.rejects(
        execute(fixture, f.options),
        (error) =>
          error instanceof GenerateApiContractError &&
          (kind !== 'unexpected-removal' || error.rule === 'CANDIDATE_REMOVAL'),
      );
      await assert.rejects(stat(join(fixture.root, f.directory, 'inventory.json')), {
        code: 'ENOENT',
      });
    });
  });

test('CLI rejects candidate-only flags in generate/check and incomplete approval flags', () => {
  for (const args of [
    ['--candidate-source', 'source.yaml'],
    ['--check', '--candidate-source', 'source.yaml'],
    ['--candidate', 'candidate', '--candidate-source', 'source.yaml'],
  ]) {
    const result = spawnSync(process.execPath, [runnerPath, ...args], {
      cwd: projectRoot,
      encoding: 'utf8',
      windowsHide: true,
      timeout: 10000,
    });
    assert.equal(result.status, 2);
    assert.equal(result.stdout, '');
  }
});

test('candidate inventory distinguishes modified and deleted bytes with explicit removal scope', async () => {
  await withFixture('candidate-inventory-status', async (fixture) => {
    await execute(fixture);
    const f = await candidateFixture(fixture);
    const deleted = 'frontend/src/app/api/generated/model/current-identity-response.ts';
    const modified = 'frontend/src/app/api/generated/api/identity.service.ts';
    f.approval.candidate_scope.generated_paths.push(deleted, modified);
    f.approval.candidate_scope.deleted_paths = [deleted];
    const planPath = join(
      fixture.root,
      '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-06-PLAN.md',
    );
    const plan = (await readFile(planPath, 'utf8')).replace(
      '\n---\n',
      `\n  - ${deleted}\n  - ${modified}\n---\n`,
    );
    await writeText(planPath, plan);
    f.approval.plan_sha256 = sha256(plan).toLowerCase();
    await writeText(join(fixture.root, f.approvalPath), JSON.stringify(f.approval));
    const generate = f.options.adapters.generate;
    f.options.adapters.generate = async (args) => {
      await generate(args);
      await rm(join(args.outputDirectory, 'model/current-identity-response.ts'));
      await writeText(
        join(args.outputDirectory, 'api/identity.service.ts'),
        'export const line = 2;\r\n',
      );
    };
    await execute(fixture, f.options);
    const inventory = JSON.parse(await readFile(join(fixture.root, f.directory, 'inventory.json')));
    const removed = inventory.changes.find((entry) => entry.path === deleted);
    const changed = inventory.changes.find((entry) => entry.path === modified);
    assert.equal(removed.status, 'deleted');
    assert.equal(removed.after_content, null);
    assert.equal(sha256(removed.before_content), removed.before_sha256);
    assert.equal(changed.status, 'modified');
    assert.equal(sha256(changed.after_content), changed.after_sha256);
    assert.equal(changed.after_content, 'export const line = 2;\n');
  });
});

test('REAL approved Java engine generates new candidate CandidateChat service and model without governed writes', async () => {
  await withFixture('candidate-real-engine', async (fixture) => {
    fixture.contract = APPROVED_ENGINE;
    await writeFile(
      fixture.enginePath,
      await readFile(join(projectRoot, 'frontend', APPROVED_ENGINE.path)),
    );
    await writeText(
      join(fixture.frontendRoot, 'openapitools.json'),
      await readFile(join(projectRoot, 'frontend/openapitools.json')),
    );
    await rm(fixture.target, { recursive: true });
    for (const entry of (
      await readTreeManifest(join(projectRoot, 'frontend/src/app/api/generated'))
    ).entries)
      await writeText(join(fixture.target, entry.path), entry.bytes);
    const baseline = await readFile(join(projectRoot, 'backend/openapi/betufu.yaml'), 'utf8');
    await writeText(join(fixture.root, 'backend/openapi/betufu.yaml'), baseline);
    const f = await candidateFixture(fixture);
    const source = baseline
      .replace(
        'paths:\n',
        'paths:\n  /api/v1/chat/test:\n    get:\n      tags: [CandidateChat]\n      operationId: getCandidateChat\n      responses:\n        "200":\n          description: Candidate only\n          content:\n            application/json:\n              schema: {$ref: "#/components/schemas/CandidateChatMessage"}\n',
      )
      .replace(
        '  schemas:\n',
        '  schemas:\n    CandidateChatMessage:\n      type: object\n      required: [text]\n      properties:\n        text: {type: string}\n',
      );
    assert.notEqual(source, baseline);
    await writeText(join(fixture.root, f.sourcePath), source);
    const additions = ['api/api.ts', 'model/models.ts'].map(
      (path) => `frontend/src/app/api/generated/${path}`,
    );
    f.approval.candidate_scope.generated_paths.push(...additions);
    f.approval.candidate_scope.source_sha256 = sha256(source);
    const planPath = join(
      fixture.root,
      '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-06-PLAN.md',
    );
    const plan = (await readFile(planPath, 'utf8')).replace(
      '\n---\n',
      `\n${additions.map((path) => `  - ${path}\n`).join('')}---\n`,
    );
    await writeText(planPath, plan);
    f.approval.plan_sha256 = sha256(plan).toLowerCase();
    await writeText(join(fixture.root, f.approvalPath), JSON.stringify(f.approval));
    const before = await snapshotTree(fixture.target);
    const result = await executeGenerateApi({
      projectRoot: fixture.root,
      contract: APPROVED_ENGINE,
      ...f.options,
      adapters: { currentHead: 'a'.repeat(40) },
    });
    assert.equal(result.governedAfter, before);
    const inventory = JSON.parse(await readFile(join(fixture.root, f.directory, 'inventory.json')));
    const service = inventory.changes.find((entry) =>
      entry.path.endsWith('/api/candidate-chat.service.ts'),
    );
    assert.match(service.after_content, /getCandidateChat/);
    assert.match(service.after_content, /CandidateChatMessage/);
    assert.equal(sha256(service.after_content), service.after_sha256);
    assert.equal(inventory.engine.sha256, APPROVED_ENGINE.sha256);
    assert.equal(await snapshotTree(fixture.target), before);
    assert.equal(
      await readFile(join(fixture.root, 'backend/openapi/betufu.yaml'), 'utf8'),
      baseline,
    );
  });
});

async function snapshotTree(path) {
  try {
    const manifest = await readTreeManifest(path);
    return manifest.digest;
  } catch (error) {
    if (error?.code === 'ENOENT') {
      return null;
    }
    throw error;
  }
}

test('normaliza texto gerado para LF e uma newline final', () => {
  assert.equal(normalizeGeneratedText('a\r\nb\rc'), 'a\nb\nc\n');
  assert.throws(
    () => normalizeGeneratedText('safe\0unsafe'),
    (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_NUL',
  );
});

test('endurece somente o fallback conhecido do BaseService para same-origin', () => {
  assert.equal(
    hardenGeneratedTypeScript(
      'api.base.service.ts',
      "export class BaseService {\r\n    protected basePath = 'http://localhost';\r\n}\r\n",
    ),
    "export class BaseService {\n    protected basePath = '';\n}\n",
  );
  assert.throws(
    () =>
      hardenGeneratedTypeScript(
        'api.base.service.ts',
        "export class BaseService {\n    protected basePath = '';\n}\n",
      ),
    (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_BASE_PATH',
  );
  assert.throws(
    () =>
      validateGeneratedTypeScript(
        'api/identity.service.ts',
        "const endpoint = 'http://localhost';\n",
      ),
    (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_ABSOLUTE_URL',
  );
});

for (const [label, source] of [
  ['ausente', 'export class BaseService {}\n'],
  [
    'divergente',
    "export class BaseService { protected basePath = 'https://api.example.invalid'; }\n",
  ],
  [
    'duplicado',
    "protected basePath = 'http://localhost';\nprotected basePath = 'http://localhost';\n",
  ],
  [
    'comentado',
    "/*\n    protected basePath = 'http://localhost';\n*/\nexport class BaseService { protected basePath = '/api'; }\n",
  ],
  [
    'outra-classe',
    "export class Decoy {\n    protected basePath = 'http://localhost';\n}\nexport class BaseService {}\n",
  ],
]) {
  test(`recusa fallback ${label} no BaseService gerado`, () => {
    assert.throws(
      () => hardenGeneratedTypeScript('api.base.service.ts', source),
      (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_BASE_PATH',
    );
  });
}

test('recusa fallback presente somente em template string', () => {
  const source = [
    'const decoy = ' + String.fromCodePoint(96),
    "    protected basePath = 'http://localhost';",
    String.fromCodePoint(96) + ';',
    "export class BaseService { protected basePath = '/api'; }",
    '',
  ].join('\n');
  assert.throws(
    () => hardenGeneratedTypeScript('api.base.service.ts', source),
    (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_BASE_PATH',
  );
});

for (const [label, relativePath, source] of [
  ['network-path', 'model/example.ts', "const endpoint = '//api.example.invalid';\n"],
  ['escaped-scheme', 'model/example.ts', "const endpoint = 'h\\x74tps://api.example.invalid';\n"],
  ['escaped-solidus', 'model/example.ts', "const endpoint = 'https:\\/\\/api.example.invalid';\n"],
  [
    'diagnostic-fora-do-bloco',
    'api.module.ts',
    "const diagnostic = 'See also https://github.com/angular/angular/issues/20575';\n",
  ],
]) {
  test(`recusa URL absoluta ${label} antes da publicacao`, () => {
    assert.throws(
      () => validateGeneratedTypeScript(relativePath, source),
      (error) =>
        error instanceof GenerateApiContractError && error.rule === 'GENERATED_ABSOLUTE_URL',
    );
  });
}

test('manifesto recusa bytes com URL absoluta escondida depois de regex literal', () => {
  const maliciousSource =
    "const matcher = /https?:\\/\\//; const endpoint = 'https://evil.invalid';\n";
  assert.throws(
    () =>
      assertApprovedGeneratedManifest({
        files: APPROVED_GENERATED_FILES,
        digest: sha256(Buffer.from(maliciousSource, 'utf8')),
      }),
    (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_MANIFEST',
  );
});

test('geracao seleciona catalogo, organizacoes, atletas, elencos, importacao tabular e eventos/resultados', () => {
  assert.deepEqual(PUBLIC_GENERATOR_API_TAGS, [
    'Chat',
    'AthleteLink',
    'AthleteMedia',
    'AthletePublicProfile',
    'CreditLedger',
    'CreditCorrection',
    'Diagnostic',
    'Enrollment',
    'Identity',
    'OperationalCapabilities',
    'OperationalPanel',
    'PasswordResetRecovery',
    'Prediction',
    'Profile',
    'Role',
    'Session',
    'SportsAthlete',
    'SportsBracket',
    'SportsCatalog',
    'SportsEventResult',
    'SportsOrganization',
    'SportsPublic',
    'SportsRoster',
    'SportsStandings',
    'SportsTabularImport',
    'OddsMarket',
    'OddsOverride',
    'OddsPublic',
    'Settlement',
    'Ranking',
    'Group',
    'Achievement',
    'EmergencyPauses',
    'PrivacyRights',
  ]);
  for (const model of [
    'SettlementHistoryItem',
    'SettlementAuthorizationRequest',
    'CreditCorrectionView',
    'RankingEntry',
    'PrivateGroup',
    'Achievement',
  ]) {
    assert.equal(PUBLIC_GENERATOR_MODELS.includes(model), true, model);
  }
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('FavoriteOrganizationRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('CreditBalanceResponse'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('AthleteMediaResponse'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('AthletePublicProfile'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('AthletePublicProfileUpdateRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('PublicSportsEventPage'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsEventDetail'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsEventFormat'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsEventLifecycleEntry'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsEventSlotItem'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsResultScoreItem'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsResultVersion'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsCatalogScope'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsVenueSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsOrganizationSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsTeamSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsAthleteCreateRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsAthleteSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketCreateRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketCorrectRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketMatchConfigureRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketMatchView'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketPublishRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketRound'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketSlotConfigureRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketSlotView'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketSourceType'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketUpdateRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsBracketView'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsRosterCorrectRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsRosterDraft'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsRosterMemberItem'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsRosterMemberRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsRosterPublication'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsRosterUpdateRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsStandingsRow'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsStandingsSnapshot'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsTabularImportPreview'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsTabularImportPreviewError'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsTabularImportPreviewSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsTabularImportResult'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('CreateOddsMarketRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('OddsLineageReferences'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('OddsMarketLifecycleRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('OddsMarketState'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('OddsMarketView'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('OddsOverrideApprovalRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('OddsOverrideProposalRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('OddsOverrideView'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('PublicOddsExplanation'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('PublicOddsHistory'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('PublicOddsSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('PublicOddsView'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('PredictionSubmitRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('PredictionTicketResponse'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsRosterSnapshotForOdds'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsCompetitionCreateRequest'), false);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsAthlete'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsBracket'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsCatalog'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsEventResult'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsOrganization'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsRoster'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsStandings'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsTabularImport'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsTabularImportPreview'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsTabularImportResult'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsOperations'), false);
});

test('manifesto aprovado identifica exatamente a arvore TypeScript versionada', async () => {
  const generatedRoot = join(projectRoot, 'frontend', 'src', 'app', 'api', 'generated');
  const manifest = await readTreeManifest(generatedRoot);

  assert.deepEqual(manifest.files, APPROVED_GENERATED_FILES);
  assert.equal(manifest.digest, APPROVED_GENERATED_DIGEST);
  assert.equal(assertApprovedGeneratedManifest(manifest), true);
});

test('inventario ratificado inclui odds, saldo, palpites e barrels atualizados', async () => {
  const generatedRoot = join(projectRoot, 'frontend', 'src', 'app', 'api', 'generated');
  const manifest = await readTreeManifest(generatedRoot);
  const oddsFiles = [
    'api/odds-market.service.ts',
    'api/odds-override.service.ts',
    'api/odds-public.service.ts',
    'api/credit-ledger.service.ts',
    'api/prediction.service.ts',
    'model/create-odds-market-request.ts',
    'model/credit-balance-response.ts',
    'model/odds-lineage-references.ts',
    'model/odds-market-lifecycle-request.ts',
    'model/odds-market-state.ts',
    'model/odds-market-view.ts',
    'model/odds-override-approval-request.ts',
    'model/odds-override-proposal-request.ts',
    'model/odds-override-view.ts',
    'model/public-odds-explanation.ts',
    'model/public-odds-history.ts',
    'model/public-odds-summary.ts',
    'model/public-odds-view.ts',
    'model/prediction-submit-request.ts',
    'model/prediction-ticket-response.ts',
    'model/sports-roster-snapshot-for-odds.ts',
  ];

  assert.equal(manifest.digest, 'BBF8DE6CA2FEA7C26FBAFC9A3ABB11F15D7B14E34A4993121A625B1D83C9DA29');
  assert.deepEqual(
    oddsFiles.filter((path) => manifest.files.includes(path)),
    oddsFiles,
  );
  assert.equal(manifest.files.includes('api/api.ts'), true);
  assert.equal(manifest.files.includes('model/models.ts'), true);
  assert.equal(manifest.files.includes('index.ts'), true);
});

test('manifesto aprovado recusa arquivo, servico e bytes extras', async () => {
  const generatedRoot = join(projectRoot, 'frontend', 'src', 'app', 'api', 'generated');
  const manifest = await readTreeManifest(generatedRoot);
  const mutations = [
    {
      ...manifest,
      files: [...manifest.files, 'extra.ts'],
    },
    {
      ...manifest,
      files: [...manifest.files, 'api/extra.service.ts'],
    },
    {
      ...manifest,
      digest: '0'.repeat(64),
    },
  ];

  for (const mutation of mutations) {
    assert.throws(
      () => assertApprovedGeneratedManifest(mutation),
      (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_MANIFEST',
    );
  }
});

test('inspeciona endpoints e schemas do contrato sem depender do gerador', () => {
  const inventory = inspectOpenApiContract(
    [
      'openapi: 3.1.0',
      'paths:',
      '  /api/v1/public/sports/events:',
      '    get:',
      '      operationId: listPublishedSportsEvents',
      'components:',
      '  schemas:',
      '    PublicSportsEventPage:',
      '      type: object',
      '    SportsEventFormat:',
      '      type: string',
      '',
    ].join('\n'),
  );

  assert.deepEqual(inventory.endpoints, [
    {
      path: '/api/v1/public/sports/events',
      methods: ['GET'],
    },
  ]);
  assert.deepEqual(inventory.schemas, ['PublicSportsEventPage', 'SportsEventFormat']);
  assert.deepEqual(inventory.publicSchemas, ['PublicSportsEventPage']);
});

test('inventario gerado exclui comandos do tracer e preserva o catalogo escopado', () => {
  const inventory = inspectGeneratedOpenApiContract(
    [
      'openapi: 3.1.0',
      'paths:',
      '  /api/v1/operations/sports/competitions:',
      '    post:',
      '      operationId: createSportsCompetition',
      '  /api/v1/operations/sports/editions/{editionId}/brackets:',
      '    get:',
      '      operationId: listSportsBrackets',
      '    post:',
      '      operationId: createSportsBracket',
      '  /api/v1/operations/sports/brackets/{bracketId}:',
      '    get:',
      '      operationId: getSportsBracket',
      '    put:',
      '      operationId: updateSportsBracket',
      '  /api/v1/operations/sports/brackets/{bracketId}/publish:',
      '    post:',
      '      operationId: publishSportsBracket',
      '  /api/v1/operations/sports/brackets/{bracketId}/correct:',
      '    post:',
      '      operationId: correctSportsBracket',
      '  /api/v1/operations/sports/catalog/scope:',
      '    get:',
      '      operationId: getSportsCatalogScope',
      '  /api/v1/public/sports/events:',
      '    get:',
      '      operationId: listPublishedSportsEvents',
      '  /api/v1/operations/sports/organizations:',
      '    get:',
      '      operationId: listSportsOrganizations',
      '    post:',
      '      operationId: createSportsOrganization',
      '  /api/v1/operations/sports/teams/{teamId}/publish:',
      '    post:',
      '      operationId: publishSportsTeam',
      '  /api/v1/operations/sports/events/{eventId}/lifecycle:',
      '    get:',
      '      operationId: listSportsEventLifecycle',
      '  /api/v1/operations/sports/athletes:',
      '    get:',
      '      operationId: listSportsAthletes',
      '  /api/v1/operations/sports/teams/{teamId}/athletes:',
      '    post:',
      '      operationId: createSportsAthlete',
      '  /api/v1/operations/sports/teams/{teamId}/roster:',
      '    get:',
      '      operationId: getSportsRosterDraft',
      '    put:',
      '      operationId: updateSportsRosterDraft',
      '  /api/v1/operations/sports/teams/{teamId}/roster/publish:',
      '    post:',
      '      operationId: publishSportsRoster',
      '  /api/v1/operations/sports/teams/{teamId}/roster/correct:',
      '    post:',
      '      operationId: correctSportsRoster',
      '  /api/v1/operations/sports/teams/{teamId}/roster/publication:',
      '    get:',
      '      operationId: getSportsRosterPublication',
      'components:',
      '  schemas:',
      '    SportsCompetitionCreateRequest:',
      '      type: object',
      '    PublicSportsEventPage:',
      '      type: object',
      '    SportsOrganizationSummary:',
      '      type: object',
      '    FavoriteOrganizationRequest:',
      '      type: object',
      '    SportsAthleteCreateRequest:',
      '      type: object',
      '    SportsAthleteSummary:',
      '      type: object',
      '    SportsEventLifecycleEntry:',
      '      type: object',
      '    SportsRosterCorrectRequest:',
      '      type: object',
      '    SportsRosterDraft:',
      '      type: object',
      '    SportsRosterMemberItem:',
      '      type: object',
      '    SportsRosterMemberRequest:',
      '      type: object',
      '    SportsRosterPublication:',
      '      type: object',
      '    SportsRosterUpdateRequest:',
      '      type: object',
      '',
    ].join('\n'),
  );

  assert.deepEqual(inventory, {
    endpoints: [
      { path: '/api/v1/operations/sports/editions/{editionId}/brackets', methods: ['GET', 'POST'] },
      { path: '/api/v1/operations/sports/brackets/{bracketId}', methods: ['GET', 'PUT'] },
      { path: '/api/v1/operations/sports/brackets/{bracketId}/publish', methods: ['POST'] },
      { path: '/api/v1/operations/sports/brackets/{bracketId}/correct', methods: ['POST'] },
      { path: '/api/v1/operations/sports/catalog/scope', methods: ['GET'] },
      { path: '/api/v1/public/sports/events', methods: ['GET'] },
      { path: '/api/v1/operations/sports/organizations', methods: ['GET', 'POST'] },
      { path: '/api/v1/operations/sports/teams/{teamId}/publish', methods: ['POST'] },
      { path: '/api/v1/operations/sports/events/{eventId}/lifecycle', methods: ['GET'] },
      { path: '/api/v1/operations/sports/athletes', methods: ['GET'] },
      { path: '/api/v1/operations/sports/teams/{teamId}/athletes', methods: ['POST'] },
      { path: '/api/v1/operations/sports/teams/{teamId}/roster', methods: ['GET', 'PUT'] },
      { path: '/api/v1/operations/sports/teams/{teamId}/roster/publish', methods: ['POST'] },
      { path: '/api/v1/operations/sports/teams/{teamId}/roster/correct', methods: ['POST'] },
      { path: '/api/v1/operations/sports/teams/{teamId}/roster/publication', methods: ['GET'] },
    ],
    schemas: [
      'FavoriteOrganizationRequest',
      'PublicSportsEventPage',
      'SportsAthleteCreateRequest',
      'SportsAthleteSummary',
      'SportsEventLifecycleEntry',
      'SportsOrganizationSummary',
      'SportsRosterCorrectRequest',
      'SportsRosterDraft',
      'SportsRosterMemberItem',
      'SportsRosterMemberRequest',
      'SportsRosterPublication',
      'SportsRosterUpdateRequest',
    ],
    publicSchemas: ['PublicSportsEventPage'],
  });
});

test('modo candidato persiste somente metadados e nao toca a arvore governada', async () => {
  await withFixture('candidate', async (fixture) => {
    const candidateDirectory = join(fixture.frontendRoot, 'target', 'api-candidate', '03-03');
    const governedBefore = await readTreeManifest(fixture.target);
    let scratchDirectory;
    const result = await execute(fixture, {
      mode: 'candidate',
      candidateDirectory,
      adapters: {
        generate: async ({ outputDirectory }) => {
          scratchDirectory = outputDirectory;
          await writeGeneratedBaseService(outputDirectory);
          await writeText(
            join(outputDirectory, 'api', 'identity.service.ts'),
            'export const line = 1;\n',
          );
          await writeText(join(outputDirectory, 'README.md'), 'raw auxiliary output\n');
        },
      },
    });

    assert.equal(result.mode, 'candidate');
    assert.equal(result.governedBefore, governedBefore.digest);
    assert.equal(result.governedAfter, governedBefore.digest);
    assert.equal(await snapshotTree(fixture.target), governedBefore.digest);
    assert.deepEqual((await readdir(candidateDirectory)).sort(), [
      'digest.sha256',
      'governed-after.sha256',
      'governed-before.sha256',
      'inventory.json',
    ]);
    const inventory = JSON.parse(
      await readFile(join(candidateDirectory, 'inventory.json'), 'utf8'),
    );
    assert.equal(inventory.digest, result.digest);
    assert.deepEqual(inventory.files, result.files);
    assert.equal(
      await readFile(join(candidateDirectory, 'digest.sha256'), 'utf8'),
      `${result.digest}\n`,
    );
    assert.equal(
      await readFile(join(candidateDirectory, 'governed-before.sha256'), 'utf8'),
      `${governedBefore.digest}\n`,
    );
    assert.equal(
      await readFile(join(candidateDirectory, 'governed-after.sha256'), 'utf8'),
      `${governedBefore.digest}\n`,
    );
    assert.equal(
      (await readdir(candidateDirectory)).some((entry) => entry.endsWith('.ts')),
      false,
    );
    await assert.rejects(stat(scratchDirectory), { code: 'ENOENT' });
  });
});

test('modo candidato bloqueia campo proibido em schema publico antes do digest', async () => {
  await withFixture('candidate-forbidden-field', async (fixture) => {
    await writeFile(
      join(fixture.root, 'backend', 'openapi', 'betufu.yaml'),
      [
        'openapi: 3.1.0',
        'paths:',
        '  /api/v1/public/example:',
        '    get:',
        '      operationId: getExample',
        'components:',
        '  schemas:',
        '    PublicExample:',
        '      type: object',
        '      properties:',
        '        matricula:',
        '          type: string',
        '',
      ].join('\n'),
      'utf8',
    );
    const candidateDirectory = join(fixture.frontendRoot, 'target', 'api-candidate', '03-03');
    await assert.rejects(
      execute(fixture, {
        mode: 'candidate',
        candidateDirectory,
      }),
      (error) =>
        error instanceof GenerateApiContractError &&
        error.rule === 'PUBLIC_SCHEMA_FORBIDDEN_FIELD' &&
        error.message.includes('matricula'),
    );
    await assert.rejects(stat(candidateDirectory), { code: 'ENOENT' });
  });
});

test('manifesto divergente bloqueia publicacao e preserva a arvore valida', async () => {
  await withFixture('generated-manifest', async (fixture) => {
    await execute(fixture);
    const before = await readTreeManifest(fixture.target);

    await assert.rejects(
      execute(fixture, {
        generatedManifestContract: {
          files: before.files,
          digest: before.digest,
        },
        adapters: {
          generate: async ({ outputDirectory }) => {
            await writeGeneratedBaseService(outputDirectory);
            await writeText(
              join(outputDirectory, 'api', 'identity.service.ts'),
              'export const line = 1;\n',
            );
            await writeText(
              join(outputDirectory, 'model', 'current-identity-response.ts'),
              'export interface CurrentIdentityResponse {}\n',
            );
            await writeText(
              join(outputDirectory, 'api', 'extra.service.ts'),
              'export const extraService = true;\n',
            );
          },
        },
      }),
      (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_MANIFEST',
    );

    const after = await readTreeManifest(fixture.target);
    assert.equal(after.digest, before.digest);
    assert.deepEqual(after.files, before.files);
  });
});

test('gera somente TypeScript, substitui a arvore e remove obsoletos', async () => {
  await withFixture('generate', async (fixture) => {
    const result = await execute(fixture);

    assert.equal(result.mode, 'generate');
    assert.deepEqual(result.files, [
      'api.base.service.ts',
      'api/identity.service.ts',
      'model/current-identity-response.ts',
    ]);
    assert.equal(
      await readFile(join(fixture.target, 'api.base.service.ts'), 'utf8'),
      "export class BaseService {\n    protected basePath = '';\n}\n",
    );
    assert.equal(
      await readFile(join(fixture.target, 'api', 'identity.service.ts'), 'utf8'),
      'export const line = 1;\n',
    );
    assert.equal(
      await readFile(join(fixture.target, 'model', 'current-identity-response.ts'), 'utf8'),
      'export interface CurrentIdentityResponse {}\n',
    );
    await assert.rejects(stat(join(fixture.target, 'legacy.ts')), { code: 'ENOENT' });
    await assert.rejects(stat(join(fixture.target, 'README.md')), { code: 'ENOENT' });
    await assert.rejects(stat(join(fixture.target, 'package.json')), { code: 'ENOENT' });
  });
});

test('fallback ausente ou divergente nunca substitui a arvore valida', async (t) => {
  for (const scenario of [
    { label: 'missing', source: null },
    {
      label: 'unexpected',
      source: "export class BaseService { protected basePath = 'https://api.example.invalid'; }\n",
    },
    {
      label: 'duplicate',
      source:
        "protected basePath = 'http://localhost';\nprotected basePath = 'http://localhost';\n",
    },
  ]) {
    await t.test(scenario.label, async () => {
      await withFixture(`base-path-${scenario.label}`, async (fixture) => {
        const before = await snapshotTree(fixture.target);
        await assert.rejects(
          execute(fixture, {
            adapters: {
              generate: async ({ outputDirectory }) => {
                if (scenario.source !== null) {
                  await writeText(join(outputDirectory, 'api.base.service.ts'), scenario.source);
                }
                await writeText(
                  join(outputDirectory, 'api', 'identity.service.ts'),
                  'export const line = 1;\n',
                );
              },
            },
          }),
          (error) =>
            error instanceof GenerateApiContractError && error.rule === 'GENERATED_BASE_PATH',
        );
        assert.equal(await snapshotTree(fixture.target), before);
      });
    });
  }
});

test('URL absoluta na candidata nunca substitui a arvore valida', async () => {
  await withFixture('absolute-url-before-publish', async (fixture) => {
    const before = await snapshotTree(fixture.target);
    await assert.rejects(
      execute(fixture, {
        adapters: {
          generate: async ({ outputDirectory }) => {
            await writeGeneratedBaseService(outputDirectory);
            await writeText(
              join(outputDirectory, 'model', 'current-identity-response.ts'),
              "export const endpoint = 'https://api.example.invalid';\n",
            );
          },
        },
      }),
      (error) =>
        error instanceof GenerateApiContractError && error.rule === 'GENERATED_ABSOLUTE_URL',
    );
    assert.equal(await snapshotTree(fixture.target), before);
  });
});

test('falha do gerador preserva integralmente a arvore valida', async () => {
  await withFixture('generator-failure', async (fixture) => {
    const before = await snapshotTree(fixture.target);

    await assert.rejects(
      execute(fixture, {
        adapters: {
          generate: async () => {
            throw new Error('SENSITIVE_GENERATOR_FAILURE');
          },
        },
      }),
      (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATOR_FAILED',
    );

    assert.equal(await snapshotTree(fixture.target), before);
  });
});

test('check limpo nao altera a arvore valida', async () => {
  await withFixture('check-clean', async (fixture) => {
    await execute(fixture);
    const before = await snapshotTree(fixture.target);
    const result = await execute(fixture, { mode: 'check' });

    assert.equal(result.mode, 'check');
    assert.equal(result.drift, false);
    assert.equal(await snapshotTree(fixture.target), before);
  });
});

for (const drift of ['add', 'delete', 'change']) {
  test(`check detecta ${drift} sem escrever na arvore valida`, async () => {
    await withFixture(`check-${drift}`, async (fixture) => {
      await execute(fixture);
      const before = await snapshotTree(fixture.target);
      const customGenerate = async ({ outputDirectory }) => {
        await writeGeneratedBaseService(outputDirectory);
        await writeText(
          join(outputDirectory, 'api', 'identity.service.ts'),
          drift === 'change' ? 'export const line = 2;\n' : 'export const line = 1;\n',
        );
        if (drift !== 'delete') {
          await writeText(
            join(outputDirectory, 'model', 'current-identity-response.ts'),
            'export interface CurrentIdentityResponse {}\n',
          );
        }
        if (drift === 'add') {
          await writeText(join(outputDirectory, 'extra.ts'), 'export const extra = true;\n');
        }
      };

      await assert.rejects(
        execute(fixture, { mode: 'check', adapters: { generate: customGenerate } }),
        (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_DRIFT',
      );
      assert.equal(await snapshotTree(fixture.target), before);
    });
  });
}

test('JAR ausente, divergente ou adulterado nunca publica', async (t) => {
  await t.test('ausente', async () => {
    await withFixture('jar-missing', async (fixture) => {
      await rm(fixture.enginePath);
      await assert.rejects(execute(fixture), (error) => error.rule === 'ENGINE_MISSING');
    });
  });
  await t.test('tamanho divergente', async () => {
    await withFixture('jar-size', async (fixture) => {
      await writeFile(fixture.enginePath, Buffer.from('wrong-size'));
      await assert.rejects(execute(fixture), (error) => error.rule === 'ENGINE_SIZE');
    });
  });
  await t.test('adulterado durante geracao', async () => {
    await withFixture('jar-tampered', async (fixture) => {
      const before = await snapshotTree(fixture.target);
      await assert.rejects(
        execute(fixture, {
          adapters: {
            generate: async ({ outputDirectory }) => {
              await writeText(join(outputDirectory, 'api.ts'), 'export const api = true;\n');
              await writeFile(fixture.enginePath, Buffer.from('tampered'));
            },
          },
        }),
        (error) => ['ENGINE_SIZE', 'ENGINE_HASH'].includes(error.rule),
      );
      assert.equal(await snapshotTree(fixture.target), before);
    });
  });
  await t.test('snapshot executado adulterado mesmo quando o gerador falha', async () => {
    await withFixture('jar-execution-snapshot-tampered', async (fixture) => {
      const before = await snapshotTree(fixture.target);
      await assert.rejects(
        execute(fixture, {
          adapters: {
            generate: async ({ enginePath }) => {
              await writeFile(enginePath, Buffer.from('tampered-execution-snapshot'));
              throw new Error('generator-failed-after-tamper');
            },
          },
        }),
        (error) => ['ENGINE_SIZE', 'ENGINE_HASH'].includes(error.rule),
      );
      assert.equal(await snapshotTree(fixture.target), before);
    });
  });
});

test('lock vivo ou ambiguo nao e roubado; lock morto e recuperado', async (t) => {
  for (const status of ['alive', 'ambiguous']) {
    await t.test(status, async () => {
      await withFixture(`lock-${status}`, async (fixture) => {
        await writeLock(fixture.workspace, {
          version: 1,
          pid: 999,
          nonce: 'abcdefabcdefabcdefabcdefabcdefab',
          createdAt: FIXED_NOW,
        });
        await assert.rejects(
          execute(fixture, { adapters: { processStatus: async () => status } }),
          (error) => error.rule === (status === 'alive' ? 'LOCK_LIVE' : 'LOCK_AMBIGUOUS'),
        );
      });
    });
  }

  await t.test('dead', async () => {
    await withFixture('lock-dead', async (fixture) => {
      await writeLock(fixture.workspace, {
        version: 1,
        pid: 999,
        nonce: 'abcdefabcdefabcdefabcdefabcdefab',
        createdAt: FIXED_NOW,
      });
      const result = await execute(fixture, { adapters: { processStatus: async () => 'dead' } });
      assert.equal(result.mode, 'generate');
    });
  });

  await t.test('dead substituido durante a verificacao', async () => {
    await withFixture('lock-dead-replaced', async (fixture) => {
      const lockPath = join(fixture.workspace, 'pipeline.lock');
      const ownerPath = join(lockPath, 'owner.json');
      const replacement = {
        version: 1,
        pid: 777,
        nonce: '77777777777777777777777777777777',
        createdAt: FIXED_NOW,
      };
      await writeLock(fixture.workspace, {
        version: 1,
        pid: 999,
        nonce: 'abcdefabcdefabcdefabcdefabcdefab',
        createdAt: FIXED_NOW,
      });
      await assert.rejects(
        execute(fixture, {
          adapters: {
            processStatus: async () => {
              await writeFile(ownerPath, `${JSON.stringify(replacement)}\n`, 'utf8');
              return 'dead';
            },
          },
        }),
        (error) => error.rule === 'LOCK_AMBIGUOUS',
      );
      assert.deepEqual(JSON.parse(await readFile(ownerPath, 'utf8')), replacement);
      await assert.rejects(stat(join(lockPath, 'recovery.lock')), { code: 'ENOENT' });
    });
  });

  await t.test('recuperador morto nao envenena o lock', async () => {
    await withFixture('lock-dead-recovery-marker', async (fixture) => {
      const lockPath = await writeLock(fixture.workspace, {
        version: 1,
        pid: 999,
        nonce: 'abcdefabcdefabcdefabcdefabcdefab',
        createdAt: FIXED_NOW,
      });
      await writeText(
        join(lockPath, 'recovery.lock'),
        `${JSON.stringify({ version: 1, pid: 888, nonce: '88888888888888888888888888888888', createdAt: FIXED_NOW })}\n`,
      );
      const result = await execute(fixture, { adapters: { processStatus: async () => 'dead' } });
      assert.equal(result.mode, 'generate');
      await assert.rejects(stat(lockPath), { code: 'ENOENT' });
    });
  });
});

test('duas execucoes concorrentes nao geram nem publicam em paralelo', async () => {
  await withFixture('concurrent', async (fixture) => {
    let signalEntered;
    let signalRelease;
    const entered = new Promise((accept) => {
      signalEntered = accept;
    });
    const release = new Promise((accept) => {
      signalRelease = accept;
    });
    const firstNonce = '11111111111111111111111111111111';
    const secondNonce = '22222222222222222222222222222222';
    const first = executeGenerateApi({
      projectRoot: fixture.root,
      contract: fixture.contract,
      generatedManifestContract: null,
      mode: 'generate',
      adapters: adapters({
        nonce: () => firstNonce,
        pid: 1111,
        generate: async ({ outputDirectory }) => {
          signalEntered();
          await release;
          await writeGeneratedBaseService(outputDirectory);
          await writeText(join(outputDirectory, 'api.ts'), 'export const api = true;\n');
        },
      }),
    });
    await entered;

    let secondGeneratorCalled = false;
    await assert.rejects(
      executeGenerateApi({
        projectRoot: fixture.root,
        contract: fixture.contract,
        generatedManifestContract: null,
        mode: 'generate',
        adapters: adapters({
          nonce: () => secondNonce,
          pid: 2222,
          processStatus: async (pid) => (pid === 1111 ? 'alive' : 'ambiguous'),
          generate: async () => {
            secondGeneratorCalled = true;
          },
        }),
      }),
      (error) => error.rule === 'LOCK_LIVE',
    );
    assert.equal(secondGeneratorCalled, false);
    signalRelease();
    const result = await first;
    assert.equal(result.mode, 'generate');
    assert.deepEqual(result.files, ['api.base.service.ts', 'api.ts']);
  });
});

test('check recusa journal pendente e nao executa recovery', async () => {
  await withFixture('check-journal', async (fixture) => {
    const before = await snapshotTree(fixture.target);
    await writeText(
      join(fixture.workspace, 'pipeline.journal.json'),
      `${JSON.stringify({ version: 1, nonce: FIXED_NONCE, state: 'prepared', operation: `tmp/openapi-generator/operations/${FIXED_NONCE}`, candidate: `tmp/openapi-generator/operations/${FIXED_NONCE}/candidate`, backup: `tmp/openapi-generator/operations/${FIXED_NONCE}/backup`, target: 'src/app/api/generated', hadTarget: true, createdAt: FIXED_NOW })}\n`,
    );
    await assert.rejects(
      execute(fixture, { mode: 'check' }),
      (error) => error.rule === 'RECOVERY_REQUIRED',
    );
    assert.equal(await snapshotTree(fixture.target), before);
  });
});

test('generate recupera journals prepared, old-moved e new-installed', async (t) => {
  for (const state of ['prepared', 'old-moved', 'new-installed']) {
    await t.test(state, async () => {
      await withFixture(`recovery-${state}`, async (fixture) => {
        const operation = join(fixture.workspace, 'operations', FIXED_NONCE);
        const candidate = join(operation, 'candidate');
        const backup = join(operation, 'backup');
        await writeText(join(candidate, 'candidate.ts'), 'export const candidate = true;\n');
        if (state === 'old-moved') {
          await mkdir(dirname(backup), { recursive: true });
          await import('node:fs/promises').then(({ rename }) => rename(fixture.target, backup));
        }
        if (state === 'new-installed') {
          await mkdir(dirname(backup), { recursive: true });
          await import('node:fs/promises').then(async ({ rename }) => {
            await rename(fixture.target, backup);
            await rename(candidate, fixture.target);
          });
        }
        await writeText(
          join(fixture.workspace, 'pipeline.journal.json'),
          `${JSON.stringify({ version: 1, nonce: FIXED_NONCE, state, operation: `tmp/openapi-generator/operations/${FIXED_NONCE}`, candidate: `tmp/openapi-generator/operations/${FIXED_NONCE}/candidate`, backup: `tmp/openapi-generator/operations/${FIXED_NONCE}/backup`, target: 'src/app/api/generated', hadTarget: true, createdAt: FIXED_NOW })}\n`,
        );

        const result = await execute(fixture);
        assert.equal(result.mode, 'generate');
        await assert.rejects(stat(join(fixture.workspace, 'pipeline.journal.json')), {
          code: 'ENOENT',
        });
      });
    });
  }
});

test('recovery restaura a arvore anterior nas janelas entre rename e journal', async (t) => {
  for (const crashWindow of ['prepared-after-first-rename', 'old-moved-after-second-rename']) {
    await t.test(crashWindow, async () => {
      await withFixture(`recovery-${crashWindow}`, async (fixture) => {
        const before = await snapshotTree(fixture.target);
        const operation = join(fixture.workspace, 'operations', FIXED_NONCE);
        const candidate = join(operation, 'candidate');
        const backup = join(operation, 'backup');
        const { rename } = await import('node:fs/promises');
        await writeText(join(candidate, 'candidate.ts'), 'export const candidate = true;\n');
        await mkdir(dirname(backup), { recursive: true });
        await rename(fixture.target, backup);

        const state = crashWindow === 'prepared-after-first-rename' ? 'prepared' : 'old-moved';
        if (crashWindow === 'old-moved-after-second-rename') {
          await rename(candidate, fixture.target);
        }
        await writeText(
          join(fixture.workspace, 'pipeline.journal.json'),
          `${JSON.stringify({ version: 1, nonce: FIXED_NONCE, state, operation: `tmp/openapi-generator/operations/${FIXED_NONCE}`, candidate: `tmp/openapi-generator/operations/${FIXED_NONCE}/candidate`, backup: `tmp/openapi-generator/operations/${FIXED_NONCE}/backup`, target: 'src/app/api/generated', hadTarget: true, createdAt: FIXED_NOW })}\n`,
        );

        await assert.rejects(
          execute(fixture, {
            adapters: {
              generate: async () => {
                throw new Error('stop-after-recovery');
              },
            },
          }),
          (error) => error.rule === 'GENERATOR_FAILED',
        );
        assert.equal(await snapshotTree(fixture.target), before);
        await assert.rejects(stat(join(fixture.workspace, 'pipeline.journal.json')), {
          code: 'ENOENT',
        });
      });
    });
  }
});

test('recovery repetido reconhece cleanup ou rollback ja concluido', async (t) => {
  for (const scenario of [
    { label: 'prepared-cleanup', state: 'prepared', operationExists: false },
    { label: 'old-moved-rollback', state: 'old-moved', operationExists: true },
    { label: 'new-installed-cleanup', state: 'new-installed', operationExists: false },
  ]) {
    await t.test(scenario.label, async () => {
      await withFixture(`recovery-idempotent-${scenario.label}`, async (fixture) => {
        const before = await snapshotTree(fixture.target);
        const operation = join(fixture.workspace, 'operations', FIXED_NONCE);
        if (scenario.operationExists) {
          await writeText(join(operation, 'raw', 'leftover.txt'), 'diagnostic only\n');
        }
        await writeText(
          join(fixture.workspace, 'pipeline.journal.json'),
          `${JSON.stringify({ version: 1, nonce: FIXED_NONCE, state: scenario.state, operation: `tmp/openapi-generator/operations/${FIXED_NONCE}`, candidate: `tmp/openapi-generator/operations/${FIXED_NONCE}/candidate`, backup: `tmp/openapi-generator/operations/${FIXED_NONCE}/backup`, target: 'src/app/api/generated', hadTarget: true, createdAt: FIXED_NOW })}\n`,
        );

        await assert.rejects(
          execute(fixture, {
            adapters: {
              generate: async () => {
                throw new Error('stop-after-idempotent-recovery');
              },
            },
          }),
          (error) => error.rule === 'GENERATOR_FAILED',
        );
        assert.equal(await snapshotTree(fixture.target), before);
        await assert.rejects(stat(join(fixture.workspace, 'pipeline.journal.json')), {
          code: 'ENOENT',
        });
      });
    });
  }
});

test('recovery completa janelas parciais de cleanup e rollback', async (t) => {
  await t.test('prepared com candidate ja removido', async () => {
    await withFixture('recovery-prepared-partial-cleanup', async (fixture) => {
      const before = await snapshotTree(fixture.target);
      const operation = join(fixture.workspace, 'operations', FIXED_NONCE);
      await writeText(join(operation, 'raw', 'leftover.txt'), 'partial cleanup\n');
      await writeText(
        join(fixture.workspace, 'pipeline.journal.json'),
        `${JSON.stringify({ version: 1, nonce: FIXED_NONCE, state: 'prepared', operation: `tmp/openapi-generator/operations/${FIXED_NONCE}`, candidate: `tmp/openapi-generator/operations/${FIXED_NONCE}/candidate`, backup: `tmp/openapi-generator/operations/${FIXED_NONCE}/backup`, target: 'src/app/api/generated', hadTarget: true, createdAt: FIXED_NOW })}\n`,
      );
      await assert.rejects(
        execute(fixture, {
          adapters: {
            generate: async () => {
              throw new Error('stop');
            },
          },
        }),
        (error) => error.rule === 'GENERATOR_FAILED',
      );
      assert.equal(await snapshotTree(fixture.target), before);
    });
  });

  await t.test('old-moved com target novo ja removido', async () => {
    await withFixture('recovery-old-moved-partial-rollback', async (fixture) => {
      const before = await snapshotTree(fixture.target);
      const operation = join(fixture.workspace, 'operations', FIXED_NONCE);
      const backup = join(operation, 'backup');
      await mkdir(dirname(backup), { recursive: true });
      await import('node:fs/promises').then(({ rename }) => rename(fixture.target, backup));
      await writeText(
        join(fixture.workspace, 'pipeline.journal.json'),
        `${JSON.stringify({ version: 1, nonce: FIXED_NONCE, state: 'old-moved', operation: `tmp/openapi-generator/operations/${FIXED_NONCE}`, candidate: `tmp/openapi-generator/operations/${FIXED_NONCE}/candidate`, backup: `tmp/openapi-generator/operations/${FIXED_NONCE}/backup`, target: 'src/app/api/generated', hadTarget: true, createdAt: FIXED_NOW })}\n`,
      );
      await assert.rejects(
        execute(fixture, {
          adapters: {
            generate: async () => {
              throw new Error('stop');
            },
          },
        }),
        (error) => error.rule === 'GENERATOR_FAILED',
      );
      assert.equal(await snapshotTree(fixture.target), before);
    });
  });
});

test('falha no segundo rename restaura a arvore anterior e preserva diagnostico', async () => {
  await withFixture('rename-rollback', async (fixture) => {
    const before = await snapshotTree(fixture.target);
    await assert.rejects(
      execute(fixture, {
        adapters: {
          beforeRename: async ({ role }) => {
            if (role === 'install-candidate') {
              throw new Error('simulated-second-rename-failure');
            }
          },
        },
      }),
      (error) => error.rule === 'PUBLISH_FAILED',
    );
    assert.equal(await snapshotTree(fixture.target), before);
    const diagnostics = await import('node:fs/promises').then(({ readdir }) =>
      readdir(join(fixture.workspace, 'diagnostics')),
    );
    assert.equal(diagnostics.length, 1);
  });
});

test('mutacao do candidato na janela de publicacao falha e restaura a arvore anterior', async () => {
  await withFixture('publish-manifest-race', async (fixture) => {
    await execute(fixture);
    const before = await readTreeManifest(fixture.target);

    await assert.rejects(
      execute(fixture, {
        adapters: {
          beforeRename: async ({ role, from }) => {
            if (role === 'install-candidate') {
              await writeText(join(from, 'tampered.ts'), 'export const tampered = true;\n');
            }
          },
        },
      }),
      (error) => error instanceof GenerateApiContractError && error.rule === 'PUBLISH_FAILED',
    );

    const after = await readTreeManifest(fixture.target);
    assert.equal(after.digest, before.digest);
    assert.deepEqual(after.files, before.files);
    assert.equal(after.files.includes('tampered.ts'), false);
  });
});

test('journal com traversal ou volume divergente falha fechado', async (t) => {
  await t.test('traversal', async () => {
    await withFixture('journal-traversal', async (fixture) => {
      await writeText(
        join(fixture.workspace, 'pipeline.journal.json'),
        `${JSON.stringify({ version: 1, nonce: FIXED_NONCE, state: 'prepared', operation: '../outside', candidate: '../outside/candidate', backup: '../outside/backup', target: 'src/app/api/generated', hadTarget: true, createdAt: FIXED_NOW })}\n`,
      );
      await assert.rejects(execute(fixture), (error) => error.rule === 'UNSAFE_PATH');
    });
  });
  await t.test('volume', async () => {
    await withFixture('volume-mismatch', async (fixture) => {
      await assert.rejects(
        execute(fixture, {
          adapters: {
            volumeOf: (path) =>
              path.includes(`${join('src', 'app', 'api', 'generated')}`)
                ? 'target-volume'
                : 'workspace-volume',
          },
        }),
        (error) => error.rule === 'VOLUME_MISMATCH',
      );
    });
  });
  await t.test('nonce divergente dos caminhos', async () => {
    await withFixture('journal-nonce-mismatch', async (fixture) => {
      const otherNonce = '11111111111111111111111111111111';
      await writeText(
        join(fixture.workspace, 'pipeline.journal.json'),
        `${JSON.stringify({ version: 1, nonce: FIXED_NONCE, state: 'prepared', operation: `tmp/openapi-generator/operations/${otherNonce}`, candidate: `tmp/openapi-generator/operations/${otherNonce}/candidate`, backup: `tmp/openapi-generator/operations/${otherNonce}/backup`, target: 'src/app/api/generated', hadTarget: true, createdAt: FIXED_NOW })}\n`,
      );
      await assert.rejects(execute(fixture), (error) => error.rule === 'UNSAFE_PATH');
    });
  });
});

test('rejeita junction ou symlink no caminho da arvore valida', async () => {
  await withFixture('symlink', async (fixture) => {
    const outside = join(fixture.root, 'outside-generated');
    await mkdir(outside, { recursive: true });
    await rm(fixture.target, { recursive: true, force: true });
    await symlink(outside, fixture.target, process.platform === 'win32' ? 'junction' : 'dir');
    await assert.rejects(execute(fixture), (error) => error.rule === 'UNSAFE_LINK');
  });
});

test('rejeita junction aninhada antes de substituir a arvore valida', async () => {
  await withFixture('nested-symlink', async (fixture) => {
    const outside = join(fixture.root, 'outside-nested');
    await mkdir(outside, { recursive: true });
    await symlink(
      outside,
      join(fixture.target, 'nested'),
      process.platform === 'win32' ? 'junction' : 'dir',
    );
    await assert.rejects(execute(fixture), (error) => error.rule === 'UNSAFE_LINK');
    assert.equal(
      await readFile(join(fixture.target, 'legacy.ts'), 'utf8'),
      'export const legacy = true;\n',
    );
  });
});

test('rejeita junction preexistente na area de operacoes', async () => {
  await withFixture('operations-symlink', async (fixture) => {
    const outside = join(fixture.root, 'outside-operations');
    const operations = join(fixture.workspace, 'operations');
    await mkdir(outside, { recursive: true });
    await mkdir(fixture.workspace, { recursive: true });
    await symlink(outside, operations, process.platform === 'win32' ? 'junction' : 'dir');
    await assert.rejects(execute(fixture), (error) => error.rule === 'UNSAFE_LINK');
    assert.deepEqual(await import('node:fs/promises').then(({ readdir }) => readdir(outside)), []);
  });
});

test('sucesso remove lock, journal e area da operacao corrente', async () => {
  await withFixture('cleanup', async (fixture) => {
    await execute(fixture);
    for (const path of [
      join(fixture.workspace, 'pipeline.lock'),
      join(fixture.workspace, 'pipeline.journal.json'),
      join(fixture.workspace, 'operations', FIXED_NONCE),
    ]) {
      await assert.rejects(stat(path), { code: 'ENOENT' });
    }
  });
});

test('duas geracoes sequenciais produzem o mesmo digest', async () => {
  await withFixture('deterministic', async (fixture) => {
    const first = await execute(fixture);
    const second = await execute(fixture);
    assert.equal(first.digest, second.digest);
  });
});

test('uso CLI invalido retorna 2 sem executar pipeline', () => {
  const result = spawnSync(process.execPath, [runnerPath, '--root', projectRoot], {
    cwd: projectRoot,
    encoding: 'utf8',
    shell: false,
    windowsHide: true,
    timeout: 10_000,
  });
  assert.ifError(result.error);
  assert.equal(result.status, 2);
  assert.equal(result.stdout, '');
  assert.match(result.stderr, /^Uso: node frontend\/scripts\/generate-api\.mjs \[--check\]/u);
});

test('erros inesperados sao sanitizados', () => {
  assert.equal(
    sanitizeError(new Error('C:\\sensitive\\SENSITIVE_CANARY_VALUE')),
    'INTERNAL_ERROR: falha interna no pipeline OpenAPI.',
  );
});
