import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { test } from "node:test";
import {
  MIGRATION_HASHES,
  RESPONSE_ALLOWLISTS,
  validateMigrationSources,
  validateSources,
} from "./validate-phase6.mjs";

const root = resolve(import.meta.dirname, "..");
const [contract, first, second, config, routes] = await Promise.all(
  [
    "backend/openapi/betufu.yaml",
    "frontend/e2e/phase6-results-ranking.spec.ts",
    "frontend/e2e/phase6-corrections.spec.ts",
    "frontend/playwright.phase6.config.ts",
    "frontend/src/app/app.routes.ts",
  ].map((path) => readFile(resolve(root, path), "utf8")),
);
const baseline = { contract, specs: [first, second], config, routes };
const setup = await readFile(
  resolve(root, "frontend/e2e/phase6.setup.ts"),
  "utf8",
);
test("real isolated setup passes the same anti-mock and credential gates", () =>
  validateSources({ ...baseline, fixtures: [setup] }));
for (const mutation of [
  'page.route("**/api/**", handler)',
  "context.addCookies([])",
  'test.skip("case")',
]) {
  test(`rejects hidden shortcut in fixture: ${mutation}`, () =>
    assert.throws(() =>
      validateSources({ ...baseline, fixtures: [setup + "\n" + mutation] }),
    ));
}
const migrations = Object.fromEntries(
  await Promise.all(
    Object.keys(MIGRATION_HASHES).map(async (name) => [
      name,
      await readFile(
        resolve(root, "backend/src/main/resources/db/migration", name),
        "utf8",
      ),
    ]),
  ),
);

test("approved migrations pass without rewriting historical bytes", () =>
  validateMigrationSources(migrations));
for (const name of Object.keys(MIGRATION_HASHES)) {
  test(`rejects drift in ${name}`, () =>
    assert.throws(
      () =>
        validateMigrationSources({
          ...migrations,
          [name]: migrations[name] + "\n-- mutation",
        }),
      /MIGRATION_DRIFT/u,
    ));
}
test("rejects a missing migration", () =>
  assert.throws(() => validateMigrationSources({}), /MIGRATION_MISSING/u));

test("approved real sources and closed DTOs pass", () =>
  validateSources(baseline));
for (const name of Object.keys(RESPONSE_ALLOWLISTS)) {
  test(`${name} rejects newly exposed PII`, () => {
    const mutation = contract.replace(
      new RegExp(`(    ${name}:[\\s\\S]*?      properties:)`, "u"),
      "$1\n        identityId: {type: string}",
    );
    assert.notEqual(mutation, contract);
    assert.throws(
      () => validateSources({ ...baseline, contract: mutation }),
      /PRIVACY_ALLOWLIST/u,
    );
  });
}
for (const [name, mutation, rule] of [
  [
    "business interception",
    "await page.route('**/api/**', handler);",
    /MOCK_FORBIDDEN/u,
  ],
  [
    "computed interception",
    "await page['route']('**/api/**', handler);",
    /MOCK_FORBIDDEN/u,
  ],
  [
    "HAR substitution",
    "await context.routeFromHAR('facts.har');",
    /MOCK_FORBIDDEN/u,
  ],
  ["cookie injection", "await context.addCookies(cookies);", /AUTH_INJECTION/u],
  [
    "stored authentication",
    "sessionStorage.setItem(key, value);",
    /AUTH_INJECTION/u,
  ],
  [
    "skipped certification",
    "test.skip('unavailable', handler);",
    /FALSE_PASS/u,
  ],
  [
    "literal identifier",
    "const id = '11111111-1111-4111-8111-111111111111';",
    /UUID_LITERAL/u,
  ],
  [
    "literal credential",
    "const password = 'not-a-real-secret';",
    /CREDENTIAL_LITERAL/u,
  ],
]) {
  test(`rejects ${name}`, () =>
    assert.throws(
      () => validateSources({ ...baseline, specs: [first + mutation, second] }),
      rule,
    ));
}
test("rejects missing route", () =>
  assert.throws(
    () =>
      validateSources({
        ...baseline,
        routes: routes.replace("path: 'conta/grupos'", "path: 'wrong'"),
      }),
    /MISSING_ROUTE/u,
  ));
test("rejects missing guard", () =>
  assert.throws(
    () =>
      validateSources({
        ...baseline,
        routes: routes.replace(
          "canActivateChild: [authenticationGuard, profileCompletionGuard]",
          "canActivateChild: []",
        ),
      }),
    /PRIVATE_GUARD_REQUIRED/u,
  ));
test("rejects permissive schema", () =>
  assert.throws(
    () =>
      validateSources({
        ...baseline,
        contract: contract.replace(
          /(    RankingEntry:[\s\S]*?additionalProperties:) false/u,
          "$1 true",
        ),
      }),
    /CLOSED_SCHEMA/u,
  ));
test("rejects missing real endpoint", () =>
  assert.throws(
    () =>
      validateSources({
        ...baseline,
        contract: contract.replace(
          "/api/v1/me/achievements:",
          "/api/v1/me/missing:",
        ),
      }),
    /MISSING_ENDPOINT/u,
  ));
test("rejects unprotected browser artifacts", () =>
  assert.throws(
    () =>
      validateSources({
        ...baseline,
        config: config.replace("trace: 'off'", "trace: 'on'"),
      }),
    /BROWSER_GATE/u,
  ));
