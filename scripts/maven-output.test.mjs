import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  createMavenOutputCollector,
  exitCodeForTermination,
  formatMavenOutcome,
} from './lib/maven-output.mjs';

function collect(chunks) {
  const written = [];
  const collector = createMavenOutputCollector({ writeLog: (chunk) => written.push(chunk) });
  for (const chunk of chunks) collector.consume(chunk);
  return { result: collector.finish(), written: written.join('') };
}

describe('maven-output', () => {
  it('mantem o log completo no callback e soma linhas de testes divididas em chunks', () => {
    const { result, written } = collect([
      '[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 1\r',
      '\n[INFO] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0\n',
    ]);

    assert.equal(written, '[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 1\r\n[INFO] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0\n');
    assert.deepEqual(result.testSummary, { testsRun: 5, failures: 1, errors: 0, skipped: 1 });
  });

  it('nao inventa contagem quando o goal nao executa testes', () => {
    const { result } = collect(['[INFO] --- spotless:check ---\n[INFO] BUILD SUCCESS\n']);

    assert.equal(result.testSummary, null);
  });

  it('seleciona linhas relevantes e limita a janela a dez linhas', () => {
    const chunks = Array.from({ length: 12 }, (_, index) => `[ERROR] falha-${index}\n`);
    const { result } = collect(chunks);

    assert.equal(result.failureLines.length, 10);
    assert.equal(result.failureLines[0], '[ERROR] falha-2');
    assert.equal(result.failureLines[9], '[ERROR] falha-11');
  });

  it('mantem resumo de testes com falhas entre as linhas relevantes', () => {
    const { result } = collect(['[INFO] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0\n']);

    assert.deepEqual(result.failureLines, ['[INFO] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0']);
  });

  it('usa as ultimas linhas quando nao encontra marcador conhecido', () => {
    const { result } = collect(['linha-1\nlinha-2\nlinha-3\n']);

    assert.deepEqual(result.failureLines, ['linha-1', 'linha-2', 'linha-3']);
  });

  it('remove ANSI e redige valores sensiveis antes de mostrar linhas', () => {
    const { result } = collect(['\u001B[31m[ERROR]\u001B[0m password=secret-value\n']);

    assert.deepEqual(result.failureLines, ['[ERROR] password=[redacted]']);
  });

  it('mantem o mapeamento de exit code e formata sucesso e falha', () => {
    assert.equal(exitCodeForTermination(0, null), 0);
    assert.equal(exitCodeForTermination(7, null), 7);
    assert.equal(exitCodeForTermination(null, 'SIGINT'), 130);
    assert.equal(exitCodeForTermination(null, 'SIGTERM'), 143);

    assert.deepEqual(formatMavenOutcome({
      code: 0,
      signal: null,
      testSummary: { testsRun: 5, failures: 0, errors: 0, skipped: 1 },
      logPath: 'ignored',
    }), {
      exitCode: 0,
      keepLog: false,
      lines: [
        '[maven] BUILD SUCCESS',
        '[maven] Tests run: 5, Failures: 0, Errors: 0, Skipped: 1',
      ],
    });

    assert.deepEqual(formatMavenOutcome({
      code: 1,
      signal: null,
      testSummary: null,
      failureLines: ['[ERROR] falha'],
      logPath: 'C:\\Temp\\maven.log',
    }), {
      exitCode: 1,
      keepLog: true,
      lines: [
        '[maven] BUILD FAILURE (exit code 1)',
        '[maven] [ERROR] falha',
        '[maven] Full log: C:\\Temp\\maven.log',
      ],
    });
  });
});
