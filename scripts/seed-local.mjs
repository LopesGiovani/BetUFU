#!/usr/bin/env node

/**
 * Seeder local BETUFU. Atua somente contra o realm betufu-local e o banco local.
 * Perfis: daily (recorte rápido) e full (fixture LAUFU completo).
 */

import { createHash, createHmac } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const FIXTURE_PATH = resolve(ROOT, 'scripts', 'data', 'laufu', 'laufu_partidas_2026.json');
const DEFAULT_ISSUER = 'http://localhost:8081/realms/betufu-local';
const DEFAULT_KEYCLOAK = 'http://localhost:8081';
const DEFAULT_REALM = 'betufu-local';
const PASSWORD = 'BetUfuTest@2026';
const DAILY_MATCHES = 8;
const SEED_TIME_ZONE = 'America/Sao_Paulo';
const SEED_TIME_ZONE_OFFSET = '-03:00';

export const PERSONAS = Object.freeze([
  { key: 'admin', username: 'admin.geral@betufu.local', email: 'admin.geral@betufu.local', role: 'ADMINISTRADOR_GERAL', scope: null, nickname: 'admin-geral' },
  { key: 'maker', username: 'maker.odds@betufu.local', email: 'maker.odds@betufu.local', role: 'ADMINISTRADOR_COMPETICAO', scope: 'competition', nickname: 'maker-odds' },
  { key: 'checker', username: 'checker.odds@betufu.local', email: 'checker.odds@betufu.local', role: 'ADMINISTRADOR_COMPETICAO', scope: 'competition', nickname: 'checker-odds' },
  { key: 'moderator', username: 'moderador@betufu.local', email: 'moderador@betufu.local', role: 'MODERADOR', scope: 'competition', nickname: 'moderador' },
  { key: 'student', username: 'aluno@ufu.br', email: 'aluno@ufu.br', role: null, scope: null, nickname: 'aluno-ufu' },
  { key: 'athlete', username: 'atleta@ufu.br', email: 'atleta@ufu.br', role: null, scope: null, nickname: 'atleta-ufu' },
]);

function stableUuid(value) {
  const hex = createHash('sha256').update(`betufu-seed:${value}`).digest('hex').slice(0, 32).split('');
  hex[12] = '5';
  hex[16] = ((Number.parseInt(hex[16], 16) & 0x3) | 0x8).toString(16);
  return `${hex.slice(0, 8).join('')}-${hex.slice(8, 12).join('')}-${hex.slice(12, 16).join('')}-${hex.slice(16, 20).join('')}-${hex.slice(20).join('')}`;
}

function sql(value) {
  if (value === null || value === undefined) return 'NULL';
  if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
  if (typeof value === 'number') return String(value);
  return `'${String(value).replaceAll("'", "''")}'`;
}

function sqlJson(value) {
  return sql(JSON.stringify(value));
}

function oddsSelectionSql(publicationId, publishedOdds) {
  if (!Array.isArray(publishedOdds) || ![2, 3].includes(publishedOdds.length)) {
    throw new Error('vetor de odds deve possuir duas ou tres selecoes');
  }
  if (publishedOdds.some((odd) => !Number.isFinite(odd) || odd <= 0)) {
    throw new Error('vetor de odds deve conter somente valores positivos');
  }
  const selectionKeys = publishedOdds.length === 2
    ? ['HOME', 'AWAY']
    : ['HOME', 'DRAW', 'AWAY'];
  const values = publishedOdds
    .map((odd, index) => `(${index + 1},${sql(selectionKeys[index])},${sql(odd)})`)
    .join(',');
  return `INSERT INTO public.odds_publication_selection(publication_id,selection_no,selection_key,published_odds) SELECT ${sql(publicationId)},seeded.selection_no,seeded.selection_key,seeded.published_odds FROM (VALUES ${values}) AS seeded(selection_no,selection_key,published_odds) WHERE EXISTS (SELECT 1 FROM public.odds_publication WHERE publication_id=${sql(publicationId)}) ON CONFLICT (publication_id,selection_no) DO NOTHING;`;
}

function envFileValues(text) {
  const result = {};
  for (const line of text.split(/\r?\n/u)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const index = trimmed.indexOf('=');
    if (index > 0) result[trimmed.slice(0, index)] = trimmed.slice(index + 1);
  }
  return result;
}

async function loadEnv(env) {
  let file = {};
  try { file = envFileValues(await readFile(resolve(ROOT, 'infra', '.env.example'), 'utf8')); } catch { /* ambiente pode fornecer tudo */ }
  return { ...file, ...env };
}

