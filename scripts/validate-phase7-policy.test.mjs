import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { test } from 'node:test';
import {
  validatePhase7Policy,
  requiredClasses,
  publicationSurfaces,
} from './validate-phase7-policy.mjs';

const root = resolve(import.meta.dirname, '..');
async function fixture(t) {
  const projectRoot = await mkdtemp(join(tmpdir(), 'betufu-policy-'));
  t.after(() => rm(projectRoot, { recursive: true, force: true }));
  const evidence = Buffer.from(
    'Synthetic evidence confined to this unit-test directory.',
  );
  await writeFile(join(projectRoot, 'evidence.txt'), evidence);
  await mkdir(join(projectRoot, 'nested'));
  const approval = () => ({
    status: 'approved',
    authority: 'test reviewer',
    reference: 'test only',
    evidence: {
      path: 'evidence.txt',
      sha256: createHash('sha256').update(evidence).digest('hex'),
    },
  });
  const retention = {
    status: 'approved',
    days: 30,
    start_event: 'record_created',
    disposition: 'restricted_pseudonymization',
    authority: approval(),
  };
  const policy = {
    schema_version: 1,
    status: 'approved',
    legal_review: approval(),
    classes: Object.fromEntries(
      requiredClasses.map((name) => [
        name,
        {
          retention: {
            ...structuredClone(retention),
            start_event:
              name === 'moderation_evidence' ? 'case_closed' : 'record_created',
          },
          purpose: 'Documented test purpose',
          rights: 'Reviewed manual handling',
          holds: [
            'open_case',
            'pending_review',
            'active_sanction',
            'legal_hold',
          ],
        },
      ]),
    ),
    rights: Object.fromEntries(
      [
        'notice',
        'access',
        'correction',
        'objection',
        'deletion',
        'pseudonymization',
      ].map((name) => [
        name,
        { procedure: 'Reviewed procedure', authority: approval() },
      ]),
    ),
    holder_verification: {
      account: 'Verified account identity',
      athlete_without_account: 'Reviewed identity procedure',
      authority: approval(),
    },
    response_operator: { identity: 'Test operator', authority: approval() },
    minimum_audit: {
      preserve_active_sanctions: true,
      preserve_progression: true,
      retention: structuredClone(retention),
    },
    restore: {
      reapply_current_suppressions_before_serving: true,
      authority: approval(),
    },
  };
  const identity = {
    schema_version: 1,
    status: 'approved',
    public_name: 'BETUFU',
    legal_identity: 'Synthetic test entity',
    official_ufu_affiliation: false,
    domain: 'example.invalid',
    contact_email: 'privacy@example.invalid',
    infrastructure_operator: 'Test operator',
    authority: approval(),
  };
  const checklist = {
    schema_version: 1,
    status: 'approved',
    surfaces: Object.fromEntries(
      publicationSurfaces.map((name) => [
        name,
        { status: 'approved', evidence: approval() },
      ]),
    ),
    blocked_identifiers: ['test-private-email'],
    approved_exceptions: [],
    authority: approval(),
  };
  return { projectRoot, policy, identity, checklist };
}

test('accepts a complete reviewed test policy with intact evidence', async (t) => {
  assert.equal(
    (await validatePhase7Policy(await fixture(t))).status,
    'approved',
  );
});

