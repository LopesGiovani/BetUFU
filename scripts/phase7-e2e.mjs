#!/usr/bin/env node
import { spawn } from "node:child_process";
import { randomBytes, createHmac } from "node:crypto";
import {
  mkdtemp,
  readFile,
  writeFile,
  unlink,
  rmdir,
  rm,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve, basename } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { setTimeout as delay } from "node:timers/promises";
import { buildSql, PERSONAS, sql, stableUuid } from "./seed-local.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const OWNER = "br.com.betufu.phase7-e2e";
const privateValues = new Set();
const SPEC_PLANS = Object.freeze({
  "frontend/e2e/phase7-chat-read.spec.ts": "07-08",
  "frontend/e2e/phase7-chat-write.spec.ts": "07-12",
  "frontend/e2e/phase7-chat-community.spec.ts": "07-15",
  "frontend/e2e/phase7-moderation.spec.ts": "07-19",
  "frontend/e2e/phase7-operations.spec.ts": "07-23",
  "frontend/e2e/phase7-privacy.spec.ts": "07-26",
  "frontend/e2e/phase7-chat-rules.spec.ts": "07-35",
});
export function specPlan(value) {
  if (!Object.hasOwn(SPEC_PLANS, value)) throw new Error("Unapproved browser spec");
  return SPEC_PLANS[value];
}
export function approvedSpec(value) {
  specPlan(value);
  return value;
}
export function browserConfig(spec, directory) {
  approvedSpec(spec);
  const module = pathToFileURL(
    resolve(ROOT, "frontend/node_modules/@playwright/test/index.mjs"),
  ).href;
  return (
    "import { defineConfig } from " +
    JSON.stringify(module) +
    ";\nexport default defineConfig(" +
    JSON.stringify({
      testDir: resolve(ROOT, "frontend/e2e"),
      testMatch: [basename(spec)],
      fullyParallel: false,
      forbidOnly: true,
      retries: 0,
      workers: 1,
      timeout: 180000,
      expect: { timeout: 15000 },
      reporter: [["line"]],
      outputDir: resolve(directory, "results"),
      preserveOutput: "never",
      use: {
        baseURL: "http://localhost:4300",
        headless: true,
        browserName: "chromium",
        channel: "chrome",
        launchOptions: {
          args: ["--host-resolver-rules=MAP localhost 127.0.0.1"],
        },
        locale: "pt-BR",
        timezoneId: "America/Sao_Paulo",
        serviceWorkers: "block",
        trace: "off",
        screenshot: "off",
        video: "off",
      },
      projects: [
        {
          name: "phase7-desktop",
          use: { viewport: { width: 1280, height: 800 } },
        },
        {
          name: "phase7-320px",
          use: { viewport: { width: 320, height: 800 } },
        },
      ],
    }) +
    ");\n"
  );
}
function safeDiagnostic(value) {
  let safe = value;
  for (const secret of privateValues)
    safe = safe.replaceAll(secret, "[REDACTED]");
  return safe.split(/\r?\n/u).slice(-8).join("\n");
}
export function requireRun(run) {
  if (!/^betufu-phase7-e2e-[a-f0-9]{16}$/u.test(run))
    throw new Error("Invalid isolated run");
  return run;
}

