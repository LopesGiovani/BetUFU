import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptsDirectory, '..');
const validatorPath = join(scriptsDirectory, 'validate-angular-route-boundary.mjs');

const FIXTURE_PATHS = Object.freeze([
  'frontend/angular.json',
  'frontend/src/main.ts',
  'frontend/src/app/app.config.ts',
  'frontend/src/app/app.routes.ts',
  'frontend/src/app/app.routes.development.ts',
  'frontend/src/app/app.routes.production.ts',
  'frontend/src/app/app.component.ts',
  'frontend/src/app/app.component.html',
]);

const canonicalFiles = new Map(
  await Promise.all(
    FIXTURE_PATHS.map(async (relativePath) => [
      relativePath,
      (await readFile(join(projectRoot, relativePath), 'utf8')).replaceAll('\r\n', '\n'),
    ]),
  ),
);

function runValidator(fixtureRoot) {
  const argumentsList = [validatorPath];
  if (fixtureRoot !== undefined) {
    argumentsList.push('--root', fixtureRoot);
  }

  const result = spawnSync(process.execPath, argumentsList, {
    cwd: projectRoot,
    encoding: 'utf8',
    shell: false,
    timeout: 10_000,
    windowsHide: true,
  });

  assert.ifError(result.error);
  return result;
}

function runValidatorWithArguments(argumentsList) {
  const result = spawnSync(process.execPath, [validatorPath, ...argumentsList], {
    cwd: projectRoot,
    encoding: 'utf8',
    shell: false,
    timeout: 10_000,
    windowsHide: true,
  });

  assert.ifError(result.error);
  return result;
}

async function populateFixture(fixtureRoot) {
  for (const [relativePath, contents] of canonicalFiles) {
    const fixturePath = join(fixtureRoot, relativePath);
    await mkdir(dirname(fixturePath), { recursive: true });
    await writeFile(fixturePath, contents, 'utf8');
  }
}

async function withFixture(label, callback) {
  const safeLabel = label.replaceAll(/[^a-z0-9-]/giu, '-').toLowerCase();
  const fixtureRoot = await mkdtemp(
    join(tmpdir(), `betufu-angular-route-boundary-${safeLabel}-`),
  );

  try {
    await populateFixture(fixtureRoot);
    return await callback(fixtureRoot);
  } finally {
    await rm(fixtureRoot, { recursive: true, force: true });
  }
}

async function mutateText(fixtureRoot, relativePath, mutation) {
  const fixturePath = join(fixtureRoot, relativePath);
  const original = await readFile(fixturePath, 'utf8');
  const mutated = mutation(original);
  assert.notEqual(mutated, original, `A mutacao de ${relativePath} nao alterou a fixture.`);
  await writeFile(fixturePath, mutated, 'utf8');
}

async function mutateAngularJson(fixtureRoot, mutation) {
  const fixturePath = join(fixtureRoot, 'frontend/angular.json');
  const document = JSON.parse(await readFile(fixturePath, 'utf8'));
  mutation(document.projects.betufu.architect.build.configurations);
  await writeFile(fixturePath, `${JSON.stringify(document, null, 2)}\n`, 'utf8');
}

function expectValidationFailure(result, rule) {
  assert.equal(result.status, 1, result.stdout);
  assert.equal(result.stdout, '');
  assert.match(
    result.stderr,
    new RegExp(`^\\[validate-angular-route-boundary\\] ERRO: ${rule}:`, 'u'),
  );
}

test('aceita a fronteira canonica do repositorio', () => {
  const result = runValidator();

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /\[validate-angular-route-boundary\] OK:/u);
});

test('aceita uma fixture canonica isolada', async () => {
  await withFixture('canonical', async (fixtureRoot) => {
    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /\[validate-angular-route-boundary\] OK:/u);
  });
});

test('aceita file replacements nao relacionados em development e homolog', async () => {
  await withFixture('unrelated-replacements', async (fixtureRoot) => {
    await mutateAngularJson(fixtureRoot, (configurations) => {
      const unrelatedReplacement = {
        replace: 'src/environments/runtime.ts',
        with: 'src/environments/runtime.local.ts',
      };
      configurations.development.fileReplacements = [unrelatedReplacement];
      configurations.homolog.fileReplacements = [unrelatedReplacement];
    });

    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
  });
});

test('retorna codigo 2 para argumentos invalidos', () => {
  const result = runValidatorWithArguments(['--unexpected']);

  assert.equal(result.status, 2, result.stdout);
  assert.match(result.stderr, /^Uso: node scripts\/validate-angular-route-boundary\.mjs/u);
});

