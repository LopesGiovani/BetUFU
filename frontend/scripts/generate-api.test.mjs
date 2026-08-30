import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import {
  mkdir,
  mkdtemp,
  readdir,
  readFile,
  rm,
  stat,
  symlink,
  writeFile,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  APPROVED_GENERATED_DIGEST,
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
const RAW_BASE_SERVICE = "export class BaseService {\r\n    protected basePath = 'http://localhost';\r\n}\r\n";

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex').toUpperCase();
}

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

async function createFixture(label, { targetFiles = { 'legacy.ts': 'export const legacy = true;\n' } } = {}) {
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
      await writeText(join(outputDirectory, 'api', 'identity.service.ts'), 'export const line = 1;\r\n');
      await writeText(join(outputDirectory, 'model', 'current-identity-response.ts'), 'export interface CurrentIdentityResponse {}');
      await writeText(join(outputDirectory, 'README.md'), 'raw auxiliary output\n');
      await writeText(join(outputDirectory, 'package.json'), '{"scripts":{"prepare":"forbidden"}}\n');
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
  });
}

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
    () => hardenGeneratedTypeScript(
      'api.base.service.ts',
      "export class BaseService {\n    protected basePath = '';\n}\n",
    ),
    (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_BASE_PATH',
  );
  assert.throws(
    () => validateGeneratedTypeScript(
      'api/identity.service.ts',
      "const endpoint = 'http://localhost';\n",
    ),
    (error) => error instanceof GenerateApiContractError
      && error.rule === 'GENERATED_ABSOLUTE_URL',
  );
});

for (const [label, source] of [
  ['ausente', 'export class BaseService {}\n'],
  ['divergente', "export class BaseService { protected basePath = 'https://api.example.invalid'; }\n"],
  ['duplicado', "protected basePath = 'http://localhost';\nprotected basePath = 'http://localhost';\n"],
  ['comentado', "/*\n    protected basePath = 'http://localhost';\n*/\nexport class BaseService { protected basePath = '/api'; }\n"],
  ['outra-classe', "export class Decoy {\n    protected basePath = 'http://localhost';\n}\nexport class BaseService {}\n"],
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
    (error) => error instanceof GenerateApiContractError
      && error.rule === 'GENERATED_BASE_PATH',
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
      (error) => error instanceof GenerateApiContractError
        && error.rule === 'GENERATED_ABSOLUTE_URL',
    );
  });
}

test('manifesto recusa bytes com URL absoluta escondida depois de regex literal', () => {
  const maliciousSource = "const matcher = /https?:\\/\\//; const endpoint = 'https://evil.invalid';\n";
  assert.throws(
    () => assertApprovedGeneratedManifest({
      files: APPROVED_GENERATED_FILES,
      digest: sha256(Buffer.from(maliciousSource, 'utf8')),
    }),
    (error) => error instanceof GenerateApiContractError
      && error.rule === 'GENERATED_MANIFEST',
  );
});

test('geracao seleciona catalogo, organizacoes, atletas, SportsPublic e schemas compartilhados', () => {
  assert.deepEqual(PUBLIC_GENERATOR_API_TAGS, [
    'AthleteLink',
    'Diagnostic',
    'Enrollment',
    'Identity',
    'OperationalCapabilities',
    'OperationalPanel',
    'PasswordResetRecovery',
    'Profile',
    'Role',
    'Session',
    'SportsAthlete',
    'SportsCatalog',
    'SportsOrganization',
    'SportsPublic',
  ]);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('FavoriteOrganizationRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('PublicSportsEventPage'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsEventFormat'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsCatalogScope'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsVenueSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsOrganizationSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsTeamSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsAthleteCreateRequest'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsAthleteSummary'), true);
  assert.equal(PUBLIC_GENERATOR_MODELS.includes('SportsCompetitionCreateRequest'), false);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsAthlete'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsCatalog'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsOrganization'), true);
  assert.equal(PUBLIC_GENERATOR_GLOBAL_PROPERTY.includes('SportsOperations'), false);
});

test('manifesto aprovado identifica exatamente a arvore TypeScript versionada', async () => {
  const generatedRoot = join(projectRoot, 'frontend', 'src', 'app', 'api', 'generated');
  const manifest = await readTreeManifest(generatedRoot);

  assert.deepEqual(manifest.files, APPROVED_GENERATED_FILES);
  assert.equal(manifest.digest, APPROVED_GENERATED_DIGEST);
  assert.equal(assertApprovedGeneratedManifest(manifest), true);
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
      (error) => error instanceof GenerateApiContractError
        && error.rule === 'GENERATED_MANIFEST',
    );
  }
});

test('inspeciona endpoints e schemas do contrato sem depender do gerador', () => {
  const inventory = inspectOpenApiContract([
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
  ].join('\n'));

  assert.deepEqual(inventory.endpoints, [{
    path: '/api/v1/public/sports/events',
    methods: ['GET'],
  }]);
  assert.deepEqual(inventory.schemas, ['PublicSportsEventPage', 'SportsEventFormat']);
  assert.deepEqual(inventory.publicSchemas, ['PublicSportsEventPage']);
});