export function isolatedConfig(source, run, directory) {
  requireRun(run);
  const config = structuredClone(source);
  config.name = run;
  config.networks = {
    default: { name: `${run}-network`, labels: { [OWNER]: run } },
  };
  for (const [key, volume] of Object.entries(config.volumes)) {
    if (volume.external) throw new Error("External volume prohibited");
    config.volumes[key] = { name: `${run}-${key}`, labels: { [OWNER]: run } };
  }
  for (const [name, service] of Object.entries(config.services)) {
    delete service.profiles;
    delete service.container_name;
    service.labels = { [OWNER]: run };
    service.networks = { default: null };
    delete service.ports;
    for (const mount of service.volumes ?? []) {
      if (
        mount.type === "volume" &&
        !Object.hasOwn(config.volumes, mount.source)
      ) {
        throw new Error("Undeclared volume prohibited");
      }
    }
    if (["backend", "frontend"].includes(name))
      service.image = `betufu/${name}:${run}`;
  }
  const bind = (sourcePath, target) => ({
    type: "bind",
    source: resolve(directory, sourcePath),
    target,
    read_only: true,
  });
  config.services.frontend.ports = [
    { target: 8080, published: "4300", host_ip: "127.0.0.1", protocol: "tcp" },
  ];
  config.services.frontend.volumes = [
    bind("nginx.conf", "/etc/nginx/conf.d/default.conf"),
  ];
  config.services.keycloak.ports = [
    { target: 8080, published: "8281", host_ip: "127.0.0.1", protocol: "tcp" },
  ];
  config.services.keycloak.environment.KC_HOSTNAME = "http://localhost:8281";
  config.services.keycloak.volumes = config.services.keycloak.volumes.map(
    (mount) =>
      mount.target === "/opt/keycloak/data/import/betufu-local-realm.json"
        ? bind("realm.json", mount.target)
        : mount,
  );
  Object.assign(config.services.backend.environment, {
    BETUFU_PUBLIC_ORIGIN: "http://localhost:4300",
    BETUFU_OIDC_ISSUER_URI: "http://localhost:8281/realms/betufu-local",
    SPRING_APPLICATION_JSON: JSON.stringify({
      betufu: {
        web: {
          "public-origin": "http://localhost:4300",
          "backend-origin": "http://backend:8080",
        },
      },
    }),
  });
  config.services.backend.entrypoint = [
    "sh",
    "-c",
    "socat TCP-LISTEN:8281,fork,bind=127.0.0.1 TCP:keycloak:8080 & exec java $$JAVA_OPTS -jar /app/app.jar",
  ];
  assertIsolated(config, run);
  return config;
}

export function assertIsolated(config, run) {
  requireRun(run);
  if (
    config.name !== run ||
    Object.keys(config.networks).join() !== "default" ||
    config.networks.default.name !== `${run}-network` ||
    config.networks.default.external
  ) {
    throw new Error("Shared network prohibited");
  }
  for (const [key, volume] of Object.entries(config.volumes)) {
    if (
      volume.name !== `${run}-${key}` ||
      volume.external ||
      volume.labels?.[OWNER] !== run
    ) {
      throw new Error("Shared volume prohibited");
    }
  }
  for (const service of Object.values(config.services)) {
    if (
      service.labels?.[OWNER] !== run ||
      service.container_name ||
      service.network_mode ||
      Object.keys(service.networks).join() !== "default"
    )
      throw new Error("Shared container prohibited");
    for (const mount of service.volumes ?? []) {
      if (
        mount.type === "volume" &&
        !Object.hasOwn(config.volumes, mount.source)
      )
        throw new Error("Shared mount prohibited");
      if (mount.type === "bind" && !mount.read_only)
        throw new Error("Writable host bind prohibited");
    }
    for (const port of service.ports ?? []) {
      if (
        port.host_ip !== "127.0.0.1" ||
        !["4300", "8281"].includes(String(port.published))
      )
        throw new Error("Nonisolated port prohibited");
    }
  }
}

export function requireOwnedContainer(inspect, run, service) {
  requireRun(run);
  const labels = inspect.Config?.Labels;
  if (
    labels?.[OWNER] !== run ||
    labels?.["com.docker.compose.project"] !== run ||
    labels?.["com.docker.compose.service"] !== service
  )
    throw new Error("Container ownership mismatch");
  for (const mount of inspect.Mounts ?? []) {
    if (mount.Type === "volume" && !mount.Name.startsWith(`${run}-`))
      throw new Error("Container shares persistent volume");
  }
}

export function requireOwnedVolume(inspect, run, key) {
  requireRun(run);
  if (
    inspect.Name !== `${run}-${key}` ||
    inspect.Labels?.[OWNER] !== run ||
    inspect.Labels?.["com.docker.compose.project"] !== run ||
    inspect.Labels?.["com.docker.compose.volume"] !== key
  )
    throw new Error("Volume ownership mismatch");
}