test('nao expoe conteudo de angular.json malformado', async () => {
  await withFixture('malformed-json', async (fixtureRoot) => {
    const fixturePath = join(fixtureRoot, 'frontend/angular.json');
    await writeFile(fixturePath, '{"secret":SENSITIVE_CANARY_VALUE}\n', 'utf8');

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'ANGULAR_JSON');
    assert.doesNotMatch(result.stderr, /SENSITIVE_CANARY_VALUE/u);
    assert.doesNotMatch(result.stderr, new RegExp(fixtureRoot.replaceAll('\\', '\\\\'), 'u'));
  });
});

const replacementMutations = [
  {
    name: 'replacement production ausente',
    rule: 'PRODUCTION_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      delete configurations.production.fileReplacements;
    },
  },
  {
    name: 'replacement production invertido',
    rule: 'PRODUCTION_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.production.fileReplacements = [
        {
          replace: 'src/app/app.routes.production.ts',
          with: 'src/app/app.routes.development.ts',
        },
      ];
    },
  },
  {
    name: 'replacement indevido em homolog',
    rule: 'HOMOLOG_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.homolog.fileReplacements = [
        {
          replace: 'src/app/app.routes.development.ts',
          with: 'src/app/app.routes.production.ts',
        },
      ];
    },
  },
  {
    name: 'replacement indevido em development',
    rule: 'DEVELOPMENT_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.development.fileReplacements = [
        {
          replace: 'src/app/app.routes.development.ts',
          with: 'src/app/app.routes.production.ts',
        },
      ];
    },
  },
  {
    name: 'replacement alias com ponto em homolog',
    rule: 'HOMOLOG_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.homolog.fileReplacements = [
        {
          replace: 'src/app/./app.routes.development.ts',
          with: 'src/app/app.routes.production.ts',
        },
      ];
    },
  },
  {
    name: 'replacement alias com barras Windows em development',
    rule: 'DEVELOPMENT_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.development.fileReplacements = [
        {
          replace: 'src\\app\\app.routes.development.ts',
          with: 'src\\app\\app.routes.production.ts',
        },
      ];
    },
  },
];

for (const replacementMutation of replacementMutations) {
  test(`recusa ${replacementMutation.name}`, async () => {
    await withFixture(replacementMutation.name, async (fixtureRoot) => {
      await mutateAngularJson(fixtureRoot, replacementMutation.mutate);

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, replacementMutation.rule);
    });
  });
}

