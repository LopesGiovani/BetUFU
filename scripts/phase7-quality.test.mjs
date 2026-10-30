import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:net';
import { once } from 'node:events';
import { assertSecurityReport, qualityCommands, runCommands } from './phase7-quality.mjs';

test('generator get-uri caller can list and download through the patched FTP dependency', { timeout: 10_000 }, async () => {
  const { getUri } = await import('../frontend/node_modules/get-uri/dist/index.js');
  const servers = [], sockets = new Set(), commands = [];
  const listen = async server => { servers.push(server); server.listen(0, '127.0.0.1'); await once(server, 'listening'); return server.address().port; };
  const controlServer = createServer(control => {
    sockets.add(control); let buffer = '', channel;
    control.write('220 Isolated compatibility fixture\r\n');
    control.on('data', async chunk => {
      buffer += chunk;
      while (buffer.includes('\r\n')) {
        const boundary = buffer.indexOf('\r\n'), line = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2);
        const command = line.split(' ')[0]; commands.push(command);
        try {
          if (command === 'USER') control.write('331 Password required\r\n');
          else if (command === 'PASS') control.write('230 Logged in\r\n');
          else if (command === 'FEAT') control.write('211-Features\r\n EPSV\r\n211 End\r\n');
          else if (command === 'MDTM') control.write('502 Use directory listing\r\n');
          else if (command === 'EPSV') {
            let connected; const ready = new Promise(resolve => { connected = resolve; });
            const passive = createServer(socket => { sockets.add(socket); connected(socket); passive.close(); });
            const port = await listen(passive); channel = ready;
            control.write(`229 Entering Extended Passive Mode (|||${port}|)\r\n`);
          } else if (command === 'LIST' || command === 'RETR') {
            control.write('150 Opening data connection\r\n'); const socket = await channel;
            socket.end(command === 'LIST' ? 'type=file;size=6;modify=20261030120000; artifact.txt\r\n' : 'proof\n',
              () => control.write('226 Transfer complete\r\n'));
          } else if (command === 'SIZE') control.write('213 6\r\n');
          else if (command === 'QUIT') control.end('221 Goodbye\r\n');
          else control.write('200 Command accepted\r\n');
        } catch (error) { control.destroy(error); }
      }
    });
  });
  try {
    const port = await listen(controlServer);
    const stream = await getUri(`ftp://127.0.0.1:${port}/artifact.txt`, { timeout: 3000 });
    let content = ''; for await (const chunk of stream) content += chunk;
    assert.equal(content, 'proof\n'); assert.ok(stream.lastModified instanceof Date);
    assert.ok(commands.includes('LIST')); assert.ok(commands.includes('RETR'));
  } finally {
    for (const socket of sockets) socket.destroy();
    await Promise.all(servers.filter(server => server.listening).map(server => new Promise(resolve => server.close(resolve))));
  }
});

const inputs = { 'backend/pom.xml': 'a'.repeat(64) };
const report = () => ({ schema_version: 1, status: 'passed', produced_at: new Date().toISOString(),
  inputs, scans: [{ ecosystem: 'Maven', status: 'passed', packages: 1 },
    { ecosystem: 'npm', status: 'passed', packages: 1 }], findings: [] });

test('every quality command propagates failure and stops subsequent commands', async () => {
  for (let fail = 0; fail < qualityCommands.length; fail++) {
    const called = [];
    await assert.rejects(runCommands(async command => {
      called.push(command); if (called.length === fail + 1) throw Error('exit 7');
    }), /exit 7/);
    assert.deepEqual(called, qualityCommands.slice(0, fail + 1));
  }
});
test('quality includes both aggregates, generated-client drift and production checks', () => {
  for (const command of ['node scripts/maven.mjs -f backend/pom.xml verify',
    'node --test scripts/*.test.mjs', 'npm --prefix frontend test -- --watch=false',
    'npm --prefix frontend run generate:api:check', 'npm --prefix frontend run lint',
    'npm --prefix frontend run build:production', 'git diff --check'])
    assert.ok(qualityCommands.includes(command));
});
test('missing, failed, stale, future or drifted security evidence blocks', () => {
  assert.doesNotThrow(() => assertSecurityReport(report(), inputs));
  for (const mutate of [r => { r.status = 'failed'; }, r => { r.scans.pop(); },
    r => { r.scans[0].packages = 0; }, r => { r.scans[0].status = 'unavailable'; },
    r => { r.produced_at = '2000-01-01T00:00:00Z'; },
    r => { r.produced_at = new Date(Date.now() + 3_600_000).toISOString(); },
    r => { r.inputs = {}; }, r => { delete r.findings; }]) {
    const r = report(); mutate(r); assert.throws(() => assertSecurityReport(r, inputs), /BLOCKED/);
  }
  assert.throws(() => assertSecurityReport(null, inputs), /BLOCKED/);
});
test('every confirmed vulnerability blocks, including LOW and an unsupported exception', () => {
  for (const severity of ['LOW', 'MODERATE', 'HIGH', 'CRITICAL', 'UNKNOWN']) {
    const r = report(); r.findings.push({ id: 'KNOWN-VULNERABILITY', severity, exception: 'ignore' });
    assert.throws(() => assertSecurityReport(r, inputs), /VULNERABILITY/);
  }
});
