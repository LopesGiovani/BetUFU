#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createWriteStream, existsSync } from 'node:fs';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, isAbsolute, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { createMavenOutputCollector, formatMavenOutcome } from './lib/maven-output.mjs';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const wrapperPath = join(projectRoot, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw');
const mavenArguments = process.argv.slice(2);

const safeMavenArgument = /^[A-Za-z0-9_.\/\\:@=,+\-\[\]]+$/u;
const unsafeWindowsPath = /[\r\n"&|<>^%!`]/u;

function fail(message) {
  console.error(`[maven] ${message}`);
  process.exitCode = 2;
}

function validateArguments(argumentsToValidate) {
  for (const argument of argumentsToValidate) {
    if (argument.length === 0 || !safeMavenArgument.test(argument)) {
      throw new Error(`argumento Maven recusado: ${JSON.stringify(argument)}`);
    }
  }
}

function resolveComSpec() {
  const configuredComSpec = process.env.ComSpec ?? process.env.COMSPEC;

  if (
    configuredComSpec
    && isAbsolute(configuredComSpec)
    && basename(configuredComSpec).toLowerCase() === 'cmd.exe'
    && existsSync(configuredComSpec)
  ) {
    return configuredComSpec;
  }

  const systemRoot = process.env.SystemRoot ?? process.env.SYSTEMROOT ?? 'C:\\Windows';
  const fallbackComSpec = join(systemRoot, 'System32', 'cmd.exe');

  if (!existsSync(fallbackComSpec)) {
    throw new Error('ComSpec válido não encontrado para executar mvnw.cmd.');
  }

  return fallbackComSpec;
}

function windowsInvocation() {
  if (unsafeWindowsPath.test(wrapperPath)) {
    throw new Error('o caminho do Maven Wrapper contém metacaractere incompatível com cmd.exe.');
  }

  return {
    command: resolveComSpec(),
    arguments: ['/d', '/s', '/c', 'call', wrapperPath, ...mavenArguments],
  };
}

function closeLogStream(logStream) {
  if (logStream.writableEnded) return Promise.resolve();

  return new Promise((resolvePromise, rejectPromise) => {
    const onFinish = () => {
      logStream.removeListener('error', onError);
      resolvePromise();
    };
    const onError = (error) => {
      logStream.removeListener('finish', onFinish);
      rejectPromise(error);
    };

    logStream.once('finish', onFinish);
    logStream.once('error', onError);
    logStream.end();
  });
}

function attachOutput(stream, collector) {
  stream.setEncoding('utf8');
  stream.on('data', (chunk) => collector.consume(chunk));
}

async function runMaven() {
  validateArguments(mavenArguments);

  if (!existsSync(wrapperPath)) {
    throw new Error(`Maven Wrapper ausente em ${wrapperPath}.`);
  }

  const invocation = process.platform === 'win32'
    ? windowsInvocation()
    : { command: wrapperPath, arguments: mavenArguments };

  const temporaryDirectory = await mkdtemp(join(tmpdir(), 'betufu-maven-'));
  const logPath = join(temporaryDirectory, 'maven.log');
  const logStream = createWriteStream(logPath, { encoding: 'utf8' });
  const collector = createMavenOutputCollector({ writeLog: (chunk) => logStream.write(chunk) });
  let keepLog = false;

  try {
    const child = spawn(invocation.command, invocation.arguments, {
      cwd: projectRoot,
      shell: false,
      stdio: ['inherit', 'pipe', 'pipe'],
      windowsHide: true,
    });

    attachOutput(child.stdout, collector);
    attachOutput(child.stderr, collector);

    const signalHandlers = new Map();
    for (const signal of ['SIGINT', 'SIGTERM']) {
      const forwardSignal = () => {
        if (!child.killed) child.kill(signal);
      };
      signalHandlers.set(signal, forwardSignal);
      process.once(signal, forwardSignal);
    }

    const termination = await new Promise((resolvePromise) => {
      let settled = false;
      const resolveOnce = (result) => {
        if (settled) return;
        settled = true;
        resolvePromise(result);
      };

      child.once('error', (error) => resolveOnce({ error }));
      child.once('close', (code, signal) => resolveOnce({ code, signal }));
    });

    for (const [signal, handler] of signalHandlers) process.removeListener(signal, handler);
    await closeLogStream(logStream);

    if (termination.error) {
      throw new Error(`não foi possível iniciar o Maven Wrapper: ${termination.error.message}`);
    }

    const outcome = formatMavenOutcome({
      code: termination.code,
      signal: termination.signal,
      ...collector.finish(),
      logPath,
    });
    keepLog = outcome.keepLog;
    const writeLine = outcome.exitCode === 0 ? console.log : console.error;
    for (const line of outcome.lines) writeLine(line);
    process.exitCode = outcome.exitCode;
  } finally {
    if (!logStream.writableEnded) await closeLogStream(logStream);
    if (!keepLog) await rm(temporaryDirectory, { recursive: true, force: true });
  }
}

runMaven().catch((error) => {
  fail(error instanceof Error ? error.message : String(error));
});
