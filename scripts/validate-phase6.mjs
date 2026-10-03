#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { inspectOpenApiContract } from "../frontend/scripts/generate-api.mjs";
import { validateAngularRouteBoundary } from "./validate-angular-route-boundary.mjs";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
// Approved migration bytes at bf31d51; normalize line endings only.
export const MIGRATION_HASHES = Object.freeze({
  "V33__create_settlement_foundation.sql":
    "4e3fb0314cf7fab31b2f618115843c44b46060fe134051c6ac258832408dee52",
  "V34__add_settlement_processing_state.sql":
    "fe8257a77938cb102050a5e214486dfcd30eaf3d67be02a08bb3a259188e1e85",
  "V35__create_credit_correction_cases.sql":
    "b8541baf59a689afe8f5a2621a4143261889feca8ccfdddd3e14a92278344e7d",
  "V36__create_ranking_projection.sql":
    "f35eacc28ce465d5ebddbeca9522b0112d6b6876b678a06a8e70280787a7d835",
  "V37__create_private_groups.sql":
    "cf1034e0f13bec8c5b4f9d1d04771ba50707e651ff9e383126fdfe573d89f4e7",
  "V38__create_achievement_awards.sql":
    "f7cb6126f937f78a3091c4ce16929ab5c5f24dde80c173d74bf972ec1a12eafc",
});

export function validateMigrationSources(sources) {
  for (const [name, hash] of Object.entries(MIGRATION_HASHES)) {
    if (typeof sources[name] !== "string")
      throw new Error(`MIGRATION_MISSING: ${name}`);
    const actual = createHash("sha256")
      .update(sources[name].replace(/\r\n?/gu, "\n"))
      .digest("hex");
    if (actual !== hash) throw new Error(`MIGRATION_DRIFT: ${name}`);
  }
}
export const RESPONSE_ALLOWLISTS = Object.freeze({
  RankingEntry: [
    "position",
    "nickname",
    "wins",
    "losses",
    "hitRate",
    "verificationActive",
  ],
  PrivateGroupStanding: [
    "membershipId",
    "nickname",
    "role",
    "position",
    "wins",
    "losses",
    "hitRate",
    "verificationActive",
  ],
  Achievement: [
    "awardId",
    "code",
    "name",
    "description",
    "rule",
    "catalogEdition",
    "sportsEditionId",
    "state",
    "awardedAt",
    "revokedAt",
    "revocationReason",
  ],
  CreditCorrectionView: [
    "caseId",
    "referenceCode",
    "kind",
    "reason",
    "evidenceType",
    "evidenceId",
    "originalEntryId",
    "amount",
    "recovered",
    "pending",
    "status",
    "createdAt",
  ],
  SettlementHistoryItem: [
    "predictionId",
    "eventId",
    "marketId",
    "acceptedAt",
    "selection",
    "stake",
    "acceptedOdds",
    "potentialReturn",
    "settlementId",
    "state",
    "reason",
    "ruleVersion",
    "amount",
    "outcomeKind",
    "homeScore",
    "awayScore",
    "winnerSlotNo",
    "ledgerEntryId",
    "current",
    "processingStatus",
  ],
});