async function command(
  executable,
  args,
  { input, env = process.env, visible = false, cwd = ROOT } = {},
) {
  return new Promise((done, reject) => {
    const child = spawn(executable, args, {
      cwd,
      env,
      shell: false,
      windowsHide: true,
      stdio: ["pipe", "pipe", "pipe"],
    });
    let output = "";
    let diagnostic = "";
    child.stdout.on("data", (chunk) => {
      output += chunk;
      if (visible) process.stdout.write(chunk);
    });
    child.stderr.on("data", (chunk) => {
      diagnostic += chunk;
      if (visible) process.stderr.write(chunk);
    });
    child.on("error", reject);
    child.on("close", (code) =>
      code === 0
        ? done(output)
        : reject(
            new Error(
              `${executable} operation failed (exit ${code}): ${safeDiagnostic(diagnostic)}`,
            ),
          ),
    );
    child.stdin.end(input);
  });
}

async function ownedService(compose, run, service) {
  const id = (
    await command("docker", [...compose, "ps", "-q", service])
  ).trim();
  if (!/^[a-f0-9]{12,64}$/u.test(id))
    throw new Error("Expected exactly one owned service");
  const [inspect] = JSON.parse(await command("docker", ["inspect", id]));
  requireOwnedContainer(inspect, run, service);
  return id;
}

// Expires only the isolated store. It neither deletes browser cookies nor injects authentication.
export async function expireIsolatedSessions(run) {
  requireRun(run);
  const ids = (
    await command("docker", [
      "ps",
      "-q",
      "--filter",
      `label=${OWNER}=${run}`,
      "--filter",
      "label=com.docker.compose.service=redis",
    ])
  ).trim();
  const [inspect] = JSON.parse(await command("docker", ["inspect", ids]));
  requireOwnedContainer(inspect, run, "redis");
  const script =
    "local n=0; for _,k in ipairs(redis.call('KEYS','betufu:session:sessions:*')) do redis.call('EXPIRE',k,1); n=n+1 end; return n";
  const count = await command("docker", [
    "exec",
    ids,
    "redis-cli",
    "-e",
    "EVAL",
    script,
    "0",
  ]);
  if (Number(count.trim()) < 1) throw new Error("No real session to expire");
  // Do not let the first authenticated poll renew a still-live session.
  await delay(1500);
}

export async function releaseIsolatedStudentEnrollment(run, persona = "student") {
  if (!["student", "athlete"].includes(persona)) throw new Error("Unapproved eligibility persona");
  requireRun(run);
  const id = (
    await command("docker", [
      "ps",
      "-q",
      "--filter",
      `label=${OWNER}=${run}`,
      "--filter",
      "label=com.docker.compose.service=postgres",
    ])
  ).trim();
  const [inspect] = JSON.parse(await command("docker", ["inspect", id]));
  requireOwnedContainer(inspect, run, "postgres");
  const env = Object.fromEntries(
    inspect.Config.Env.map((value) => {
      const index = value.indexOf("=");
      return [value.slice(0, index), value.slice(index + 1)];
    }),
  );
  // Controlled eligibility substrate only; no result, prediction or ledger facts are written.
  await command(
    "docker",
    [
      "exec",
      "-i",
      id,
      "psql",
      "-X",
      "-v",
      "ON_ERROR_STOP=1",
      "-U",
      env.POSTGRES_USER,
      "-d",
      env.BETUFU_DB_NAME,
    ],
    {
      input: `UPDATE public.ufu_enrollment SET status='LIBERADO',updated_at=CURRENT_TIMESTAMP(6) WHERE identity_id=${sql(stableUuid("identity:" + persona))} AND status='ATIVO';`,
    },
  );
}