const sourceMutations = [
  {
    name: 'import direto de production',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'DIRECT_PRODUCTION_IMPORT',
    mutate: (source) => source.replace(
      "from './app.routes.development'",
      "from './app.routes.production'",
    ),
  },
  {
    name: 'simbolo development divergente',
    path: 'frontend/src/app/app.routes.development.ts',
    rule: 'DEVELOPMENT_ROUTES_EXPORT',
    mutate: (source) => source.replace('BUILD_SPECIFIC_ROUTES', 'OTHER_ROUTES'),
  },
  {
    name: 'provideRouter ausente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'ROUTER_PROVIDER',
    mutate: (source) => source.replace(/\s*provideRouter\(APP_ROUTES\),/u, ''),
  },
  {
    name: 'provideHttpClient ausente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'HTTP_PROVIDER',
    mutate: (source) => source.replace(/\s*provideHttpClient\([\s\S]*?\n\s*\),\n/u, '\n'),
  },
  {
    name: 'cookie XSRF divergente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'XSRF_COOKIE',
    mutate: (source) => source.replace('XSRF-TOKEN', 'WRONG-XSRF-TOKEN'),
  },
  {
    name: 'header XSRF divergente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'XSRF_HEADER',
    mutate: (source) => source.replace('X-XSRF-TOKEN', 'WRONG-X-XSRF-TOKEN'),
  },
  {
    name: 'XSRF desabilitado',
    path: 'frontend/src/app/app.config.ts',
    rule: 'XSRF_DISABLED',
    mutate: (source) => `${source}\nwithNoXsrfProtection();\n`,
  },
  {
    name: 'URL absoluta',
    path: 'frontend/src/app/app.config.ts',
    rule: 'ABSOLUTE_HTTP_URL',
    mutate: (source) => `${source}\nconst backend = 'https://api.example.invalid';\n`,
  },
  {
    name: 'header Authorization',
    path: 'frontend/src/app/app.config.ts',
    rule: 'AUTHORIZATION_HEADER',
    mutate: (source) => `${source}\nconst headerName = 'Authorization';\n`,
  },
  {
    name: 'bearer token',
    path: 'frontend/src/app/app.config.ts',
    rule: 'BEARER_TOKEN',
    mutate: (source) => `${source}\nconst scheme = 'Bearer';\n`,
  },
  {
    name: 'localStorage',
    path: 'frontend/src/app/app.config.ts',
    rule: 'LOCAL_STORAGE',
    mutate: (source) => `${source}\nlocalStorage.setItem('token', 'value');\n`,
  },
  {
    name: 'sessionStorage',
    path: 'frontend/src/app/app.config.ts',
    rule: 'SESSION_STORAGE',
    mutate: (source) => `${source}\nsessionStorage.setItem('token', 'value');\n`,
  },
  {
    name: 'indexedDB',
    path: 'frontend/src/app/app.config.ts',
    rule: 'INDEXED_DB',
    mutate: (source) => `${source}\nindexedDB.open('tokens');\n`,
  },
  {
    name: 'HTTP_INTERCEPTORS',
    path: 'frontend/src/app/app.config.ts',
    rule: 'LEGACY_HTTP_INTERCEPTOR',
    mutate: (source) => `${source}\nconst legacy = HTTP_INTERCEPTORS;\n`,
  },
  {
    name: 'withInterceptors',
    path: 'frontend/src/app/app.config.ts',
    rule: 'HTTP_INTERCEPTOR',
    mutate: (source) => `${source}\nwithInterceptors([]);\n`,
  },
  {
    name: 'withInterceptorsFromDi',
    path: 'frontend/src/app/app.config.ts',
    rule: 'HTTP_INTERCEPTOR',
    mutate: (source) => `${source}\nwithInterceptorsFromDi();\n`,
  },
  {
    name: 'import dinamico de production',
    path: 'frontend/src/app/app.component.ts',
    rule: 'DIRECT_PRODUCTION_IMPORT',
    mutate: (source) => `${source}\nvoid import('./app.routes.production');\n`,
  },
  {
    name: 'import dinamico de production com template literal',
    path: 'frontend/src/app/app.component.ts',
    rule: 'DIRECT_PRODUCTION_IMPORT',
    mutate: (source) => `${source}\nvoid import(\`./app.routes.production\`);\n`,
  },
  {
    name: 'mutacao posterior da lista production',
    path: 'frontend/src/app/app.routes.production.ts',
    rule: 'PRODUCTION_ROUTES_EMPTY',
    mutate: (source) => `${source}\nBUILD_SPECIFIC_ROUTES.push({ path: 'technical', redirectTo: '' });\n`,
  },
  {
    name: 'mutacao posterior da lista final APP_ROUTES',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'APP_ROUTES_USAGE',
    mutate: (source) => `${source}\nAPP_ROUTES.push({ path: 'technical', redirectTo: '' });\n`,
  },
  {
    name: 'spread posterior nas opcoes XSRF',
    path: 'frontend/src/app/app.config.ts',
    rule: 'XSRF_OPTIONS_SHAPE',
    mutate: (source) => source
      .replace(
        'export const appConfig',
        "const xsrfOverrides = { cookieName: 'OVERRIDDEN-XSRF-TOKEN' };\n\nexport const appConfig",
      )
      .replace(
        "headerName: 'X-XSRF-TOKEN',",
        "headerName: 'X-XSRF-TOKEN',\n        ...xsrfOverrides,",
      ),
  },
];

for (const sourceMutation of sourceMutations) {
  test(`recusa ${sourceMutation.name}`, async () => {
    await withFixture(sourceMutation.name, async (fixtureRoot) => {
      await mutateText(
        fixtureRoot,
        sourceMutation.path,
        sourceMutation.mutate,
      );

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, sourceMutation.rule);
    });
  });
}

test('ignora marcadores proibidos presentes somente em comentarios', async () => {
  await withFixture('comments-ignored', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.config.ts',
      (source) => `${source}\n/* https://example.invalid Authorization Bearer localStorage sessionStorage indexedDB HTTP_INTERCEPTORS withInterceptors withNoXsrfProtection */\n`,
    );

    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
  });
});

test('nao aceita provider obrigatorio presente somente em comentario', async () => {
  await withFixture('required-code-comment', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.config.ts',
      (source) => `${source.replace(/\s*provideRouter\(APP_ROUTES\),/u, '')}\n// provideRouter(APP_ROUTES)\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'ROUTER_PROVIDER');
  });
});

test('nao aceita provider obrigatorio presente somente em string', async () => {
  await withFixture('required-code-string', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.config.ts',
      (source) => `${source.replace(/\s*provideRouter\(APP_ROUTES\),/u, '')}\nconst fakeProvider = 'provideRouter(APP_ROUTES)';\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'ROUTER_PROVIDER');
  });
});

test('nao aceita export de rota presente somente em string', async () => {
  await withFixture('required-export-string', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.routes.ts',
      (source) => `${source.replace(/export const APP_ROUTES:[^;]+;/u, '')}\nconst fakeExport = 'export const APP_ROUTES: Routes = [...BUILD_SPECIFIC_ROUTES];';\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'APP_ROUTES_EXPORT');
  });
});
