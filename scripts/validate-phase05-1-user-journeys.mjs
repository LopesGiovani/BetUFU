#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const defaultProjectRoot = resolve(scriptsDirectory, "..");

const REQUIRED_SPEC_MARKERS = Object.freeze([
  "page.goto('/')",
  "Abrir evento",
  "Ver odds da partida",
  "Entrar para palpitar",
  "Endereço de email",
  "BETUFU_E2E_USERNAME",
  "BETUFU_E2E_PASSWORD",
  "Minha conta",
  "Matrícula registrada",
  "page.goto('/conta/perfil')",
  "Entre para continuar",
  "Página não encontrada",
  "Voltar para a Arena",
  "Mercado encerrado",
  "page.clock.setFixedTime",
  "page.goBack()",
  "page.goForward()",
  "page.reload()",
  "browser.newContext",
]);

const REQUIRED_CONFIG_MARKERS = Object.freeze([
  "browserName: 'chromium'",
  "browserName: 'firefox'",
  "BETUFU_FIREFOX_EXECUTABLE",
  "width: 320",
  "retries: 0",
  "workers: 1",
  "trace: 'off'",
  "screenshot: 'off'",
  "video: 'off'",
  "http://localhost:4200",
]);

const FORBIDDEN_SOURCE_PATTERNS = Object.freeze([
  [
    "MOCK_FORBIDDEN",
    /\b(?:page|context|browserContext)\.route\s*\(|\broute\.fulfill\s*\(/u,
  ],
  [
    "AUTH_INJECTION_FORBIDDEN",
    /\b(?:addCookies|storageState)\s*\(|(?:localStorage|sessionStorage)\.setItem\s*\(/u,
  ],
  [
    "UUID_LITERAL_FORBIDDEN",
    /['"][0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}['"]/iu,
  ],
  [
    "CREDENTIAL_LITERAL_FORBIDDEN",
    /\b(?:password|senha)\s*=\s*['"][^'"]+['"]|['"][^'"]+@ufu\.br['"]/iu,
  ],
]);

export function validateUserJourneySources(specSource, configSource) {
  for (const [rule, pattern] of FORBIDDEN_SOURCE_PATTERNS) {
    if (pattern.test(specSource))
      throw new Error(`${rule}: forbidden source pattern detected`);
  }

  for (const marker of REQUIRED_SPEC_MARKERS) {
    if (!specSource.includes(marker)) {
      throw new Error(`REQUIRED_JOURNEY_MARKER: missing ${marker}`);
    }
  }

  for (const marker of REQUIRED_CONFIG_MARKERS) {
    if (!configSource.includes(marker)) {
      throw new Error(`REQUIRED_CONFIG_MARKER: missing ${marker}`);
    }
  }
}

export async function validateProject(projectRoot = defaultProjectRoot) {
  const specPath = resolve(
    projectRoot,
    "frontend/e2e/user-journeys-phase-05-1.spec.ts",
  );
  const configPath = resolve(
    projectRoot,
    "frontend/playwright.user-journeys.config.ts",
  );
  const [specSource, configSource] = await Promise.all([
    readFile(specPath, "utf8"),
    readFile(configPath, "utf8"),
  ]);
  validateUserJourneySources(specSource, configSource);
  return {
    specPath,
    configPath,
    requiredJourneys: REQUIRED_SPEC_MARKERS.length,
  };
}

async function main() {
  const result = await validateProject(
    process.argv[2] ? resolve(process.argv[2]) : defaultProjectRoot,
  );
  console.log(
    `[validate-phase05-1-user-journeys] OK: ${result.requiredJourneys} journey markers; no mocks, injected auth, literal UUIDs or credentials.`,
  );
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(`[validate-phase05-1-user-journeys] FAIL: ${error.message}`);
    process.exitCode = 1;
  });
}
