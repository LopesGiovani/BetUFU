#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { readFile, lstat } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(import.meta.dirname, '..');
const surfaces = ['pages', 'builds', 'images', 'documents', 'metadata', 'public_history'];
const requireGate = (condition, code) => { if (!condition) throw Error(`BLOCKED: ${code}`); };
export function assertPublicationIdentity(identity) {
  requireGate(identity?.status === 'approved' && identity.fixture !== true &&
    identity.authority?.status === 'approved' && identity.authority.authority &&
    identity.authority.reference && identity.official_ufu_affiliation === false, 'INSTITUTIONAL_IDENTITY');
  for (const key of ['legal_identity', 'domain', 'contact_email', 'infrastructure_operator'])
    requireGate(typeof identity[key] === 'string' && identity[key].trim().length > 0, 'INSTITUTIONAL_FACTS');
  requireGate(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identity.contact_email), 'INSTITUTIONAL_CONTACT');
  const domain = new URL(identity.domain.includes('://') ? identity.domain : `https://${identity.domain}`);
  requireGate(domain.protocol === 'https:' && !/localhost|\.invalid$|\.example$/.test(domain.hostname), 'INSTITUTIONAL_DOMAIN');
}
export function inspectPublicationSurface(kind, bytes, checklist) {
  requireGate(surfaces.includes(kind) && Buffer.isBuffer(bytes) && bytes.length > 0, 'PUBLICATION_SURFACE');
  requireGate(Array.isArray(checklist?.blocked_identifiers) && Array.isArray(checklist.approved_exceptions), 'IDENTIFIER_SCOPE');
  for (const identifier of checklist.blocked_identifiers) {
    requireGate(typeof identifier === 'string' && identifier.length > 0, 'IDENTIFIER_SCOPE');
    // No exception can silently override a blocked identifier. Values never enter diagnostics.
    for (const encoding of ['utf8', 'utf16le'])
      requireGate(!bytes.includes(Buffer.from(identifier, encoding)), `PERSONAL_IDENTIFIER_${kind.toUpperCase()}`);
  }
}
async function evidence(value) {
  requireGate(value?.status === 'approved' && value.fixture !== true && value.authority && value.reference, 'PUBLICATION_AUTHORITY');
  const path = value.evidence?.path, digest = value.evidence?.sha256;
  requireGate(typeof path === 'string' && !/[:\\\x00-\x1f]/.test(path) &&
    path.split('/').every(part => part && part !== '.' && part !== '..') && /^[a-f0-9]{64}$/i.test(digest ?? ''), 'PUBLICATION_EVIDENCE');
  let target = ROOT;
  for (const part of path.split('/')) { target = resolve(target, part); requireGate(!(await lstat(target)).isSymbolicLink(), 'PUBLICATION_SYMLINK'); }
  requireGate((await lstat(target)).isFile(), 'PUBLICATION_EVIDENCE');
  const bytes = await readFile(target);
  requireGate(createHash('sha256').update(bytes).digest('hex') === digest.toLowerCase(), 'PUBLICATION_DIGEST');
  return bytes;
}
export async function validatePublicationPrivacy() {
  const identity = JSON.parse(await readFile(resolve(ROOT, 'docs/phase7-institutional-identity.json'), 'utf8'));
  assertPublicationIdentity(identity); await evidence(identity.authority);
  const checklist = JSON.parse(await readFile(resolve(ROOT, 'docs/phase7-publication-checklist.json'), 'utf8'));
  requireGate(checklist.status === 'approved' && checklist.fixture !== true, 'PUBLICATION_REVIEW');
  await evidence(checklist.authority);
  requireGate(checklist.blocked_identifiers?.length > 0, 'IDENTIFIER_SCOPE_MISSING');
  for (const kind of surfaces) {
    const surface = checklist.surfaces?.[kind];
    requireGate(surface?.status === 'approved', 'PUBLICATION_REVIEW');
    inspectPublicationSurface(kind, await evidence(surface.evidence), checklist);
  }
  // Existing legal/retention/holder review remains an independent obligation.
  const { validatePhase7Policy } = await import('./validate-phase7-policy.mjs');
  await validatePhase7Policy();
  console.log('[publication-privacy] Reviewed evidence passed; this command does not deploy.');
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { requireGate(process.argv.length === 2, 'PUBLICATION_ARGUMENTS'); await validatePublicationPrivacy(); }
  catch (error) { console.error(`[publication-privacy] ${error.message}`); process.exitCode = 1; }
}
