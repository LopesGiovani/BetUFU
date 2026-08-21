#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';

const EXPECTED = Object.freeze({
  realm: 'betufu-local',
  clientId: 'betufu-bff',
  technicalRole: 'TECHNICAL',
  technicalUserId: '00000000-0000-0000-0000-000000000101',
  technicalUsername: 'technical',
  technicalEmail: 'technical@betufu.local',
  // Perfil completo exigido pelo provider VERIFY_PROFILE do Keycloak, que sem
  // isso bloqueia o login da conta tecnica. Introduzidos pelo Plano 01-29 sem
  // violar o criterio do 01-03, porque VERIFY_PROFILE e required action de
  // provider e nao de usuario. Pinados aqui pelo mesmo motivo dos demais
  // campos: detectar drift, nao apenas presenca.
  technicalFirstName: 'BETUFU',
  technicalLastName: 'Technical',
  redirectUris: ['http://localhost:4200/login/oauth2/code/keycloak'],
  postLogoutRedirectUris: 'http://localhost:4200/',
  webOrigins: ['http://localhost:4200'],
  defaultClientScopes: ['web-origins', 'acr', 'basic', 'profile', 'email'],
  optionalClientScopes: [],
  clientSecretPlaceholder: '${BETUFU_OIDC_CLIENT_SECRET}',
  technicalPasswordPlaceholder: '${BETUFU_TECHNICAL_PASSWORD}',
});

function contractError(message) {
  throw new Error(message);
}

function expect(condition, message) {
  if (!condition) {
    contractError(message);
  }
}

function expectPlainObject(value, label) {
  expect(
    value !== null && typeof value === 'object' && !Array.isArray(value),
    `${label} deve ser um objeto.`,
  );
}

function expectExact(value, expected, label) {
  expect(
    isDeepStrictEqual(value, expected),
    `${label} nao corresponde ao contrato aprovado.`,
  );
}

function expectExactKeys(value, expectedKeys, label) {
  expectPlainObject(value, label);
  expectExact(
    Object.keys(value).sort(),
    [...expectedKeys].sort(),
    `${label}: chaves`,
  );
}

function expectSingleEntry(value, label) {
  expect(Array.isArray(value), `${label} deve ser uma lista.`);
  expect(value.length === 1, `${label} deve conter exatamente uma entrada.`);
  return value[0];
}