export function chatHistorySql() {
  const actor = sql(stableUuid("identity:student"));
  return `INSERT INTO public.chat_channel(kind,event_id) SELECT DISTINCT 'EVENT', event_id FROM public.sports_event_publication ON CONFLICT DO NOTHING;
INSERT INTO public.chat_message(channel_id,author_identity_id,body,posted_at)
SELECT c.channel_id, ${actor}, CASE WHEN i=60 THEN '<img src=x onerror=alert(1)>' ELSE 'Historico '||lpad(i::text,2,'0')||' - '||repeat('leitura ',20) END,
CURRENT_TIMESTAMP(6)-interval '1 hour'+i*interval '3 seconds'
FROM public.chat_channel c CROSS JOIN generate_series(1,60) i;`;
}

async function provision(compose, run, config, password, plan) {
  const keycloak = config.services.keycloak.environment;
  const base = "http://127.0.0.1:8281";
  const request = async (path, init = {}) => {
    const response = await fetch(`${base}${path}`, {
      ...init,
      redirect: "error",
      signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok)
      throw new Error(
        `Isolated identity provisioning failed (${response.status})`,
      );
    return response.status === 201 || response.status === 204
      ? null
      : response.json();
  };
  const token = await request("/realms/master/protocol/openid-connect/token", {
    method: "POST",
    body: new URLSearchParams({
      grant_type: "password",
      client_id: "admin-cli",
      username: keycloak.KC_BOOTSTRAP_ADMIN_USERNAME,
      password: keycloak.KC_BOOTSTRAP_ADMIN_PASSWORD,
    }),
  });
  const headers = {
    authorization: `Bearer ${token.access_token}`,
    "content-type": "application/json",
  };
  const personas = [];
  const loadPersonas = plan === "07-30" ? Array.from({ length: 50 }, (_, index) => ({
    key: `load-${index}`, username: `phase7-load-${index}@example.invalid`,
    email: `phase7-load-${index}@example.invalid`, nickname: `carga-${index}`,
  })) : [];
  for (const persona of [...PERSONAS, ...loadPersonas]) {
    await request("/admin/realms/betufu-local/users", {
      method: "POST",
      headers,
      body: JSON.stringify({
        username: persona.username,
        email: persona.email,
        firstName: "E2E",
        lastName: persona.key,
        enabled: true,
        emailVerified: true,
        requiredActions: [],
        credentials: [{ type: "password", value: password, temporary: false }],
      }),
    });
    const users = await request(
      `/admin/realms/betufu-local/users?username=${encodeURIComponent(persona.username)}&exact=true`,
      { headers },
    );
    if (users.length !== 1) throw new Error("Ambiguous isolated persona");
    personas.push({ ...persona, subject: users[0].id });
  }
  const fixture = JSON.parse(
    await readFile(
      resolve(ROOT, "scripts/data/laufu/laufu_partidas_2026.json"),
      "utf8",
    ),
  );
  const matches = (Array.isArray(fixture) ? fixture : fixture.partidas).slice(
    0,
    8,
  );
  // Future substrate avoids wall-clock dependent market closure. Results remain real API facts.
  const future = new Date(Date.now() + 2 * 86_400_000);
  const seed = buildSql({
    personas,
    matches,
    now: future,
    env: {
      BETUFU_ISSUER: config.services.backend.environment.BETUFU_OIDC_ISSUER_URI,
      BETUFU_MATRICULA_HMAC_KEY_V1:
        config.services.backend.environment.BETUFU_MATRICULA_HMAC_KEY_V1,
    },
  });
  const checkerId = stableUuid("identity:checker");
  const profiles = personas
    .filter((persona) => !["student", "athlete"].includes(persona.key))
    .map(
      (persona) =>
        `INSERT INTO public.betufu_profile(profile_id,identity_id,nickname) VALUES (${sql(stableUuid(`profile:${persona.key}`))},${sql(stableUuid(`identity:${persona.key}`))},${sql(persona.nickname)});`,
    )
    .join("\n");
  const roles = `INSERT INTO public.operational_role_grant(grant_id,identity_id,role_name,scope_ref,status) VALUES (${sql(stableUuid("e2e:checker:global"))},${sql(checkerId)},'ADMINISTRADOR_GERAL',NULL,'VIGENTE');`;
  const id = await ownedService(compose, run, "postgres");
  const pg = config.services.postgres.environment;
  const privateAthlete = plan === "07-26"
    ? `INSERT INTO public.sports_athlete(athlete_id,name,slug) VALUES (${sql(stableUuid("athlete:e2e-without-account"))},'Atleta sem conta isolado','e2e-without-account');`
    : "";
  const loadEnrollment = loadPersonas.map(persona => {
    const identity = sql(stableUuid(`identity:${persona.key}`));
    const hmac = createHmac("sha256", Buffer.from(config.services.backend.environment.BETUFU_MATRICULA_HMAC_KEY_V1, "base64"))
      .update(persona.key).digest("hex");
    // Owned eligibility/initial-grant substrate only; accepted predictions still use the real API.
    return `INSERT INTO public.ufu_enrollment(enrollment_id,identity_id,matricula_hmac,hmac_version,status) VALUES (${sql(stableUuid(`enrollment:${persona.key}`))},${identity},decode(${sql(hmac)},'hex'),1,'ATIVO');
INSERT INTO public.credit_account(identity_id,balance) VALUES (${identity},1000.00);
INSERT INTO public.credit_ledger_entry(identity_id,entry_type,direction,amount,source_type,source_id,idempotency_key) VALUES (${identity},'GRANT_INITIAL','CREDIT',1000.00,'SYSTEM',${identity},${sql(`credit:initial-grant:${stableUuid(`identity:${persona.key}`)}`)});`;
  }).join("\n");
  await command(
    "docker",
    [
      "exec",
      "-i",
      id,
      "psql",
      "-X",
      "-v",
      "ON_ERROR_STOP=1",
      "-U",
      pg.POSTGRES_USER,
      "-d",
      pg.BETUFU_DB_NAME,
    ],
    { input: seed + "\n" + roles + "\n" + profiles + "\n" + loadEnrollment + "\n" + chatHistorySql() + "\n" + privateAthlete },
  );
  return loadPersonas;
}

export async function runIsolated(spec, loadWorkload) {
  let plan;
  if (spec === null && typeof loadWorkload === "function") {
    // Durable, owner-ratified local profile supports later CI runs after plan commits.
    // It grants no publication or owner-resource authority.
    const { validateLoadAuthority } = await import("./phase7-load.mjs");
    await validateLoadAuthority();
    plan = "07-30";
  } else {
    if (loadWorkload !== undefined) throw new Error("Unapproved isolated workload");
    approvedSpec(spec);
    const { validatePhase7Approvals } = await import("./validate-phase7-approvals.mjs");
    await validatePhase7Approvals({ plan: specPlan(spec), stage: "implementation" });
    plan = specPlan(spec);
  }
  const run = `betufu-phase7-e2e-${randomBytes(8).toString("hex")}`;
  const directory = await mkdtemp(resolve(tmpdir(), "betufu-phase7-e2e-"));
  const secret = () => randomBytes(24).toString("hex");
  const env = { ...process.env };
  for (const key of [
    "POSTGRES_PASSWORD",
    "BETUFU_DB_PASSWORD",
    "BETUFU_FLYWAY_PASSWORD",
    "KEYCLOAK_DB_PASSWORD",
    "BETUFU_REDIS_PASSWORD",
    "KC_BOOTSTRAP_ADMIN_PASSWORD",
    "BETUFU_OIDC_CLIENT_SECRET",
    "BETUFU_TECHNICAL_PASSWORD",
    "BETUFU_S3_ACCESS_KEY_ID",
    "BETUFU_S3_SECRET_ACCESS_KEY",
  ])
    env[key] = secret();
  env.BETUFU_MATRICULA_HMAC_KEY_V1 = randomBytes(32).toString("base64");
  env.BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION = "v1";
  const source = JSON.parse(
    await command(
      "docker",
      [
        "compose",
        "--env-file",
        "infra/.env.example",
        "-f",
        "infra/compose.yaml",
        "--profile",
        "full",
        "config",
        "--format",
        "json",
      ],
      { env },
    ),
  );
  const config = isolatedConfig(source, run, directory);
  for (const service of Object.values(config.services)) {
    for (const [key, value] of Object.entries(service.environment ?? {})) {
      if (
        /PASSWORD|SECRET|HMAC_KEY|ACCESS_KEY/u.test(key) &&
        typeof value === "string" &&
        value.length >= 6
      )
        privateValues.add(value);
    }
  }
  const realm = JSON.parse(
    await readFile(resolve(ROOT, "infra/keycloak/realm-betufu.json"), "utf8"),
  );
  const client = realm.clients.find((item) => item.clientId === "betufu-bff");
  client.redirectUris = ["http://localhost:4300/login/oauth2/code/keycloak"];
  client.webOrigins = ["http://localhost:4300"];
  client.attributes["post.logout.redirect.uris"] = "http://localhost:4300/";
  const nginx = (
    await readFile(resolve(ROOT, "infra/nginx/nginx.conf"), "utf8")
  ).replaceAll("X-Forwarded-Port 4200", "X-Forwarded-Port 4300");
  await writeFile(resolve(directory, "realm.json"), JSON.stringify(realm), {
    mode: 0o600,
  });
  await writeFile(resolve(directory, "nginx.conf"), nginx, { mode: 0o600 });
  await writeFile(
    resolve(directory, "playwright.config.mjs"),
    browserConfig(spec ?? "frontend/e2e/phase7-chat-read.spec.ts", directory),
    { mode: 0o600 },
  );
  const configPath = resolve(directory, "compose.json");
  await writeFile(configPath, JSON.stringify(config), { mode: 0o600 });
  const compose = ["compose", "-f", configPath, "--project-name", run];
  console.log(`[phase7-e2e] Isolated run ${run}; owner volumes untouched.`);
  try {
    // The image copies dist instead of compiling src. Never certify a stale bundle.
    await command(process.execPath, [
      resolve(ROOT, "frontend/node_modules/@angular/cli/bin/ng.js"),
      "build", "--configuration", "production",
    ], { cwd: resolve(ROOT, "frontend"), visible: true });
    await command("docker", [
      ...compose,
      "up",
      "-d",
      "--build",
      "--wait",
      "--wait-timeout",
      "300",
    ]);
    for (const service of Object.keys(config.services))
      await ownedService(compose, run, service);
    const password = secret();
    privateValues.add(password);
    const loadPersonas = await provision(compose, run, config, password, plan);
    let channels = "{}";
    if (plan === "07-23") {
      const postgres = await ownedService(compose, run, "postgres");
      const pg = config.services.postgres.environment;
      channels = (await command("docker", ["exec", postgres, "psql", "-X", "-At", "-v", "ON_ERROR_STOP=1",
        "-U", pg.POSTGRES_USER, "-d", pg.BETUFU_DB_NAME, "-c",
        "SELECT json_object_agg(COALESCE(event_id::text,'global'),channel_id) FROM public.chat_channel",
      ])).trim();
    }
    const childEnv = {
      ...process.env,
      BETUFU_E2E_BASE_URL: "http://localhost:4300",
      BETUFU_E2E_ISOLATED_RUN: run,
      BETUFU_E2E_CHANNELS: channels,
      BETUFU_E2E_STUDENT_ID: stableUuid("identity:student"),
      BETUFU_E2E_PRIVATE_ATHLETE_ID: stableUuid("athlete:e2e-without-account"),
      BETUFU_E2E_USERNAME: PERSONAS.find((p) => p.key === "student").username,
      BETUFU_E2E_PASSWORD: password,
      BETUFU_E2E_OTHER_USERNAME: PERSONAS.find((p) => p.key === "athlete")
        .username,
      BETUFU_E2E_OTHER_PASSWORD: password,
      BETUFU_E2E_ADMIN_USERNAME: PERSONAS.find((p) => p.key === "admin")
        .username,
      BETUFU_E2E_ADMIN_PASSWORD: password,
      BETUFU_E2E_CHECKER_USERNAME: PERSONAS.find((p) => p.key === "checker")
        .username,
      BETUFU_E2E_CHECKER_PASSWORD: password,
      BETUFU_E2E_MODERATOR_USERNAME: PERSONAS.find((p) => p.key === "moderator").username,
      BETUFU_E2E_MODERATOR_PASSWORD: password,
    };
    if (loadWorkload) await loadWorkload({ run, users: loadPersonas, password });
    else await command(
      process.execPath,
      [
        resolve(ROOT, "frontend/node_modules/@playwright/test/cli.js"),
        "test",
        "--config=" + resolve(directory, "playwright.config.mjs"),
      ],
      { cwd: resolve(ROOT, "frontend"), env: childEnv, visible: true },
    );
  } catch (failure) {
    const proxyLog = await command("docker", [
      ...compose,
      "logs",
      "--no-color",
      "--tail",
      "100",
      "frontend",
    ]);
    const pushRequests = proxyLog
      .split(/\r?\n/u)
      .filter((line) => line.includes("GET /api/v1/chat/push "));
    if (pushRequests.length)
      console.error(safeDiagnostic(pushRequests.join("\n")));
    const log = await command("docker", [
      ...compose,
      "logs",
      "--no-color",
      "--tail",
      "120",
      "backend",
    ]);
    const errors = log
      .split(/\r?\n/u)
      .filter((line) =>
        /ERROR|Exception|Caused by|Description:|Action:/u.test(line),
      )
      .map((line) => {
        const start = line.indexOf("{");
        if (start < 0) return line;
        try {
          const entry = JSON.parse(line.slice(start));
          return entry.message;
        } catch {
          return "[unstructured backend error]";
        }
      });
    if (errors.length) console.error(safeDiagnostic(errors.join("\n")));
    throw failure;
  } finally {
    const ids = (await command("docker", [...compose, "ps", "-aq"]))
      .trim()
      .split(/\s+/u)
      .filter(Boolean);
    for (const id of ids) {
      const [inspect] = JSON.parse(await command("docker", ["inspect", id]));
      requireOwnedContainer(
        inspect,
        run,
        inspect.Config.Labels["com.docker.compose.service"],
      );
    }
    assertIsolated(config, run);
    const names = (
      await command("docker", [
        "volume",
        "ls",
        "--filter",
        `label=${OWNER}=${run}`,
        "--format",
        "{{.Name}}",
      ])
    )
      .trim()
      .split(/\s+/u)
      .filter(Boolean);
    for (const name of names) {
      const key = Object.keys(config.volumes).find(
        (candidate) => `${run}-${candidate}` === name,
      );
      if (!key) throw new Error("Unexpected owned volume");
      const [inspect] = JSON.parse(
        await command("docker", ["volume", "inspect", name]),
      );
      requireOwnedVolume(inspect, run, key);
    }
    await command("docker", [
      ...compose,
      "down",
      "--volumes",
      "--timeout",
      "15",
    ]);
    await rm(resolve(directory, "results"), { recursive: true, force: true });
    for (const name of [
      "compose.json",
      "realm.json",
      "nginx.conf",
      "playwright.config.mjs",
    ])
      await unlink(resolve(directory, name));
    await rmdir(directory);
    console.log(
      `[phase7-e2e] Removed only ${run} containers/network/volumes and generated private configuration.`,
    );
  }
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  try {
    const args = process.argv.slice(2);
    if (args.length === 2 && args[0] === "expire")
      await expireIsolatedSessions(args[1]);
    else if (args.length === 2 && args[0] === "release-student")
      await releaseIsolatedStudentEnrollment(args[1]);
    else if (args.length === 2 && args[0] === "release-athlete")
      await releaseIsolatedStudentEnrollment(args[1], "athlete");
    else {
      if (args.length !== 3 || args[0] !== "--isolated" || args[1] !== "--spec")
        throw new Error("Invalid isolated operation");
      await runIsolated(approvedSpec(args[2]));
    }
  } catch (error) {
    console.error(`[phase7-e2e] ${error.message}`);
    process.exitCode = 1;
  }
}
