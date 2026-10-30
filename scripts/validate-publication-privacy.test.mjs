import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assertPublicationIdentity, inspectPublicationSurface } from './validate-publication-privacy.mjs';

test('complete synthetic review passes assertions, but each missing institutional fact blocks', () => {
  const identity = { status: 'approved', official_ufu_affiliation: false,
    authority: { status: 'approved', authority: 'test-only', reference: 'unit-test' },
    legal_identity: 'Synthetic operator', domain: 'https://reviewed.example.org',
    contact_email: 'contact@example.org', infrastructure_operator: 'Synthetic infrastructure' };
  assert.doesNotThrow(() => assertPublicationIdentity(identity));
  for (const key of ['legal_identity', 'domain', 'contact_email', 'infrastructure_operator'])
    assert.throws(() => assertPublicationIdentity({ ...identity, [key]: null }), /BLOCKED/);
  assert.doesNotThrow(() => inspectPublicationSurface('pages', Buffer.from('Public project information'),
    { blocked_identifiers: ['private@example.invalid'], approved_exceptions: [] }));
});

test('pending or fabricated institutional facts never authorize publication', () => {
  for (const identity of [null, { status: 'pending' }, { status: 'approved', public_name: 'BETUFU' },
    { status: 'approved', fixture: true }])
    assert.throws(() => assertPublicationIdentity(identity), /BLOCKED/);
});
test('known personal identifiers block every publication surface without echoing their values', () => {
  for (const kind of ['pages', 'builds', 'images', 'documents', 'metadata', 'public_history']) {
    const identifier = 'responsavel-pessoal@example.invalid';
    assert.throws(() => inspectPublicationSurface(kind, Buffer.from('metadata ' + identifier),
      { blocked_identifiers: [identifier], approved_exceptions: [] }),
    e => /BLOCKED/.test(e.message) && !e.message.includes(identifier));
  }
});
test('binary identifiers are checked too; unsupported or empty surfaces are not evidence', () => {
  assert.throws(() => inspectPublicationSurface('images', Buffer.from([0, 65, 66, 67, 0]),
    { blocked_identifiers: ['ABC'], approved_exceptions: [] }), /BLOCKED/);
  for (const kind of ['unknown', 'pages'])
    assert.throws(() => inspectPublicationSurface(kind, Buffer.alloc(0),
      { blocked_identifiers: [], approved_exceptions: [] }), /BLOCKED/);
});