export function validateRealmDocument(realm) {
  expect(
    realm?.registrationAllowed === true
      && realm?.registrationEmailAsUsername === true
      && (!('identityProviders' in (realm ?? {}))
        || (Array.isArray(realm?.identityProviders) && realm.identityProviders.length === 0)),
    'KEYCLOAK_SELF_REGISTRATION: autocadastro deve estar habilitado, com email como username, e o realm nao pode declarar provedores de identidade externos.',
  );
  expect(
    realm?.verifyEmail === true,
    'KEYCLOAK_EMAIL_VERIFICATION: confirmacao de email deve ser obrigatoria.',
  );

  expectExactKeys(
    realm,
    [
      'realm',
      'enabled',
      'registrationAllowed',
      'registrationEmailAsUsername',
      'resetPasswordAllowed',
      'verifyEmail',
      'loginTheme',
      'smtpServer',
      'roles',
      'clients',
      'clientScopeMappings',
      'users',
      'components',
    ],
    'realm',
  );

  expectExact(realm.realm, EXPECTED.realm, 'realm.realm');
  expectExact(realm.enabled, true, 'realm.enabled');
  expectExact(realm.registrationAllowed, true, 'realm.registrationAllowed');
  expectExact(realm.registrationEmailAsUsername, true, 'realm.registrationEmailAsUsername');
  expectExact(realm.resetPasswordAllowed, true, 'realm.resetPasswordAllowed');
  expectExact(realm.verifyEmail, true, 'realm.verifyEmail');
  expectExact(realm.loginTheme, 'betufu', 'realm.loginTheme');

  expectExactKeys(
    realm.smtpServer,
    ['host', 'port', 'from', 'fromDisplayName', 'auth', 'ssl', 'starttls'],
    'realm.smtpServer',
  );
  expectExact(
    realm.smtpServer,
    {
      host: 'mailpit',
      port: '1025',
      from: 'no-reply@betufu.local',
      fromDisplayName: 'BETUFU',
      auth: 'false',
      ssl: 'false',
      starttls: 'false',
    },
    'realm.smtpServer',
  );

  expectExactKeys(realm.roles, ['realm', 'client'], 'realm.roles');
  expectExact(realm.roles.realm, [], 'realm.roles.realm');
  expectExactKeys(realm.roles.client, [EXPECTED.clientId], 'realm.roles.client');

  const technicalRole = expectSingleEntry(
    realm.roles.client[EXPECTED.clientId],
    `realm.roles.client.${EXPECTED.clientId}`,
  );
  expectExactKeys(
    technicalRole,
    ['name', 'composite', 'clientRole'],
    'client role TECHNICAL',
  );
  expectExact(technicalRole.name, EXPECTED.technicalRole, 'client role name');
  expectExact(technicalRole.composite, false, 'client role composite');
  expectExact(technicalRole.clientRole, true, 'client role clientRole');

  const client = expectSingleEntry(realm.clients, 'realm.clients');
  expectExactKeys(
    client,
    [
      'clientId',
      'enabled',
      'protocol',
      'publicClient',
      'clientAuthenticatorType',
      'secret',
      'standardFlowEnabled',
      'implicitFlowEnabled',
      'directAccessGrantsEnabled',
      'serviceAccountsEnabled',
      'authorizationServicesEnabled',
      'redirectUris',
      'webOrigins',
      'fullScopeAllowed',
      'defaultClientScopes',
      'optionalClientScopes',
      'attributes',
      'protocolMappers',
    ],
    'realm.clients[0]',
  );
  expectExact(client.clientId, EXPECTED.clientId, 'client.clientId');
  expectExact(client.enabled, true, 'client.enabled');
  expectExact(client.protocol, 'openid-connect', 'client.protocol');
  expectExact(client.publicClient, false, 'client.publicClient');
  expectExact(client.clientAuthenticatorType, 'client-secret', 'client.clientAuthenticatorType');
  expectExact(client.secret, EXPECTED.clientSecretPlaceholder, 'client.secret');
  expectExact(client.standardFlowEnabled, true, 'client.standardFlowEnabled');
  expectExact(client.implicitFlowEnabled, false, 'client.implicitFlowEnabled');
  expectExact(client.directAccessGrantsEnabled, false, 'client.directAccessGrantsEnabled');
  expectExact(client.serviceAccountsEnabled, false, 'client.serviceAccountsEnabled');
  expectExact(client.authorizationServicesEnabled, false, 'client.authorizationServicesEnabled');
  expectExact(client.redirectUris, EXPECTED.redirectUris, 'client.redirectUris');
  expectExact(client.webOrigins, EXPECTED.webOrigins, 'client.webOrigins');
  expectExact(client.fullScopeAllowed, false, 'client.fullScopeAllowed');
  expectExact(client.defaultClientScopes, EXPECTED.defaultClientScopes, 'client.defaultClientScopes');
  expectExact(client.optionalClientScopes, EXPECTED.optionalClientScopes, 'client.optionalClientScopes');

  expectExactKeys(
    client.attributes,
    ['pkce.code.challenge.method', 'post.logout.redirect.uris'],
    'client.attributes',
  );
  expectExact(
    client.attributes['pkce.code.challenge.method'],
    'S256',
    'client PKCE method',
  );
  expectExact(
    client.attributes['post.logout.redirect.uris'],
    EXPECTED.postLogoutRedirectUris,
    'client post-logout redirect URI',
  );

  const mapper = expectSingleEntry(client.protocolMappers, 'client.protocolMappers');
  expectExactKeys(
    mapper,
    ['name', 'protocol', 'protocolMapper', 'consentRequired', 'config'],
    'client.protocolMappers[0]',
  );
  expectExact(mapper.name, 'betufu-client-roles', 'mapper.name');
  expectExact(mapper.protocol, 'openid-connect', 'mapper.protocol');
  expectExact(
    mapper.protocolMapper,
    'oidc-usermodel-client-role-mapper',
    'mapper.protocolMapper',
  );
  expectExact(mapper.consentRequired, false, 'mapper.consentRequired');
  expectExactKeys(
    mapper.config,
    [
      'usermodel.clientRoleMapping.clientId',
      'multivalued',
      'claim.name',
      'jsonType.label',
      'id.token.claim',
      'access.token.claim',
      'userinfo.token.claim',
      'introspection.token.claim',
      'lightweight.claim',
    ],
    'mapper.config',
  );
  expectExact(
    mapper.config,
    {
      'usermodel.clientRoleMapping.clientId': EXPECTED.clientId,
      multivalued: 'true',
      'claim.name': 'betufu_roles',
      'jsonType.label': 'String',
      'id.token.claim': 'true',
      'access.token.claim': 'false',
      'userinfo.token.claim': 'false',
      'introspection.token.claim': 'false',
      'lightweight.claim': 'false',
    },
    'mapper.config',
  );

  expectExactKeys(
    realm.clientScopeMappings,
    [EXPECTED.clientId],
    'realm.clientScopeMappings',
  );
  const scopeMapping = expectSingleEntry(
    realm.clientScopeMappings[EXPECTED.clientId],
    `realm.clientScopeMappings.${EXPECTED.clientId}`,
  );
  expectExactKeys(scopeMapping, ['client', 'roles'], 'client scope mapping');
  expectExact(scopeMapping.client, EXPECTED.clientId, 'client scope mapping client');
  expectExact(scopeMapping.roles, [EXPECTED.technicalRole], 'client scope mapping roles');

  const technicalUser = expectSingleEntry(realm.users, 'realm.users');
  expectExactKeys(
    technicalUser,
    [
      'id',
      'username',
      'email',
      'enabled',
      'emailVerified',
      'requiredActions',
      'realmRoles',
      'clientRoles',
      'credentials',
      'firstName',
      'lastName',
    ],
    'realm.users[0]',
  );
  expectExact(technicalUser.id, EXPECTED.technicalUserId, 'technical user id');
  expectExact(technicalUser.username, EXPECTED.technicalUsername, 'technical username');
  expectExact(technicalUser.email, EXPECTED.technicalEmail, 'technical email');
  expectExact(
    technicalUser.firstName, EXPECTED.technicalFirstName, 'technical user firstName');
  expectExact(
    technicalUser.lastName, EXPECTED.technicalLastName, 'technical user lastName');
  expectExact(technicalUser.enabled, true, 'technical user enabled');
  expectExact(technicalUser.emailVerified, true, 'technical user emailVerified');
  expectExact(technicalUser.requiredActions, [], 'technical user requiredActions');
  expectExact(technicalUser.realmRoles, [], 'technical user realmRoles');
  expectExactKeys(
    technicalUser.clientRoles,
    [EXPECTED.clientId],
    'technical user clientRoles',
  );
  expectExact(
    technicalUser.clientRoles[EXPECTED.clientId],
    [EXPECTED.technicalRole],
    'technical user client role assignments',
  );

  const credential = expectSingleEntry(technicalUser.credentials, 'technical user credentials');
  expectExactKeys(credential, ['type', 'value', 'temporary'], 'technical user credential');
  expectExact(credential.type, 'password', 'technical user credential type');
  expectExact(
    credential.value,
    EXPECTED.technicalPasswordPlaceholder,
    'technical user credential value',
  );
  expectExact(credential.temporary, false, 'technical user credential temporary');

  expectExactKeys(
    realm.components,
    ['org.keycloak.userprofile.UserProfileProvider'],
    'realm.components',
  );
  const userProfileProvider = expectSingleEntry(
    realm.components['org.keycloak.userprofile.UserProfileProvider'],
    'realm.components.org.keycloak.userprofile.UserProfileProvider',
  );
  expectExactKeys(
    userProfileProvider,
    ['providerId', 'config'],
    'user profile provider',
  );
  expectExact(
    userProfileProvider.providerId,
    'declarative-user-profile',
    'user profile providerId',
  );
  expectExactKeys(
    userProfileProvider.config,
    ['kc.user.profile.config'],
    'user profile config',
  );
  const rawConfig = expectSingleEntry(
    userProfileProvider.config['kc.user.profile.config'],
    'kc.user.profile.config',
  );
  expect(typeof rawConfig === 'string', 'kc.user.profile.config deve ser uma string.');

  let upConfig;
  try {
    upConfig = JSON.parse(rawConfig);
  } catch {
    contractError('kc.user.profile.config nao contem JSON valido.');
  }

  expectExactKeys(upConfig, ['attributes', 'groups'], 'UPConfig');
  expectExact(upConfig.groups, [], 'UPConfig.groups');
  expect(Array.isArray(upConfig.attributes), 'UPConfig.attributes deve ser uma lista.');
  expect(
    upConfig.attributes.length === 2,
    'UPConfig.attributes deve conter exatamente 2 atributos.',
  );

  const attributeNames = upConfig.attributes.map((attr) => attr?.name);
  expect(
    !attributeNames.includes('firstName') && !attributeNames.includes('lastName'),
    'UPConfig nao pode conter firstName ou lastName.',
  );
  expectExact(
    attributeNames,
    ['username', 'email'],
    'UPConfig.attributes nomes',
  );

  const expectedUsernameAttr = {
    name: 'username',
    displayName: '${username}',
    validations: {
      length: { min: 3, max: 255 },
      'username-prohibited-characters': {},
      'up-username-not-idn-homograph': {},
    },
    permissions: {
      view: ['admin', 'user'],
      edit: ['admin', 'user'],
    },
    multivalued: false,
  };

  const expectedEmailAttr = {
    name: 'email',
    displayName: '${email}',
    validations: {
      email: {},
      length: { max: 255 },
    },
    required: {
      roles: ['user'],
    },
    permissions: {
      view: ['admin', 'user'],
      edit: ['admin', 'user'],
    },
    multivalued: false,
  };

  expectExact(
    upConfig.attributes[0],
    expectedUsernameAttr,
    'UPConfig username attribute',
  );
  expectExact(
    upConfig.attributes[1],
    expectedEmailAttr,
    'UPConfig email attribute',
  );

  return realm;
}

export async function validateRealmFile(realmPath) {
  let document;

  try {
    document = JSON.parse(await readFile(realmPath, 'utf8'));
  } catch {
    contractError('nao foi possivel ler JSON valido.');
  }

  return validateRealmDocument(document);
}

async function main() {
  if (process.argv.length !== 3) {
    console.error('Uso: node scripts/validate-keycloak-realm.mjs <realm.json>');
    process.exitCode = 2;
    return;
  }

  try {
    await validateRealmFile(resolve(process.argv[2]));
    console.log('[validate-keycloak-realm] OK: realm betufu-local segue o contrato aprovado.');
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`[validate-keycloak-realm] ERRO: ${message}`);
    process.exitCode = 1;
  }
}

const invokedAsScript = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedAsScript) {
  await main();
}
