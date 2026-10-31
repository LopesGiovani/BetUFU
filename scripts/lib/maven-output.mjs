const ANSI_ESCAPE_SEQUENCE = /\u001B(?:\[[0-?]*[ -/]*[@-~]|\][^\u0007]*(?:\u0007|\u001B\\))/gu;
const TEST_SUMMARY = /Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)/u;
const RELEVANT_FAILURE = /(?:BUILD FAILURE|\[ERROR\]|Failed to execute goal|There are test failures|Caused by:)/u;
const SENSITIVE_VALUE = /((?:password|passwd|token|secret|authorization|client[_-]?secret|api[_-]?key)\s*[=:]\s*)[^\s,;]+/giu;

export function stripAnsi(value) {
  return String(value).replace(ANSI_ESCAPE_SEQUENCE, '');
}

function sanitizeLine(value) {
  return stripAnsi(value).replace(SENSITIVE_VALUE, '$1[redacted]');
}

function addToRing(ring, value, limit) {
  ring.push(value);
  if (ring.length > limit) ring.shift();
}

export function createMavenOutputCollector({ writeLog = () => {}, maxFailureLines = 10 } = {}) {
  let pending = '';
  const testSummary = { testsRun: 0, failures: 0, errors: 0, skipped: 0 };
  let testSummaryLines = 0;
  const firstFailureLines = [];
  const relevantFailureLines = [];
  const fallbackFailureLines = [];

  function consumeLine(rawLine) {
    const line = sanitizeLine(rawLine.replace(/\r$/u, ''));
    const trimmed = line.trim();

    if (trimmed.length === 0) return;

    const testMatch = TEST_SUMMARY.exec(line);
    if (testMatch) {
      testSummary.testsRun += Number(testMatch[1]);
      testSummary.failures += Number(testMatch[2]);
      testSummary.errors += Number(testMatch[3]);
      testSummary.skipped += Number(testMatch[4]);
      testSummaryLines += 1;
    }

    addToRing(fallbackFailureLines, line, maxFailureLines);
    const testFailure = testMatch && (Number(testMatch[2]) > 0 || Number(testMatch[3]) > 0);
    if (RELEVANT_FAILURE.test(line) || testFailure) {
      if (firstFailureLines.length < maxFailureLines) firstFailureLines.push(line);
      else addToRing(relevantFailureLines, line, maxFailureLines);
    }
  }

  return {
    consume(chunk) {
      const text = String(chunk);
      writeLog(text);
      pending += text;

      const lines = pending.split(/\n/u);
      pending = lines.pop() ?? '';
      for (const line of lines) consumeLine(line);
    },

    finish() {
      if (pending.length > 0) consumeLine(pending);

      return {
        testSummary: testSummaryLines > 0 ? { ...testSummary } : null,
        failureLines: firstFailureLines.length > 0
          ? [...firstFailureLines, ...relevantFailureLines]
          : [...fallbackFailureLines],
      };
    },
  };
}

export function exitCodeForTermination(code, signal) {
  if (code !== null) return code;
  return signal === 'SIGINT' ? 130 : 143;
}

export function formatMavenOutcome({ code, signal, testSummary, failureLines = [], logPath }) {
  const exitCode = exitCodeForTermination(code, signal);
  if (exitCode === 0) {
    const lines = ['[maven] BUILD SUCCESS'];
    if (testSummary) {
      lines.push(
        `[maven] Tests run: ${testSummary.testsRun}, Failures: ${testSummary.failures}, `
        + `Errors: ${testSummary.errors}, Skipped: ${testSummary.skipped}`,
      );
    }
    return { exitCode, keepLog: false, lines };
  }

  const lines = [`[maven] BUILD FAILURE (exit code ${exitCode})`];
  for (const line of failureLines) lines.push(`[maven] ${line}`);
  if (failureLines.length === 0) lines.push('[maven] See full log for details.');
  lines.push(`[maven] Full log: ${logPath}`);
  return { exitCode, keepLog: true, lines };
}
