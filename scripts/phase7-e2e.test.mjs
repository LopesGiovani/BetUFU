import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { stripApprovedStompCsrf } from "./validate-runtime-prerequisites.mjs";
import { normalizePhaseSevenRoutes } from "./validate-angular-route-boundary.mjs";
import {
  assertIsolated,
  isolatedConfig,
  requireOwnedContainer,
  requireRun,
  requireOwnedVolume,
  approvedSpec,
  specPlan,
  browserConfig,
  chatHistorySql,
  writeIsolatedConfig,
  isolatedDiagnostics,
  safeDiagnostic,
} from "./phase7-e2e.mjs";

test("isolated config preserves private credentials and container-readable templates", async () => {
  const directory = await mkdtemp(resolve(tmpdir(), "betufu-config-test-"));
  const config = { services: { keycloak: { environment: { PASSWORD: "private-fixture" } } } };
  const realm = JSON.parse(await readFile(new URL("../infra/keycloak/realm-betufu.json", import.meta.url), "utf8"));
  try {
    const path = await writeIsolatedConfig(directory, config, realm, "server {}", undefined);
    assert.deepEqual(JSON.parse(await readFile(path, "utf8")), config);
    const template = await readFile(resolve(directory, "realm.json"), "utf8");
    assert.match(template, /\$\{BETUFU_OIDC_CLIENT_SECRET\}/u);
    assert.equal(template.includes("private-fixture"), false);
    assert.equal(await readFile(resolve(directory, "nginx.conf"), "utf8"), "server {}");
    if (process.platform !== "win32") {
      for (const [name, mode] of [["realm.json", 0o644], ["nginx.conf", 0o644],
        ["compose.json", 0o600], ["playwright.config.mjs", 0o600]])
        assert.equal((await stat(resolve(directory, name))).mode & 0o777, mode, name);
      assert.equal((await stat(directory)).mode & 0o777, 0o700);
    }
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("startup diagnostics reach Keycloak even if other service logs are unavailable", async () => {
  const services = [], reports = [];
  await isolatedDiagnostics(["compose", "-f", "private.json"], async (executable, args) => {
    assert.equal(executable, "docker");
    const service = args.at(-1); services.push(service);
    if (service !== "keycloak") throw Error("private diagnostic detail");
    return 'INFO starting\nERROR Failed to import realm\nCaused by: java.nio.file.AccessDeniedException: realm.json';
  }, message => reports.push(message));
  assert.deepEqual(services, ["frontend", "backend", "keycloak"]);
  assert.ok(reports.some(message => /keycloak:.*ERROR Failed to import realm/su.test(message)));
  assert.ok(reports.some(message => message.includes("AccessDeniedException")));
  assert.equal(reports.join().includes("private diagnostic detail"), false);
});

test("diagnostics redact generated secrets and bound log output", () => {
  const log = [...Array.from({ length: 10 }, (_, index) => String(index)), "ERROR private-fixture"].join("\n");
  const safe = safeDiagnostic(log, new Set(["private-fixture"]));
  assert.equal(safe.split("\n").length, 8);
  assert.ok(safe.endsWith("ERROR [REDACTED]"));
  assert.equal(safe.includes("private-fixture"), false);
});

test("CSRF exception accepts only the owned CONNECT frame and official extractor", async () => {
  const path = "frontend/src/app/features/chat/chat-read.store.ts";
  const source = await readFile(new URL(`../${path}`, import.meta.url), "utf8");
  assert.equal(
    stripApprovedStompCsrf(path, source).includes("X-XSRF-TOKEN"),
    false,
  );
  for (const changed of [
    source.replace("inject(HttpXsrfTokenExtractor)", "inject(FakeExtractor)"),
    source.replace("this.csrf.getToken()", "getFakeToken()"),
    source.replace("CONNECT\\naccept-version", "SEND\\naccept-version"),
    source + "\nconst headers = { 'X-XSRF-TOKEN': 'manual' };",
  ])
    assert.equal(
      stripApprovedStompCsrf(path, changed).includes("X-XSRF-TOKEN"),
      true,
    );
  assert.equal(stripApprovedStompCsrf("frontend/src/main.ts", source), source);
});

test("only the approved Phase7 browser spec is accepted", () => {
  const spec = "frontend/e2e/phase7-chat-read.spec.ts";
  assert.equal(approvedSpec(spec), spec);
  assert.equal(specPlan(spec), "07-08");
  assert.equal(specPlan("frontend/e2e/phase7-chat-write.spec.ts"), "07-12");
  assert.equal(approvedSpec("frontend/e2e/phase7-chat-write.spec.ts"), "frontend/e2e/phase7-chat-write.spec.ts");
  assert.equal(specPlan("frontend/e2e/phase7-chat-community.spec.ts"), "07-15");
  assert.equal(specPlan("frontend/e2e/phase7-moderation.spec.ts"), "07-19");
  assert.equal(specPlan("frontend/e2e/phase7-operations.spec.ts"), "07-23");
  assert.equal(specPlan("frontend/e2e/phase7-privacy.spec.ts"), "07-26");
  assert.equal(specPlan("frontend/e2e/phase7-chat-rules.spec.ts"), "07-35");
  assert.equal(specPlan("frontend/e2e/phase7-integrated.spec.ts"), "07-31");
  for (const bad of [
    "",
    "../owner.spec.ts",
    "frontend/e2e/phase7-integrated.spec.ts;echo",
    spec + ";echo",
  ])
    assert.throws(() => approvedSpec(bad));
  const config = browserConfig(spec, "/tmp/owned");
  assert.match(config, /phase7-chat-read\.spec\.ts/u);
  assert.doesNotMatch(config, /globalSetup/u);
  assert.match(config, /"retries":0/u);
  assert.match(config, /"width":320/u);
});

test("historical fixture appends facts only and never fabricates accepted write receipts", () => {
  const sql = chatHistorySql();
  assert.match(sql, /INSERT INTO public\.chat_message/u);
  assert.doesNotMatch(sql, /UPDATE|DELETE|TRUNCATE|chat_write_receipt/u);
  assert.match(sql, /sports_event_publication/u);
});

test("isolated browser certifies a fresh production bundle before building the image", async () => {
  const runner = await readFile(new URL("./phase7-e2e.mjs", import.meta.url), "utf8");
  const start = runner.indexOf('export async function runIsolated');
  const execution = runner.slice(start);
  assert.ok(execution.indexOf('"build", "--configuration", "production"') < execution.indexOf('"up",'));
  assert.ok(execution.includes('frontend/node_modules/@angular/cli/bin/ng.js'));
});

test("community normalization only accepts the exact public Arena lazy route", () => {
  const route =
    "{ path: 'comunidade', loadComponent: () => import('./features/chat/community-page.component').then(({ CommunityPageComponent }) => CommunityPageComponent), },";
  const source =
    "{ component: ArenaShellComponent, children: [" +
    route +
    "] }, { path: 'entrar' }";
  assert.doesNotMatch(normalizePhaseSevenRoutes(source), /comunidade/u);
  assert.equal(normalizePhaseSevenRoutes("legacy"), "legacy");
  for (const bad of [
    source.replace("community-page.component", "owner.component"),
    source.replace("community-page.component", "chat-panel.component"),
    source.replace("CommunityPageComponent", "PrivateComponent"),
    source.replace(route, route + route),
    "{ component: ArenaShellComponent, children: [] }, { path: 'entrar' }," +
      route,
  ])
    assert.throws(() => normalizePhaseSevenRoutes(bad));
});

const run = "betufu-phase7-e2e-0123456789abcdef";
function source() {
  const services = Object.fromEntries(
    [
      "postgres",
      "redis",
      "seaweedfs",
      "mailpit",
      "backend",
      "frontend",
      "keycloak",
    ].map((name) => [
      name,
      {
        image: "pinned",
        environment: {},
        profiles: ["full"],
        networks: { default: null },
        ports: [{ target: 8080, published: "4200" }],
        volumes: [{ type: "volume", source: "data", target: "/data" }],
      },
    ]),
  );
  services.keycloak.volumes.push({
    type: "bind",
    source: "/original/realm.json",
    target: "/opt/keycloak/data/import/betufu-local-realm.json",
    read_only: true,
  });
  return {
    services,
    volumes: { data: { name: "betufu-local-data" } },
    networks: { default: { name: "infra_default" } },
  };
}
test("clones seven services without mutating approved owner configuration", () => {
  const original = source();
  const before = structuredClone(original);
  const config = isolatedConfig(original, run, "/tmp/owned");
  assert.deepEqual(original, before);
  assertIsolated(config, run);
  assert.equal(config.volumes.data.name, `${run}-data`);
  assert.equal(config.services.backend.image, `betufu/backend:${run}`);
  assert.equal(config.services.keycloak.image, `betufu/keycloak:${run}`);
  assert.deepEqual(config.services.keycloak.build, {
    context: resolve(import.meta.dirname, ".."),
    dockerfile: "infra/keycloak/Dockerfile",
  });
  assert.equal(
    config.services.backend.environment.BETUFU_PUBLIC_ORIGIN,
    "http://localhost:4300",
  );
  assert.equal(config.services.redis.ports, undefined);
  assert.equal(
    JSON.parse(config.services.backend.environment.SPRING_APPLICATION_JSON)
      .betufu.web["public-origin"],
    "http://localhost:4300",
  );
});
for (const invalid of [
  "infra",
  "betufu-phase7-e2e-*",
  "",
  "../owner",
  "betufu-phase7-e2e-0123456789abcdeg",
]) {
  test(`rejects invalid ownership token ${invalid}`, () =>
    assert.throws(() => requireRun(invalid)));
}
for (const [name, mutate] of [
  [
    "shared volume",
    (c) => (c.volumes.data.name = "betufu-local-postgres-data"),
  ],
  ["external volume", (c) => (c.volumes.data.external = true)],
  ["shared network", (c) => (c.networks.default.name = "infra_default")],
  ["host network", (c) => (c.services.redis.network_mode = "host")],
  ["owner port", (c) => (c.services.frontend.ports[0].published = "4200")],
  ["public bind", (c) => (c.services.frontend.ports[0].host_ip = "0.0.0.0")],
  ["writable host", (c) => (c.services.frontend.volumes[0].read_only = false)],
  ["undeclared volume", (c) => (c.services.redis.volumes[0].source = "owner")],
]) {
  test(`fails closed on ${name}`, () => {
    const config = isolatedConfig(source(), run, "/tmp/owned");
    mutate(config);
    assert.throws(() => assertIsolated(config, run));
  });
}
function inspected() {
  return {
    Config: {
      Labels: {
        "br.com.betufu.phase7-e2e": run,
        "com.docker.compose.project": run,
        "com.docker.compose.service": "postgres",
      },
    },
    Mounts: [{ Type: "volume", Name: `${run}-data` }],
  };
}
test("requires runtime labels and actual volumes before SQL or cleanup", () => {
  requireOwnedContainer(inspected(), run, "postgres");
  for (const key of [
    "br.com.betufu.phase7-e2e",
    "com.docker.compose.project",
    "com.docker.compose.service",
  ]) {
    const value = inspected();
    value.Config.Labels[key] = "infra";
    assert.throws(() => requireOwnedContainer(value, run, "postgres"));
  }
  const shared = inspected();
  shared.Mounts[0].Name = "betufu-local-postgres-data";
  assert.throws(() => requireOwnedContainer(shared, run, "postgres"));
});

test("requires exact actual volume name and all ownership labels before removal", () => {
  const volume = {
    Name: `${run}-data`,
    Labels: {
      "br.com.betufu.phase7-e2e": run,
      "com.docker.compose.project": run,
      "com.docker.compose.volume": "data",
    },
  };
  requireOwnedVolume(volume, run, "data");
  for (const key of Object.keys(volume.Labels)) {
    const other = structuredClone(volume);
    other.Labels[key] = "infra";
    assert.throws(() => requireOwnedVolume(other, run, "data"));
  }
  assert.throws(() =>
    requireOwnedVolume(
      { ...volume, Name: "betufu-local-postgres-data" },
      run,
      "data",
    ),
  );
});