test('inventario gerado exclui comandos do tracer e preserva o catalogo escopado', () => {
  const inventory = inspectGeneratedOpenApiContract([
    'openapi: 3.1.0',
    'paths:',
    '  /api/v1/operations/sports/competitions:',
    '    post:',
    '      operationId: createSportsCompetition',
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
    '  /api/v1/operations/sports/athletes:',
    '    get:',
    '      operationId: listSportsAthletes',
    '  /api/v1/operations/sports/teams/{teamId}/athletes:',
    '    post:',
    '      operationId: createSportsAthlete',
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
    '',
  ].join('\n'));

  assert.deepEqual(inventory, {
    endpoints: [
      { path: '/api/v1/operations/sports/catalog/scope', methods: ['GET'] },
      { path: '/api/v1/public/sports/events', methods: ['GET'] },
      { path: '/api/v1/operations/sports/organizations', methods: ['GET', 'POST'] },
      { path: '/api/v1/operations/sports/teams/{teamId}/publish', methods: ['POST'] },
      { path: '/api/v1/operations/sports/athletes', methods: ['GET'] },
      { path: '/api/v1/operations/sports/teams/{teamId}/athletes', methods: ['POST'] },
    ],
    schemas: [
      'FavoriteOrganizationRequest',
      'PublicSportsEventPage',
      'SportsAthleteCreateRequest',
      'SportsAthleteSummary',
      'SportsOrganizationSummary',
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
          await writeText(join(outputDirectory, 'api', 'identity.service.ts'), 'export const line = 1;\n');
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
    const inventory = JSON.parse(await readFile(join(candidateDirectory, 'inventory.json'), 'utf8'));
    assert.equal(inventory.digest, result.digest);
    assert.deepEqual(inventory.files, result.files);
    assert.equal(await readFile(join(candidateDirectory, 'digest.sha256'), 'utf8'), `${result.digest}\n`);
    assert.equal(
      await readFile(join(candidateDirectory, 'governed-before.sha256'), 'utf8'),
      `${governedBefore.digest}\n`,
    );
    assert.equal(
      await readFile(join(candidateDirectory, 'governed-after.sha256'), 'utf8'),
      `${governedBefore.digest}\n`,
    );
    assert.equal((await readdir(candidateDirectory)).some((entry) => entry.endsWith('.ts')), false);
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
      (error) => error instanceof GenerateApiContractError
        && error.rule === 'PUBLIC_SCHEMA_FORBIDDEN_FIELD'
        && error.message.includes('matricula'),
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
            await writeText(join(outputDirectory, 'api', 'identity.service.ts'), 'export const line = 1;\n');
            await writeText(join(outputDirectory, 'model', 'current-identity-response.ts'), 'export interface CurrentIdentityResponse {}\n');
            await writeText(
              join(outputDirectory, 'api', 'extra.service.ts'),
              "export const extraService = true;\n",
            );
          },
        },
      }),
      (error) => error instanceof GenerateApiContractError
        && error.rule === 'GENERATED_MANIFEST',
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
    assert.equal(await readFile(join(fixture.target, 'api', 'identity.service.ts'), 'utf8'), 'export const line = 1;\n');
    assert.equal(await readFile(join(fixture.target, 'model', 'current-identity-response.ts'), 'utf8'), 'export interface CurrentIdentityResponse {}\n');
    await assert.rejects(stat(join(fixture.target, 'legacy.ts')), { code: 'ENOENT' });
    await assert.rejects(stat(join(fixture.target, 'README.md')), { code: 'ENOENT' });
    await assert.rejects(stat(join(fixture.target, 'package.json')), { code: 'ENOENT' });
  });
});

test('fallback ausente ou divergente nunca substitui a arvore valida', async (t) => {
  for (const scenario of [
    { label: 'missing', source: null },
    { label: 'unexpected', source: "export class BaseService { protected basePath = 'https://api.example.invalid'; }\n" },
    { label: 'duplicate', source: "protected basePath = 'http://localhost';\nprotected basePath = 'http://localhost';\n" },
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
                await writeText(join(outputDirectory, 'api', 'identity.service.ts'), 'export const line = 1;\n');
              },
            },
          }),
          (error) => error instanceof GenerateApiContractError && error.rule === 'GENERATED_BASE_PATH',
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
      (error) => error instanceof GenerateApiContractError
        && error.rule === 'GENERATED_ABSOLUTE_URL',
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
        await writeText(join(outputDirectory, 'api', 'identity.service.ts'), drift === 'change' ? 'export const line = 2;\n' : 'export const line = 1;\n');
        if (drift !== 'delete') {
          await writeText(join(outputDirectory, 'model', 'current-identity-response.ts'), 'export interface CurrentIdentityResponse {}\n');
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
      const replacement = { version: 1, pid: 777, nonce: '77777777777777777777777777777777', createdAt: FIXED_NOW };
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
          processStatus: async (pid) => pid === 1111 ? 'alive' : 'ambiguous',
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
    await assert.rejects(execute(fixture, { mode: 'check' }), (error) => error.rule === 'RECOVERY_REQUIRED');
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
        await assert.rejects(stat(join(fixture.workspace, 'pipeline.journal.json')), { code: 'ENOENT' });
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
        await assert.rejects(stat(join(fixture.workspace, 'pipeline.journal.json')), { code: 'ENOENT' });
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
        await assert.rejects(stat(join(fixture.workspace, 'pipeline.journal.json')), { code: 'ENOENT' });
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
        execute(fixture, { adapters: { generate: async () => { throw new Error('stop'); } } }),
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
        execute(fixture, { adapters: { generate: async () => { throw new Error('stop'); } } }),
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
    const diagnostics = await import('node:fs/promises').then(({ readdir }) => readdir(join(fixture.workspace, 'diagnostics')));
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
      (error) => error instanceof GenerateApiContractError
        && error.rule === 'PUBLISH_FAILED',
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
            volumeOf: (path) => path.includes(`${join('src', 'app', 'api', 'generated')}`) ? 'target-volume' : 'workspace-volume',
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
    await symlink(outside, join(fixture.target, 'nested'), process.platform === 'win32' ? 'junction' : 'dir');
    await assert.rejects(execute(fixture), (error) => error.rule === 'UNSAFE_LINK');
    assert.equal(await readFile(join(fixture.target, 'legacy.ts'), 'utf8'), 'export const legacy = true;\n');
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