function requireEnv(env, key) {
  const value = env[key];
  if (typeof value !== 'string' || value.length === 0) throw new Error(`${key} ausente; seeder local falha fechado.`);
  return value;
}

async function httpJson(fetchImpl, url, init = {}) {
  const response = await fetchImpl(url, { ...init, redirect: 'error', signal: AbortSignal.timeout(15_000) });
  if (!response.ok) throw new Error(`Keycloak recusou operação com HTTP ${response.status}.`);
  if (response.status === 204 || response.status === 201) return null;
  return response.json();
}

async function keycloakToken(fetchImpl, env) {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: 'admin-cli',
    username: requireEnv(env, 'KC_BOOTSTRAP_ADMIN_USERNAME'),
    password: requireEnv(env, 'KC_BOOTSTRAP_ADMIN_PASSWORD'),
  });
  const doc = await httpJson(fetchImpl, `${env.KEYCLOAK_BASE_URL ?? DEFAULT_KEYCLOAK}/realms/master/protocol/openid-connect/token`, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body,
  });
  if (!doc?.access_token) throw new Error('Keycloak não devolveu token administrativo.');
  return doc.access_token;
}

async function ensureKeycloakUser(fetchImpl, env, token, persona) {
  const base = env.KEYCLOAK_BASE_URL ?? DEFAULT_KEYCLOAK;
  const root = `${base}/admin/realms/${encodeURIComponent(env.KEYCLOAK_REALM ?? DEFAULT_REALM)}`;
  const headers = { authorization: `Bearer ${token}`, 'content-type': 'application/json' };
  const found = await httpJson(fetchImpl, `${root}/users?username=${encodeURIComponent(persona.username)}&exact=true`, { headers });
  const existing = Array.isArray(found) ? found.find((u) => u.username === persona.username) : null;
  if (existing?.id) return { ...persona, subject: existing.id };
  await httpJson(fetchImpl, `${root}/users`, {
    method: 'POST', headers,
    body: JSON.stringify({ username: persona.username, email: persona.email, enabled: true, emailVerified: true, requiredActions: [], realmRoles: [], credentials: [{ type: 'password', value: PASSWORD, temporary: false }] }),
  });
  const created = await httpJson(fetchImpl, `${root}/users?username=${encodeURIComponent(persona.username)}&exact=true`, { headers });
  const user = Array.isArray(created) ? created.find((u) => u.username === persona.username) : null;
  if (!user?.id) throw new Error(`Keycloak não confirmou a persona ${persona.key}.`);
  return { ...persona, subject: user.id };
}

function hmacHex(matricula, key) {
  const normalized = matricula.trim().toUpperCase().replace(/[\s./-]+/gu, '');
  if (!/^[A-Z0-9]{5,20}$/u.test(normalized)) throw new Error('matrícula de fixture inválida');
  return createHmac('sha256', Buffer.from(key, 'base64')).update(normalized, 'utf8').digest('hex');
}

function organizationForTeam(name) {
  const normalized = name.normalize('NFD').replace(/[\u0300-\u036f]/gu, '').toUpperCase();
  if (normalized.includes('ENGENHARIA')) return 'Engenharia';
  if (normalized.includes('MEDICINA')) return 'Medicina';
  if (normalized.includes('EXATAS') || normalized.includes('COMPUTACAO')) return 'Exatas';
  return 'Monetária';
}

function localDateParts(date) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: SEED_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  return Object.fromEntries(
    parts
      .filter((part) => ['year', 'month', 'day'].includes(part.type))
      .map((part) => [part.type, Number.parseInt(part.value, 10)]),
  );
}

function dateAfterDays(year, month, day, days) {
  const result = new Date(Date.UTC(year, month - 1, day + days));
  return result.toISOString().slice(0, 10);
}

function scheduledAtForMatch(match, index, { now = new Date(), preserveFixtureDates = false } = {}) {
  if (preserveFixtureDates) return match.data_hora_iso ?? `${match.data}T${match.horario}:00-03:00`;
  const { year, month, day } = localDateParts(now);
  const dayOffset = index < 2 ? 0 : Math.floor((index - 2) / 2) + 1;
  const hour = index % 2 === 0 ? 18 : 21;
  return `${dateAfterDays(year, month, day, dayOffset)}T${String(hour).padStart(2, '0')}:00:00${SEED_TIME_ZONE_OFFSET}`;
}

