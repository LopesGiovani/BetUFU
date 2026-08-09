#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { basename, dirname, isAbsolute, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

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

try {
  validateArguments(mavenArguments);

  if (!existsSync(wrapperPath)) {
    throw new Error(`Maven Wrapper ausente em ${wrapperPath}.`);
  }

  const invocation = process.platform === 'win32'
    ? windowsInvocation()
    : { command: wrapperPath, arguments: mavenArguments };

  const child = spawn(invocation.command, invocation.arguments, {
    cwd: projectRoot,
    shell: false,
    stdio: 'inherit',
    windowsHide: true,
  });

  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.once(signal, () => {
      if (!child.killed) {
        child.kill(signal);
      }
    });
  }

  child.once('error', (error) => {
    fail(`não foi possível iniciar o Maven Wrapper: ${error.message}`);
  });

  child.once('exit', (code, signal) => {
    if (code !== null) {
      process.exitCode = code;
      return;
    }

    process.exitCode = signal === 'SIGINT' ? 130 : 143;
  });
} catch (error) {
  fail(error instanceof Error ? error.message : String(error));
}
