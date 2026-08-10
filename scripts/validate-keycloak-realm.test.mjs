import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test, { after } from 'node:test';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptsDirectory, '..');
const validatorPath = join(scriptsDirectory, 'validate-keycloak-realm.mjs');
const canonicalRealmPath = join(projectRoot, 'infra', 'keycloak', 'realm-betufu.json');
const canonicalRealm = JSON.parse(await readFile(canonicalRealmPath, 'utf8'));
const fixtureDirectory = await mkdtemp(join(tmpdir(), 'betufu-keycloak-realm-'));

after(async () => {
  await rm(fixtureDirectory, { recursive: true, force: true });
});

function runValidator(realmPath) {
  const result = spawnSync(process.execPath, [validatorPath, realmPath], {
    cwd: projectRoot,
    encoding: 'utf8',
    shell: false,
    windowsHide: true,
  });

  assert.ifError(result.error);
  return result;
}

test('aceita o realm canonico', () => {
  const result = runValidator(canonicalRealmPath);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /\[validate-keycloak-realm\] OK:/u);
});

test('nao expoe conteudo de JSON malformado', async () => {
  const fixturePath = join(fixtureDirectory, 'malformed-sensitive.json');
  await writeFile(fixturePath, '{"secret":SENSITIVE_CANARY_VALUE}\n', 'utf8');

  const result = runValidator(fixturePath);

  assert.equal(result.status, 1, result.stdout);
  assert.equal(
    result.stderr.trim(),
    '[validate-keycloak-realm] ERRO: nao foi possivel ler JSON valido.',
  );
});

const unsafeMutations = [
  {
    name: 'redirect com wildcard',
    mutate: (realm) => {
      realm.clients[0].redirectUris = ['http://localhost:4200/*'];
    },
  },
  {
    name: 'redirect com sinal de mais',
    mutate: (realm) => {
      realm.clients[0].redirectUris = ['http://localhost:4200/+'];
    },
  },
  {
    name: 'redirect com prefixo indevido',
    mutate: (realm) => {
      realm.clients[0].redirectUris = [
        'http://localhost:4200/login/oauth2/code/keycloak/extra',
      ];
    },
  },
  {
    name: 'web origin com wildcard',
    mutate: (realm) => {
      realm.clients[0].webOrigins = ['*'];
    },
  },
  {
    name: 'post logout divergente',
    mutate: (realm) => {
      realm.clients[0].attributes['post.logout.redirect.uris'] = 'http://localhost:4200/*';
    },
  },
  {
    name: 'direct grant habilitado',
    mutate: (realm) => {
      realm.clients[0].directAccessGrantsEnabled = true;
    },
  },
  {
    name: 'implicit flow habilitado',
    mutate: (realm) => {
      realm.clients[0].implicitFlowEnabled = true;
    },
  },
  {
    name: 'authorization code desabilitado',
    mutate: (realm) => {
      realm.clients[0].standardFlowEnabled = false;
    },
  },
  {
    name: 'cliente publico',
    mutate: (realm) => {
      realm.clients[0].publicClient = true;
    },
  },
  {
    name: 'service account habilitada',
    mutate: (realm) => {
      realm.clients[0].serviceAccountsEnabled = true;
    },
  },
  {
    name: 'authorization services habilitado',
    mutate: (realm) => {
      realm.clients[0].authorizationServicesEnabled = true;
    },
  },
  {
    name: 'PKCE divergente',
    mutate: (realm) => {
      realm.clients[0].attributes['pkce.code.challenge.method'] = 'plain';
    },
  },
  {
    name: 'escopo padrao divergente',
    mutate: (realm) => {
      realm.clients[0].defaultClientScopes.push('roles');
    },
  },
  {
    name: 'escopo opcional divergente',
    mutate: (realm) => {
      realm.clients[0].optionalClientScopes.push('offline_access');
    },
  },
  {
    name: 'full scope habilitado',
    mutate: (realm) => {
      realm.clients[0].fullScopeAllowed = true;
    },
  },
  {
    name: 'required action na conta tecnica',
    mutate: (realm) => {
      realm.users[0].requiredActions.push('CONFIGURE_TOTP');
    },
  },
  {
    name: 'realm role declarado',
    mutate: (realm) => {
      realm.roles.realm.push({ name: 'ADMIN' });
    },
  },
  {
    name: 'realm role atribuido a conta tecnica',
    mutate: (realm) => {
      realm.users[0].realmRoles.push('ADMIN');
    },
  },
  {
    name: 'client role adicional',
    mutate: (realm) => {
      realm.roles.client['betufu-bff'].push({
        name: 'ADMIN',
        composite: false,
        clientRole: true,
      });
    },
  },
  {
    name: 'papel adicional atribuido a conta tecnica',
    mutate: (realm) => {
      realm.users[0].clientRoles['betufu-bff'].push('ADMIN');
    },
  },
  {
    name: 'papel adicional no scope mapping',
    mutate: (realm) => {
      realm.clientScopeMappings['betufu-bff'][0].roles.push('ADMIN');
    },
  },
  {
    name: 'claim ausente',
    mutate: (realm) => {
      realm.clients[0].protocolMappers = [];
    },
  },
  {
    name: 'claim exposta no access token',
    mutate: (realm) => {
      realm.clients[0].protocolMappers[0].config['access.token.claim'] = 'true';
    },
  },
  {
    name: 'senha temporaria',
    mutate: (realm) => {
      realm.users[0].credentials[0].temporary = true;
    },
  },
  {
    name: 'cliente duplicado',
    mutate: (realm) => {
      realm.clients.push(structuredClone(realm.clients[0]));
    },
  },
];

for (const [index, unsafeMutation] of unsafeMutations.entries()) {
  test(`recusa ${unsafeMutation.name}`, async () => {
    const mutatedRealm = structuredClone(canonicalRealm);
    unsafeMutation.mutate(mutatedRealm);

    const fixturePath = join(fixtureDirectory, `unsafe-${index}.json`);
    await writeFile(fixturePath, `${JSON.stringify(mutatedRealm, null, 2)}\n`, 'utf8');

    const result = runValidator(fixturePath);

    assert.equal(result.status, 1, result.stdout);
    assert.match(result.stderr, /\[validate-keycloak-realm\] ERRO:/u);
  });
}