function closeDeadlineFor(scheduledAt) {
  const parsed = new Date(scheduledAt);
  if (Number.isNaN(parsed.getTime())) throw new Error(`scheduled_at inválido: ${scheduledAt}`);
  return new Date(parsed.getTime() - 30 * 60 * 1000).toISOString();
}

function marketSqlForMatch(match, index, { now, preserveFixtureDates }) {
  const lines = [];
  const when = scheduledAtForMatch(match, index, { now, preserveFixtureDates });
  const eventId = stableUuid(`event:${match.origem_id}`);
  const marketId = stableUuid(`market:${eventId}`);
  const initialVersionId = stableUuid(`market-version:${eventId}:initial`);
  const scheduledVersionId = stableUuid(`market-version:${eventId}:scheduled:${when}`);
  const scheduledCalculationId = stableUuid(`calculation:${eventId}:scheduled:${when}`);
  const scheduledPublicationId = stableUuid(`publication:${eventId}:scheduled:${when}`);
  const marketState = index === 2 ? 'SUSPENDED' : index === 3 ? 'LOCKED' : 'OPEN';
  const deadline = closeDeadlineFor(when);
  const publishedOdds = index === 1 ? [1.73, 2.12] : [1.90, 1.90];
  // Resolve pelo evento para preservar mercados e palpites criados antes da identidade estável.
  lines.push(`INSERT INTO public.odds_market(market_id,event_id,market_type,current_version_id,lock_version) VALUES (${sql(marketId)},${sql(eventId)},'HEAD_TO_HEAD',NULL,0) ON CONFLICT (event_id,market_type) DO NOTHING;`);
  lines.push(`INSERT INTO public.odds_market_version(market_version_id,market_id,version_no,state,close_deadline) SELECT ${sql(initialVersionId)},m.market_id,1,${sql(marketState)},${sql(deadline)} FROM public.odds_market m WHERE m.event_id=${sql(eventId)} AND m.market_type='HEAD_TO_HEAD' AND NOT EXISTS (SELECT 1 FROM public.odds_market_version existing WHERE existing.market_id=m.market_id) ON CONFLICT DO NOTHING;`);
  lines.push(`UPDATE public.odds_market m SET current_version_id=${sql(initialVersionId)} WHERE m.event_id=${sql(eventId)} AND m.market_type='HEAD_TO_HEAD' AND m.current_version_id IS NULL AND EXISTS (SELECT 1 FROM public.odds_market_version v WHERE v.market_id=m.market_id AND v.market_version_id=${sql(initialVersionId)});`);
  if (index === 1) {
    const historyCalculationId = stableUuid(`calculation:${eventId}:history:1`);
    const historyPublicationId = stableUuid(`publication:${eventId}:history:1`);
    const historyOdds = [1.90, 1.90];
    lines.push(`INSERT INTO public.odds_calculation(calculation_id,market_version_id,algorithm_version,configuration_digest,input_snapshot,fair_probability,confidence) SELECT ${sql(historyCalculationId)},v.market_version_id,'elo-v1','seed-local',${sqlJson({ confidenceBand: 'MEDIUM', sampleSize: 8, publishedOdds: historyOdds })},0.5,0.75 FROM public.odds_market m JOIN public.odds_market_version v ON v.market_id=m.market_id AND v.version_no=1 WHERE m.event_id=${sql(eventId)} AND m.market_type='HEAD_TO_HEAD' AND NOT EXISTS (SELECT 1 FROM public.odds_calculation existing_calculation JOIN public.odds_publication existing_publication ON existing_publication.calculation_id=existing_calculation.calculation_id WHERE existing_calculation.market_version_id=v.market_version_id) ON CONFLICT (calculation_id) DO NOTHING;`);
    lines.push(`INSERT INTO public.odds_publication(publication_id,calculation_id,margin,rounding_mode,published_odds,supersedes_publication_id,base_lock_version) SELECT ${sql(historyPublicationId)},${sql(historyCalculationId)},0.05,'HALF_UP',${sql(historyOdds[0])},NULL,0 WHERE EXISTS (SELECT 1 FROM public.odds_calculation WHERE calculation_id=${sql(historyCalculationId)}) ON CONFLICT (publication_id) DO NOTHING;`);
    lines.push(oddsSelectionSql(historyPublicationId, historyOdds));
  }
  lines.push(`INSERT INTO public.odds_market_version(market_version_id,market_id,version_no,state,close_deadline,supersedes_version_id) SELECT ${sql(scheduledVersionId)},m.market_id,COALESCE((SELECT max(version_no) + 1 FROM public.odds_market_version WHERE market_id=m.market_id),1),${sql(marketState)},${sql(deadline)},m.current_version_id FROM public.odds_market m WHERE m.event_id=${sql(eventId)} AND m.market_type='HEAD_TO_HEAD' AND NOT EXISTS (SELECT 1 FROM public.odds_market_version WHERE market_version_id=${sql(scheduledVersionId)}) ON CONFLICT DO NOTHING;`);
  lines.push(`UPDATE public.odds_market m SET current_version_id=${sql(scheduledVersionId)} WHERE m.event_id=${sql(eventId)} AND m.market_type='HEAD_TO_HEAD' AND EXISTS (SELECT 1 FROM public.odds_market_version v WHERE v.market_id=m.market_id AND v.market_version_id=${sql(scheduledVersionId)});`);
  lines.push(`INSERT INTO public.odds_calculation(calculation_id,market_version_id,algorithm_version,configuration_digest,input_snapshot,fair_probability,confidence) SELECT ${sql(scheduledCalculationId)},${sql(scheduledVersionId)},'elo-v1','seed-local',${sqlJson({ confidenceBand: index === 1 ? 'HIGH' : 'MEDIUM', sampleSize: index === 1 ? 12 : 8, publishedOdds })},${index === 1 ? 0.55 : 0.5},${index === 1 ? 0.82 : 0.75} WHERE EXISTS (SELECT 1 FROM public.odds_market_version WHERE market_version_id=${sql(scheduledVersionId)}) ON CONFLICT (calculation_id) DO NOTHING;`);
  lines.push(`INSERT INTO public.odds_publication(publication_id,calculation_id,margin,rounding_mode,published_odds,supersedes_publication_id,base_lock_version) SELECT ${sql(scheduledPublicationId)},${sql(scheduledCalculationId)},0.05,'HALF_UP',${sql(publishedOdds[0])},NULL,0 WHERE EXISTS (SELECT 1 FROM public.odds_calculation WHERE calculation_id=${sql(scheduledCalculationId)}) ON CONFLICT (publication_id) DO NOTHING;`);
  lines.push(oddsSelectionSql(scheduledPublicationId, publishedOdds));
  if (index === 2) lines.push(`INSERT INTO public.odds_override(override_id,market_id,calculation_id,proposer_id,reason,decision) SELECT ${sql(stableUuid('override:pending'))},m.market_id,${sql(scheduledCalculationId)},${sql(stableUuid('identity:maker'))},'Teste maker-checker local','PROPOSED' FROM public.odds_market m WHERE m.event_id=${sql(eventId)} AND m.market_type='HEAD_TO_HEAD' AND EXISTS (SELECT 1 FROM public.odds_calculation WHERE calculation_id=${sql(scheduledCalculationId)}) ON CONFLICT (override_id) DO NOTHING;`);
  lines.push(`INSERT INTO public.odds_lifecycle(lifecycle_fact_id,market_id,from_state,to_state,reason,actor_id) SELECT ${sql(stableUuid(`lifecycle:${scheduledVersionId}`))},m.market_id,NULL,${sql(marketState)},'Seed local',${sql(stableUuid('identity:admin'))} FROM public.odds_market m WHERE m.event_id=${sql(eventId)} AND m.market_type='HEAD_TO_HEAD' ON CONFLICT (lifecycle_fact_id) DO NOTHING;`);
  return lines;
}