const invalid = [
  [
    'pending legal review',
    (f) => {
      f.policy.legal_review.status = 'pending';
    },
  ],
  [
    'proposed 90 days',
    (f) => {
      f.policy.classes.common_messages.retention.status = 'proposed';
      f.policy.classes.common_messages.retention.days = 90;
    },
  ],
  [
    'proposed 180 days',
    (f) => {
      f.policy.classes.moderation_evidence.retention.status = 'proposed';
      f.policy.classes.moderation_evidence.retention.days = 180;
    },
  ],
  [
    'inconclusive authority',
    (f) => {
      f.policy.response_operator.authority.status = 'inconclusive';
    },
  ],
  [
    'missing holder for athlete without account',
    (f) => {
      delete f.policy.holder_verification.athlete_without_account;
    },
  ],
  [
    'missing minimum audit period',
    (f) => {
      delete f.policy.minimum_audit.retention.days;
    },
  ],
  [
    'audit erases active ban',
    (f) => {
      f.policy.minimum_audit.preserve_active_sanctions = false;
    },
  ],
  [
    'audit erases progression',
    (f) => {
      f.policy.minimum_audit.preserve_progression = false;
    },
  ],
  [
    'evidence age starts before case closure',
    (f) => {
      f.policy.classes.moderation_evidence.retention.start_event =
        'record_created';
    },
  ],
  [
    'evidence expires with case open',
    (f) => {
      f.policy.classes.moderation_evidence.holds = [];
    },
  ],
  [
    'false UFU affiliation',
    (f) => {
      f.identity.official_ufu_affiliation = true;
    },
  ],
  [
    'empty legal identity',
    (f) => {
      f.identity.legal_identity = '';
    },
  ],
  [
    'missing domain',
    (f) => {
      delete f.identity.domain;
    },
  ],
  [
    'missing legal authority',
    (f) => {
      delete f.policy.legal_review.authority;
    },
  ],
  [
    'unreviewed public history',
    (f) => {
      f.checklist.surfaces.public_history.status = 'pending';
    },
  ],
  [
    'unapproved identifier exception',
    (f) => {
      f.checklist.approved_exceptions.push({
        identifier: 'private',
        reason: 'test',
        authority: { status: 'pending' },
      });
    },
  ],
  [
    'empty identifier policy',
    (f) => {
      f.checklist.blocked_identifiers = [];
    },
  ],
  [
    'invalid evidence hash',
    (f) => {
      f.identity.authority.evidence.sha256 = '0'.repeat(64);
    },
  ],
  [
    'missing evidence',
    (f) => {
      f.identity.authority.evidence.path = 'absent.txt';
    },
  ],
  [
    'evidence is a directory',
    (f) => {
      f.identity.authority.evidence.path = 'nested';
    },
  ],
  [
    'escaping evidence path',
    (f) => {
      f.identity.authority.evidence.path = '../evidence.txt';
    },
  ],
  [
    'fixture cannot authorize launch',
    (f) => {
      f.policy.fixture = true;
    },
  ],
  [
    'restore resurrects deleted data',
    (f) => {
      f.policy.restore.reapply_current_suppressions_before_serving = false;
    },
  ],
];
for (const name of requiredClasses)
  invalid.push([
    `missing ${name} policy`,
    (f) => {
      delete f.policy.classes[name];
    },
  ]);
for (const name of [
  'notice',
  'access',
  'correction',
  'objection',
  'deletion',
  'pseudonymization',
])
  invalid.push([
    `missing ${name} right`,
    (f) => {
      delete f.policy.rights[name];
    },
  ]);

for (const [name, mutate] of invalid)
  test(`rejects ${name}`, async (t) => {
    const f = await fixture(t);
    f.policy.classes.moderation_evidence.retention.start_event = 'case_closed';
    mutate(f);
    await assert.rejects(validatePhase7Policy(f), /BLOCKED:/);
  });

test('real documents remain pending and CLI blocks launch', async () => {
  const policy = JSON.parse(
    await readFile(join(root, 'docs/phase7-privacy-policy.json')),
  );
  assert.equal(policy.classes.common_messages.retention.days, 90);
  assert.equal(policy.classes.moderation_evidence.retention.days, 180);
  assert.equal(policy.minimum_audit.retention.days, null);
  assert.equal(
    policy.classes.moderation_evidence.retention.start_event,
    'case_closed',
  );
  assert.throws(
    () =>
      execFileSync(process.execPath, ['scripts/validate-phase7-policy.mjs'], {
        cwd: root,
        stdio: 'pipe',
      }),
    (e) => e.status === 1 && e.stderr.toString().includes('BLOCKED:'),
  );
});

test('pending-review test discriminates a mutation disabling both status guards', async (t) => {
  const f = await fixture(t);
  f.policy.legal_review.status = 'pending';
  await assert.rejects(validatePhase7Policy(f), /BLOCKED:/);
  const source = await readFile(
    join(root, 'scripts/validate-phase7-policy.mjs'),
    'utf8',
  );
  const mutated = source
    .replace("value?.status === 'approved' &&", 'true &&')
    .replace(
      "requirePolicy(value.status === 'approved', 'UNRESOLVED_STATUS');",
      "requirePolicy(true, 'UNRESOLVED_STATUS');",
    );
  assert.notEqual(mutated, source);
  const path = join(f.projectRoot, 'mutated-policy.mjs');
  await writeFile(path, mutated);
  const mutation = await import(pathToFileURL(path).href);
  assert.equal((await mutation.validatePhase7Policy(f)).status, 'approved');
});
