import { test } from "node:test";
import assert from "node:assert/strict";
import {
  assertIsolated,
  isolatedConfig,
  requireOwnedContainer,
  requireRun,
  requireOwnedVolume,
} from "./phase6-e2e.mjs";

const run = "betufu-phase6-e2e-0123456789abcdef";
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
  "betufu-phase6-e2e-*",
  "",
  "../owner",
  "betufu-phase6-e2e-0123456789abcdeg",
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
        "br.com.betufu.phase6-e2e": run,
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
    "br.com.betufu.phase6-e2e",
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
      "br.com.betufu.phase6-e2e": run,
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