function openMarketPostconditionSql() {
  return `DO $betufu$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM public.sports_event event
    JOIN public.sports_event_publication event_publication ON event_publication.event_id=event.event_id
    JOIN public.odds_market market ON market.event_id=event.event_id
    JOIN public.odds_market_version version ON version.market_version_id=market.current_version_id
    JOIN public.odds_calculation calculation ON calculation.market_version_id=version.market_version_id
    JOIN public.odds_publication publication ON publication.calculation_id=calculation.calculation_id
    WHERE event.status='PUBLICADO'
      AND version.state='OPEN'
      AND version.close_deadline > CURRENT_TIMESTAMP
      AND (SELECT count(*) FROM public.odds_publication_selection selection WHERE selection.publication_id=publication.publication_id) >= 2
  ) THEN
    RAISE EXCEPTION 'seed local sem mercado publico aberto e futuro';
  END IF;
END
$betufu$;`;
}

function buildSql({ personas, matches, env, now = new Date() }) {
  const competitionId = stableUuid('competition:laufu-2026-futsal-masculino');
  const editionId = stableUuid('edition:laufu-2026-futsal-masculino');
  const sportId = stableUuid('sport:futsal');
  const categoryId = stableUuid('category:laufu-2026-masculino');
  const phaseId = stableUuid('phase:laufu-2026-grupos');
  const hmacKey = requireEnv(env, 'BETUFU_MATRICULA_HMAC_KEY_V1');
  const studentHmac = hmacHex('202600001', hmacKey);
  const athleteHmac = hmacHex('202600002', hmacKey);
  const users = personas.map((p) => `INSERT INTO public.local_identity(identity_id,issuer,subject) VALUES (${sql(stableUuid(`identity:${p.key}`))},${sql(env.BETUFU_ISSUER ?? DEFAULT_ISSUER)},${sql(p.subject)}) ON CONFLICT (issuer,subject) DO NOTHING;`);
  const lines = ['BEGIN;', ...users];
  const preserveFixtureDates = ['1', 'true', 'yes'].includes(String(env.BETUFU_SEED_PRESERVE_FIXTURE_DATES ?? '').toLowerCase());
  lines.push(`INSERT INTO public.sports_sport(sport_id,name,slug) VALUES (${sql(sportId)},'Futsal','futsal') ON CONFLICT (slug) DO NOTHING;`);
  lines.push(`INSERT INTO public.sports_competition(competition_id,name,slug,status,sport_id) VALUES (${sql(competitionId)},'LAUFU 2026 - Futsal Masculino','laufu-2026-futsal-masculino','ATIVA',${sql(sportId)}) ON CONFLICT (slug) DO NOTHING;`);
  lines.push(`INSERT INTO public.sports_edition(edition_id,competition_id,name,slug,start_date,end_date,status) VALUES (${sql(editionId)},${sql(competitionId)},'2026','2026','2026-01-01','2026-12-31','ATIVA') ON CONFLICT (competition_id,slug) DO NOTHING;`);
  lines.push(`INSERT INTO public.sports_category(category_id,edition_id,name,slug,status) VALUES (${sql(categoryId)},${sql(editionId)},'Masculino','masculino','ATIVA') ON CONFLICT (edition_id,slug) DO NOTHING;`);
  lines.push(`INSERT INTO public.sports_phase(phase_id,edition_id,category_id,name,slug,status) VALUES (${sql(phaseId)},${sql(editionId)},${sql(categoryId)},'Fase de grupos','fase-de-grupos','ATIVA') ON CONFLICT (category_id,slug) DO NOTHING;`);
  const teamNames = [...new Set(['MONETÁRIA', 'ENGENHARIA', 'MEDICINA', 'COMPUTAÇÃO E EXATAS', ...matches.flatMap((m) => [m.time_mandante_nome, m.time_visitante_nome])])];
  for (const name of teamNames) {
    const org = organizationForTeam(name);
    const orgId = stableUuid(`organization:${org}`);
    const teamId = stableUuid(`team:${name}`);
    lines.push(`INSERT INTO public.sports_organization(organization_id,name,slug,status) VALUES (${sql(orgId)},${sql(org)},${sql(org.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/gu, '').replace(/[^a-z0-9]+/gu, '-'))},'PUBLICADO') ON CONFLICT (slug) DO NOTHING;`);
    lines.push(`INSERT INTO public.sports_team(team_id,organization_id,edition_id,sport_id,category_id,name,slug,status) VALUES (${sql(teamId)},${sql(orgId)},${sql(editionId)},${sql(sportId)},${sql(categoryId)},${sql(name)},${sql(name.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/gu, '').replace(/[^a-z0-9]+/gu, '-'))},'PUBLICADO') ON CONFLICT (edition_id,sport_id,category_id,slug) DO NOTHING;`);
  }
  const rosterTeamId = stableUuid('team:MONETÁRIA');
  const rosterId = stableUuid('roster:monetaria');
  const athleteId = stableUuid('athlete:atleta-ufu');
  const rosterPublicationId = stableUuid('roster-publication:monetaria:v1');
  lines.push(`INSERT INTO public.sports_athlete(athlete_id,name,slug,matricula_hmac,hmac_version,status) VALUES (${sql(athleteId)},'Atleta de Teste','atleta-de-teste',decode(${sql(athleteHmac)},'hex'),1,'PUBLICADO') ON CONFLICT (slug) DO NOTHING;`);
  lines.push(`INSERT INTO public.sports_roster(roster_id,team_id,status) VALUES (${sql(rosterId)},${sql(rosterTeamId)},'PUBLICADO') ON CONFLICT (team_id) DO NOTHING;`);
  lines.push(`INSERT INTO public.sports_roster_member(roster_id,athlete_id,jersey_number,position_name) VALUES (${sql(rosterId)},${sql(athleteId)},10,'Ala') ON CONFLICT (roster_id,athlete_id) DO NOTHING;`);
  lines.push(`INSERT INTO public.sports_roster_publication(publication_id,roster_id,team_id,competition_id,edition_id,organization_id,sport_id,category_id,version_no,team_name,organization_name,edition_name,competition_name,sport_name,category_name,published_by_identity_id,base_lock_version) VALUES (${sql(rosterPublicationId)},${sql(rosterId)},${sql(rosterTeamId)},${sql(competitionId)},${sql(editionId)},${sql(stableUuid('organization:Monetária'))},${sql(sportId)},${sql(categoryId)},1,'MONETÁRIA','Monetária','2026','LAUFU 2026 - Futsal Masculino','Futsal','Masculino',${sql(stableUuid('identity:admin'))},0) ON CONFLICT (roster_id,version_no) DO NOTHING;`);
  lines.push(`INSERT INTO public.sports_roster_publication_member(publication_id,athlete_id,athlete_name,athlete_slug,jersey_number,position_name) VALUES (${sql(rosterPublicationId)},${sql(athleteId)},'Atleta de Teste','atleta-de-teste',10,'Ala') ON CONFLICT (publication_id,athlete_id) DO NOTHING;`);
  for (const [index, match] of matches.entries()) {
    const eventId = stableUuid(`event:${match.origem_id}`);
    const homeId = stableUuid(`team:${match.time_mandante_nome}`);
    const awayId = stableUuid(`team:${match.time_visitante_nome}`);
    const when = scheduledAtForMatch(match, index, { now, preserveFixtureDates });
    lines.push(`INSERT INTO public.sports_event(event_id,competition_id,edition_id,title,event_format,status,scheduled_at,zone_id,venue_state,source_reference,sport_id,category_id,phase_id) VALUES (${sql(eventId)},${sql(competitionId)},${sql(editionId)},${sql(`${match.time_mandante_nome} x ${match.time_visitante_nome}`)},'HEAD_TO_HEAD','PUBLICADO',${sql(when)},'America/Sao_Paulo','LOCAL_A_DEFINIR',${sql(match.origem_url)},${sql(sportId)},${sql(categoryId)},${sql(phaseId)}) ON CONFLICT (event_id) DO UPDATE SET scheduled_at=EXCLUDED.scheduled_at;`);
    lines.push(`INSERT INTO public.sports_event_slot(event_id,slot_no,display_label,team_id) VALUES (${sql(eventId)},1,${sql(match.time_mandante_nome)},${sql(homeId)}),(${sql(eventId)},2,${sql(match.time_visitante_nome)},${sql(awayId)}) ON CONFLICT (event_id,slot_no) DO NOTHING;`);
    const pubId = stableUuid(`event-publication:${match.origem_id}:${when}`);
    lines.push(`INSERT INTO public.sports_event_publication(publication_id,event_id,competition_id,edition_id,version_no,competition_name,edition_name,title,event_format,scheduled_at,zone_id,venue_state,source_reference,published_by_identity_id,base_lock_version,sport_id,sport_name,category_id,category_name,phase_id,phase_name) SELECT ${sql(pubId)},${sql(eventId)},${sql(competitionId)},${sql(editionId)},COALESCE((SELECT max(version_no) + 1 FROM public.sports_event_publication WHERE event_id=${sql(eventId)}),1),'LAUFU 2026 - Futsal Masculino','2026',${sql(`${match.time_mandante_nome} x ${match.time_visitante_nome}`)},'HEAD_TO_HEAD',${sql(when)},'America/Sao_Paulo','LOCAL_A_DEFINIR',${sql(match.origem_url)},${sql(stableUuid('identity:admin'))},0,${sql(sportId)},'Futsal',${sql(categoryId)},'Masculino',${sql(phaseId)},'Fase de grupos' WHERE NOT EXISTS (SELECT 1 FROM public.sports_event_publication WHERE publication_id=${sql(pubId)}) ON CONFLICT DO NOTHING;`);
    lines.push(`INSERT INTO public.sports_event_publication_slot(publication_id,slot_no,display_label,team_id,team_name) VALUES (${sql(pubId)},1,${sql(match.time_mandante_nome)},${sql(homeId)},${sql(match.time_mandante_nome)}),(${sql(pubId)},2,${sql(match.time_visitante_nome)},${sql(awayId)},${sql(match.time_visitante_nome)}) ON CONFLICT (publication_id,slot_no) DO NOTHING;`);
  }
  for (const [index, match] of matches.entries()) {
    lines.push(...marketSqlForMatch(match, index, { now, preserveFixtureDates }));
  }
  if (!preserveFixtureDates) lines.push(openMarketPostconditionSql());
  const student = stableUuid('identity:student');
  const athlete = stableUuid('identity:athlete');
  lines.push(`INSERT INTO public.betufu_profile(profile_id,identity_id,nickname) VALUES (${sql(stableUuid('profile:student'))},${sql(student)},'aluno-ufu') ON CONFLICT (identity_id) DO NOTHING;`);
  lines.push(`INSERT INTO public.betufu_profile(profile_id,identity_id,nickname) VALUES (${sql(stableUuid('profile:athlete'))},${sql(athlete)},'atleta-ufu') ON CONFLICT (identity_id) DO NOTHING;`);
  lines.push(`INSERT INTO public.ufu_enrollment(enrollment_id,identity_id,matricula_hmac,hmac_version,status) VALUES (${sql(stableUuid('enrollment:student'))},${sql(student)},decode(${sql(studentHmac)},'hex'),1,'ATIVO') ON CONFLICT DO NOTHING;`);
  lines.push(`INSERT INTO public.ufu_enrollment(enrollment_id,identity_id,matricula_hmac,hmac_version,status) VALUES (${sql(stableUuid('enrollment:athlete'))},${sql(athlete)},decode(${sql(athleteHmac)},'hex'),1,'ATIVO') ON CONFLICT DO NOTHING;`);
  lines.push(`INSERT INTO public.credit_account (identity_id, balance)
SELECT DISTINCT enrollment.identity_id, 0.00
FROM public.ufu_enrollment enrollment
WHERE enrollment.status = 'ATIVO'
ON CONFLICT (identity_id) DO NOTHING;`);
  lines.push(`WITH inserted_grants AS (
    INSERT INTO public.credit_ledger_entry (
        identity_id, entry_type, direction, amount, source_type, source_id, idempotency_key
    )
    SELECT DISTINCT
        enrollment.identity_id,
        'GRANT_INITIAL',
        'CREDIT',
        1000.00,
        'SYSTEM',
        enrollment.identity_id,
        'credit:initial-grant:' || enrollment.identity_id::text
    FROM public.ufu_enrollment enrollment
    WHERE enrollment.status = 'ATIVO'
    ON CONFLICT DO NOTHING
    RETURNING identity_id
)
UPDATE public.credit_account account
SET balance = (account.balance + 1000.00)::numeric(20,2),
    updated_at = CURRENT_TIMESTAMP(6)
FROM inserted_grants grant_entry
WHERE account.identity_id = grant_entry.identity_id;`);
  lines.push(`INSERT INTO public.sports_athlete_link_proposal(proposal_id,athlete_id,identity_id,status,decided_by_identity_id,decided_at) VALUES (${sql(stableUuid('athlete-link:accepted'))},${sql(athleteId)},${sql(athlete)},'ACEITA',${sql(stableUuid('identity:admin'))},CURRENT_TIMESTAMP(6)) ON CONFLICT (proposal_id) DO NOTHING;`);
  lines.push(`INSERT INTO public.operational_role_grant(grant_id,identity_id,role_name,scope_ref,status) VALUES (${sql(stableUuid('grant:admin'))},${sql(stableUuid('identity:admin'))},'ADMINISTRADOR_GERAL',NULL,'VIGENTE') ON CONFLICT DO NOTHING;`);
  lines.push(`INSERT INTO public.operational_role_grant(grant_id,identity_id,role_name,scope_ref,status) VALUES (${sql(stableUuid('grant:maker'))},${sql(stableUuid('identity:maker'))},'ADMINISTRADOR_COMPETICAO',${sql(competitionId)},'VIGENTE') ON CONFLICT DO NOTHING;`);
  lines.push(`INSERT INTO public.operational_role_grant(grant_id,identity_id,role_name,scope_ref,status) VALUES (${sql(stableUuid('grant:checker'))},${sql(stableUuid('identity:checker'))},'ADMINISTRADOR_COMPETICAO',${sql(competitionId)},'VIGENTE') ON CONFLICT DO NOTHING;`);
  lines.push(`INSERT INTO public.operational_role_grant(grant_id,identity_id,role_name,scope_ref,status) VALUES (${sql(stableUuid('grant:moderator'))},${sql(stableUuid('identity:moderator'))},'MODERADOR',${sql(competitionId)},'VIGENTE') ON CONFLICT DO NOTHING;`);
  lines.push('COMMIT;');
  return lines.join('\n');
}

