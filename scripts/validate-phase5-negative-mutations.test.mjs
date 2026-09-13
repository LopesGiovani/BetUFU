import test from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const exec = promisify(execFile);
test('phase5 negative harness is fail-closed and passes governed sources', async () => {
  const result = await exec(process.execPath, ['scripts/validate-phase5-negative-mutations.mjs']);
  assert.match(result.stdout, /PASS/);
});
