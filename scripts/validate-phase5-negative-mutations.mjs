#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const files = [
  'backend/src/main/resources/db/migration/V29__create_virtual_credit_ledger.sql',
  'backend/src/main/resources/db/migration/V30__create_prediction_records.sql',
];
for (const relative of files) {
  const content = await readFile(resolve(root, relative), 'utf8');
  const required = relative.includes('V29__')
    ? ['BEFORE UPDATE OR DELETE', 'balance >= 0.00', 'entry_type IN']
    : ['BEFORE UPDATE OR DELETE', 'ck_prediction_state', 'stake >= 10.00'];
  for (const token of required) {
    if (!content.includes(token)) throw new Error(`PHASE5_NEGATIVE_MISSING:${relative}:${token}`);
  }
}
console.log('[phase5-negative] PASS: immutable triggers, non-negative balance, and closed entry vocabulary present');