function sanitizeSqlError(text, env = {}) {
  if (typeof text !== 'string' || text.length === 0) return '';
  let result = text
    .split(/\r?\n/u)
    .filter((line) => !line.trim().startsWith('DETAIL:'))
    .join('\n')
    .replace(/\b[0-9a-fA-F]{64}\b/gu, '[REDACTED_HASH]');

  for (const [key, value] of Object.entries(env)) {
    if (typeof value === 'string' && value.length >= 6) {
      const escaped = value.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
      result = result.replace(new RegExp(escaped, 'gu'), `[REDACTED:${key}]`);
    }
  }
  return result.trim();
}

function run(command, args, input = '') {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, { cwd: ROOT, shell: false, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    const stdout = []; const stderr = [];
    child.stdout.on('data', (chunk) => stdout.push(chunk));
    child.stderr.on('data', (chunk) => stderr.push(chunk));
    child.once('error', rejectPromise);
    child.once('close', (code) => {
      if (code === 0) {
        resolvePromise(Buffer.concat(stdout).toString('utf8'));
      } else {
        const rawStderr = Buffer.concat(stderr).toString('utf8').trim();
        const sanitized = sanitizeSqlError(rawStderr, process.env);
        const detail = sanitized.length > 0 ? `: ${sanitized}` : '';
        rejectPromise(new Error(`docker exec falhou com exit code ${code}${detail}`));
      }
    });
    child.stdin.end(input);
  });
}