export function validateSources({
  contract,
  specs,
  fixtures = [],
  config,
  routes,
}) {
  const normalized = contract.replace(/\r\n?/gu, "\n");
  for (const [name, fields] of Object.entries(RESPONSE_ALLOWLISTS)) {
    const block = normalized.match(
      new RegExp(`^    ${name}:\\n([\\s\\S]*?)(?=^    \\S|(?![\\s\\S]))`, "mu"),
    )?.[1];
    if (!block || !/^      additionalProperties: false$/mu.test(block))
      throw new Error(`CLOSED_SCHEMA: ${name}`);
    const properties =
      block.match(/^      properties:\n([\s\S]*)$/mu)?.[1] ?? "";
    const actual = [...properties.matchAll(/^        (\w+):/gmu)]
      .map((match) => match[1])
      .sort();
    if (JSON.stringify(actual) !== JSON.stringify([...fields].sort()))
      throw new Error(`PRIVACY_ALLOWLIST: ${name}`);
  }
  const endpoints = inspectOpenApiContract(contract).endpoints;
  for (const path of [
    "/api/v1/public/ranking/lifetime",
    "/api/v1/me/settlements",
    "/api/v1/me/credit-corrections",
    "/api/v1/me/groups",
    "/api/v1/me/achievements",
  ]) {
    if (
      !endpoints.some(
        (endpoint) =>
          endpoint.path === path && endpoint.methods.includes("GET"),
      )
    )
      throw new Error(`MISSING_ENDPOINT: ${path}`);
  }
  for (const spec of [...specs, ...fixtures]) {
    for (const [rule, pattern] of [
      [
        "MOCK_FORBIDDEN",
        /\.\s*(?:route|routeFromHAR|fulfill)\s*\(|\[\s*['"](?:route|fulfill)['"]\s*\]/u,
      ],
      [
        "AUTH_INJECTION",
        /\b(?:addCookies|storageState|addInitScript)\b|(?:localStorage|sessionStorage)\s*\.\s*setItem/u,
      ],
      ["UUID_LITERAL", /['"][0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}['"]/iu],
      [
        "CREDENTIAL_LITERAL",
        /(?:password|senha)\s*=\s*['"][^'"]+['"]|['"][^'"]+@ufu\.br['"]/iu,
      ],
      ["FALSE_PASS", /\b(?:test|describe)\s*\.\s*(?:skip|fixme|only)\b/u],
    ]) {
      if (pattern.test(spec)) throw new Error(rule);
    }
    if (
      specs.includes(spec) &&
      (!spec.includes("page.goto('/')") || !spec.includes("waitForResponse"))
    )
      throw new Error("REAL_JOURNEY_REQUIRED");
  }
  for (const marker of [
    "width: 320",
    "retries: 0",
    "workers: 1",
    "trace: 'off'",
    "screenshot: 'off'",
    "video: 'off'",
    "'localhost'",
    "'127.0.0.1'",
    "globalSetup: './e2e/phase6.setup.ts'",
  ]) {
    if (!config.includes(marker)) throw new Error(`BROWSER_GATE: ${marker}`);
  }
  for (const path of [
    "ranking",
    "conta/historico",
    "conta/grupos",
    "conta/conquistas",
    "operacao/liquidacoes",
  ]) {
    if (!routes.includes(`path: '${path}'`))
      throw new Error(`MISSING_ROUTE: ${path}`);
  }
  if (
    !routes.includes(
      "canActivateChild: [authenticationGuard, profileCompletionGuard]",
    )
  )
    throw new Error("PRIVATE_GUARD_REQUIRED");
}

export async function validatePhase6(root = projectRoot) {
  const paths = [
    "backend/openapi/betufu.yaml",
    "frontend/e2e/phase6-results-ranking.spec.ts",
    "frontend/e2e/phase6-corrections.spec.ts",
    "frontend/playwright.phase6.config.ts",
    "frontend/src/app/app.routes.ts",
    "frontend/e2e/phase6.setup.ts",
  ];
  const [contract, first, second, config, routes, setup] = await Promise.all(
    paths.map((path) => readFile(resolve(root, path), "utf8")),
  );
  validateSources({
    contract,
    specs: [first, second],
    fixtures: [setup],
    config,
    routes,
  });
  const migrations = await Promise.all(
    Object.keys(MIGRATION_HASHES).map(async (name) => [
      name,
      await readFile(
        resolve(root, "backend/src/main/resources/db/migration", name),
        "utf8",
      ),
    ]),
  );
  validateMigrationSources(Object.fromEntries(migrations));
  // Reuse the existing structural route and generated-client boundary gate.
  await validateAngularRouteBoundary(root);
  return {
    responseSchemas: Object.keys(RESPONSE_ALLOWLISTS).length,
    realSpecs: 2,
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  validatePhase6()
    .then((result) => {
      console.log(
        `[phase6] source guards passed: ${result.responseSchemas} strict response schemas, ${result.realSpecs} E2E specs. This does not certify runtime tests.`,
      );
    })
    .catch((error) => {
      console.error(`[phase6] FAIL: ${error.message}`);
      process.exitCode = 1;
    });
}
