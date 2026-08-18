import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateSessionUiContract } from './validate-phase1.mjs';

const scriptsDir = resolve(fileURLToPath(import.meta.url), '..');
const projectRoot = resolve(scriptsDir, '..');

async function createFixtureTree(overrides = {}) {
  const root = await mkdtemp(join(tmpdir(), 'betufu-phase1-fixture-'));

  const sessionDir = join(root, 'frontend', 'src', 'app', 'core', 'session');
  const layoutDir = join(root, 'frontend', 'src', 'app', 'layout');
  const routesDir = join(root, 'frontend', 'src', 'app');

  await mkdir(sessionDir, { recursive: true });
  await mkdir(layoutDir, { recursive: true });

  const defaultStore = `
import { Injectable } from '@angular/core';
@Injectable()
export class SessionStore {
  loadSession() {}
}
`;

  const defaultLayout = `
import { Component } from '@angular/core';
@Component({ selector: 'app-authenticated-layout', template: '<router-outlet />' })
export class AuthenticatedLayoutComponent {}
`;

  const defaultRoutesDev = `
import type { Routes } from '@angular/router';
import { AuthenticatedLayoutComponent } from './layout/authenticated-layout.component';
export const BUILD_SPECIFIC_ROUTES: Routes = [
  { path: '', component: AuthenticatedLayoutComponent, children: [] }
];
`;

  const defaultRoutesProd = `
import type { Routes } from '@angular/router';
export const BUILD_SPECIFIC_ROUTES: Routes = [];
`;

  await writeFile(join(sessionDir, 'session.store.ts'), overrides.store ?? defaultStore, 'utf8');
  await writeFile(join(layoutDir, 'authenticated-layout.component.ts'), overrides.layout ?? defaultLayout, 'utf8');
  await writeFile(join(routesDir, 'app.routes.development.ts'), overrides.routesDev ?? defaultRoutesDev, 'utf8');
  await writeFile(join(routesDir, 'app.routes.production.ts'), overrides.routesProd ?? defaultRoutesProd, 'utf8');

  return {
    root,
    cleanup: () => rm(root, { recursive: true, force: true }),
  };
}

test('validateSessionUiContract: aceita estrutura canonica valida', async () => {
  const fixture = await createFixtureTree();
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, true);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: recusa localStorage em session.store.ts', async () => {
  const fixture = await createFixtureTree({
    store: 'export class SessionStore { save() { localStorage.setItem("k", "v"); } }',
  });
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: recusa sessionStorage em arquivo de layout', async () => {
  const fixture = await createFixtureTree({
    layout: 'export class Layout { read() { return sessionStorage.getItem("k"); } }',
  });
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: recusa document.cookie ou indexedDB', async () => {
  const fixture = await createFixtureTree({
    store: 'export class SessionStore { check() { return document.cookie; } }',
  });
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: recusa token Bearer manual em session ou layout', async () => {
  const fixture = await createFixtureTree({
    store: 'export const h = { Authorization: "Bearer " + token };',
  });
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: recusa polling com setInterval, timer ou interval', async () => {
  const fixture = await createFixtureTree({
    store: 'export class Store { poll() { setInterval(() => {}, 1000); } }',
  });
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: recusa retry ou retryWhen em chamadas reativas', async () => {
  const fixture = await createFixtureTree({
    store: 'import { retry } from "rxjs"; export const p = obs.pipe(retry(3));',
  });
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: recusa app.routes.development sem AuthenticatedLayoutComponent', async () => {
  const fixture = await createFixtureTree({
    routesDev: 'export const BUILD_SPECIFIC_ROUTES = [{ path: "tracer", component: TracerComponent }];',
  });
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: recusa app.routes.production contendo AuthenticatedLayoutComponent ou Tracer', async () => {
  const fixture = await createFixtureTree({
    routesProd: 'import { AuthenticatedLayoutComponent } from "./layout"; export const BUILD_SPECIFIC_ROUTES = [];',
  });
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

test('validateSessionUiContract: detecta arquivo extra nao-canonico em subdiretorio varrido', async () => {
  const fixture = await createFixtureTree();
  const extraFile = join(fixture.root, 'frontend', 'src', 'app', 'core', 'session', 'token-cache.ts');
  await writeFile(extraFile, 'export const save = () => localStorage.setItem("token", "secret");', 'utf8');
  try {
    const result = await validateSessionUiContract(fixture.root);
    assert.strictEqual(result, false);
  } finally {
    await fixture.cleanup();
  }
});