export async function seedLocal({ profile = 'daily', env: providedEnv = process.env, fetchImpl = globalThis.fetch, runCommand = run, fixture = null, log = () => {} } = {}) {
  if (!['daily', 'full'].includes(profile)) throw new Error('profile deve ser daily ou full');
  if (typeof fetchImpl !== 'function') throw new Error('fetch indisponível; seeder falha fechado.');
  const env = await loadEnv(providedEnv);
  const data = fixture ?? JSON.parse(await readFile(FIXTURE_PATH, 'utf8'));
  const source = data.partidas.filter((m) => m.campeonato_id === '9774');
  const matches = profile === 'full' ? data.partidas : source.slice(0, DAILY_MATCHES);
  if (matches.length === 0) throw new Error('fixture LAUFU não contém partidas utilizáveis.');
  const token = await keycloakToken(fetchImpl, env);
  const personas = [];
  for (const persona of PERSONAS) personas.push(await ensureKeycloakUser(fetchImpl, env, token, persona));
  const sqlText = buildSql({ personas, matches, env });
  const compose = ['compose', '--env-file', resolve(ROOT, 'infra', '.env.example'), '-f', resolve(ROOT, 'infra', 'compose.yaml'), '--project-name', 'infra', 'ps', '-q', 'postgres'];
  const containerResult = await runCommand('docker', compose);
  const containerId = String(containerResult).trim().split(/\r?\n/u)[0];
  if (!/^[a-f0-9]+$/iu.test(containerId)) throw new Error('container postgres não está disponível; seeder falha fechado.');
  try {
    await runCommand('docker', [ 'exec', '-i', containerId, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', env.POSTGRES_USER ?? 'postgres', '-d', env.BETUFU_DB_NAME ?? 'betufu_local', '-f', '-' ], sqlText);
  } catch (error) {
    if (error instanceof Error) {
      error.message = sanitizeSqlError(error.message, env);
    }
    throw error;
  }
  log(`seed ${profile} concluído: ${matches.length} partidas e ${personas.length} personas.`);
  return { profile, matches: matches.length, personas: personas.length };
}

async function main() {
  const [command, profile = 'daily'] = process.argv.slice(2);
  if (command !== 'seed') {
    console.error('Uso: node scripts/seed-local.mjs seed <daily|full>');
    process.exitCode = command ? 1 : 0;
    return;
  }
  try { await seedLocal({ profile, log: console.log }); } catch (error) { console.error(`[seed-local] ERRO: ${error instanceof Error ? error.message : 'falha desconhecida'}`); process.exitCode = 1; }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

export { buildSql, closeDeadlineFor, sanitizeSqlError, scheduledAtForMatch, sql, stableUuid };
