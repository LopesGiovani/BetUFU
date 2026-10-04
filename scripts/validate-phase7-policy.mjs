#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { lstat, readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultRoot = resolve(import.meta.dirname, '..');
export const requiredClasses = [
  'common_messages',
  'moderation_evidence',
  'sanction_progression',
  'athlete_without_account',
  'ledger',
  'audit',
  'logs',
  'backups',
];
export const publicationSurfaces = [
  'pages',
  'builds',
  'images',
  'documents',
  'metadata',
  'public_history',
];
const rights = [
  'notice',
  'access',
  'correction',
  'objection',
  'deletion',
  'pseudonymization',
];
const text = (value) => typeof value === 'string' && value.trim().length > 0;
function requirePolicy(condition, rule) {
  if (!condition) throw new Error(`BLOCKED: ${rule}`);
}

async function evidenceBytes(root, path) {
  requirePolicy(text(path) && !/[:\\\x00-\x1f]/.test(path), 'UNSAFE_EVIDENCE');
  const parts = path.split('/');
  requirePolicy(
    parts.every((part) => part && part !== '.' && part !== '..'),
    'UNSAFE_EVIDENCE',
  );
  let target = root;
  for (const part of parts) {
    target = join(target, part);
    const info = await lstat(target);
    requirePolicy(!info.isSymbolicLink(), 'UNSAFE_EVIDENCE');
  }
  requirePolicy((await lstat(target)).isFile(), 'EVIDENCE_FILE');
  return readFile(target);
}

async function authority(root, value) {
  requirePolicy(
    value?.status === 'approved' && value.fixture !== true,
    'AUTHORITY_STATUS',
  );
  requirePolicy(
    text(value.authority) && text(value.reference),
    'AUTHORITY_IDENTITY',
  );
  const evidence = value.evidence;
  requirePolicy(
    /^[a-f0-9]{64}$/.test(evidence?.sha256 ?? ''),
    'EVIDENCE_DIGEST',
  );
  const bytes = await evidenceBytes(root, evidence.path);
  requirePolicy(
    bytes.length > 0 &&
      createHash('sha256').update(bytes).digest('hex') === evidence.sha256,
    'EVIDENCE_DIGEST',
  );
}

// Approval records establish traceability, not independent proof of legal review.
function rejectUnresolved(value) {
  requirePolicy(value && typeof value === 'object', 'DOCUMENT_SHAPE');
  requirePolicy(value.fixture !== true, 'FIXTURE_AUTHORITY');
  if (Object.hasOwn(value, 'status'))
    requirePolicy(value.status === 'approved', 'UNRESOLVED_STATUS');
  for (const child of Object.values(value))
    if (child && typeof child === 'object') rejectUnresolved(child);
}

async function retention(root, value) {
  requirePolicy(value?.status === 'approved', 'RETENTION_STATUS');
  requirePolicy(
    Number.isSafeInteger(value.days) && value.days > 0,
    'RETENTION_PERIOD',
  );
  requirePolicy(
    text(value.start_event) && text(value.disposition),
    'RETENTION_RULE',
  );
  await authority(root, value.authority);
}

async function privacy(root, policy) {
  await authority(root, policy.legal_review);
  for (const name of requiredClasses) {
    const rule = policy.classes?.[name];
    requirePolicy(
      text(rule?.purpose) && text(rule.rights) && Array.isArray(rule.holds),
      'DATA_CLASS',
    );
    await retention(root, rule.retention);
  }
  const evidence = policy.classes.moderation_evidence;
  requirePolicy(
    evidence.retention.start_event === 'case_closed',
    'CASE_CLOSURE',
  );
  requirePolicy(
    ['open_case', 'pending_review', 'legal_hold'].every((hold) =>
      evidence.holds.includes(hold),
    ),
    'CASE_HOLDS',
  );
  for (const name of rights) {
    requirePolicy(text(policy.rights?.[name]?.procedure), 'RIGHTS_PROCEDURE');
    await authority(root, policy.rights[name].authority);
  }
  requirePolicy(
    text(policy.holder_verification?.account) &&
      text(policy.holder_verification.athlete_without_account),
    'HOLDER_VERIFICATION',
  );
  await authority(root, policy.holder_verification.authority);
  requirePolicy(text(policy.response_operator?.identity), 'RESPONSE_OPERATOR');
  await authority(root, policy.response_operator.authority);
  requirePolicy(
    policy.minimum_audit?.preserve_active_sanctions === true &&
      policy.minimum_audit.preserve_progression === true,
    'MINIMUM_AUDIT',
  );
  await retention(root, policy.minimum_audit.retention);
  requirePolicy(
    policy.restore?.reapply_current_suppressions_before_serving === true,
    'RESTORE_SUPPRESSIONS',
  );
  await authority(root, policy.restore.authority);
}

async function institutional(root, identity) {
  requirePolicy(
    identity.public_name === 'BETUFU' &&
      identity.official_ufu_affiliation === false,
    'INSTITUTIONAL_AFFILIATION',
  );
  for (const field of [
    'legal_identity',
    'domain',
    'contact_email',
    'infrastructure_operator',
  ])
    requirePolicy(text(identity[field]), 'INSTITUTIONAL_IDENTITY');
  requirePolicy(
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identity.contact_email),
    'CONTACT_EMAIL',
  );
  await authority(root, identity.authority);
}

async function publication(root, checklist) {
  await authority(root, checklist.authority);
  for (const surface of publicationSurfaces) {
    requirePolicy(
      checklist.surfaces?.[surface]?.status === 'approved',
      'PUBLICATION_SURFACE',
    );
    await authority(root, checklist.surfaces[surface].evidence);
  }
  requirePolicy(
    Array.isArray(checklist.blocked_identifiers) &&
      checklist.blocked_identifiers.length > 0 &&
      checklist.blocked_identifiers.every(text),
    'PUBLICATION_IDENTIFIERS',
  );
  requirePolicy(
    Array.isArray(checklist.approved_exceptions),
    'PUBLICATION_EXCEPTIONS',
  );
  for (const exception of checklist.approved_exceptions) {
    requirePolicy(
      text(exception.identifier) && text(exception.reason),
      'PUBLICATION_EXCEPTION',
    );
    await authority(root, exception.authority);
  }
}

export async function validatePhase7Policy({
  projectRoot = defaultRoot,
  policy,
  identity,
  checklist,
} = {}) {
  try {
    const root = resolve(projectRoot);
    const values = [policy, identity, checklist];
    const paths = [
      'docs/phase7-privacy-policy.json',
      'docs/phase7-institutional-identity.json',
      'docs/phase7-publication-checklist.json',
    ];
    for (let i = 0; i < values.length; i++) {
      values[i] ??= JSON.parse(
        (await evidenceBytes(root, paths[i])).toString('utf8'),
      );
      requirePolicy(
        values[i].schema_version === 1 && values[i].status === 'approved',
        'DOCUMENT_STATUS',
      );
      rejectUnresolved(values[i]);
    }
    await privacy(root, values[0]);
    await institutional(root, values[1]);
    await publication(root, values[2]);
    return {
      status: 'approved',
      scope: 'documentary-policy-evidence',
      launch_authorized: false,
    };
  } catch (error) {
    if (error.message?.startsWith('BLOCKED:')) throw error;
    throw new Error('BLOCKED: POLICY_EVIDENCE_UNREADABLE');
  }
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  try {
    requirePolicy(process.argv.length === 2, 'CLI_ARGUMENTS');
    console.log(JSON.stringify(await validatePhase7Policy()));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
