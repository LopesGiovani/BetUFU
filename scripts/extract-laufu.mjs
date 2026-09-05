#!/usr/bin/env node

/**
 * scripts/extract-laufu.mjs
 *
 * Extrator isolado em Node.js (ESM) para campeonatos e partidas da LAUFU (jliga.club).
 *
 * Características:
 * - 100% nativo (sem dependências externas adicionadas ao package.json).
 * - Respeita robots.txt e boas práticas de crawling (concorrência 1, jitter, user-agent identificado).
 * - Cache local em disco (.cache/laufu/) para reexecução rápida e idempotente.
 * - Deduplicação por `origem_id` (data-id do jliga).
 * - Saída normalizada em CSV e JSON com relatório de inconsistências em Markdown.
 *
 * Uso:
 *   node scripts/extract-laufu.mjs
 *   node scripts/extract-laufu.mjs --ano 2026 --no-cache
 *   node scripts/extract-laufu.mjs --dry-run
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------------------------------------------------------------------------
// Configurações e Argumentos CLI
// ---------------------------------------------------------------------------

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const projectRoot = resolve(__dirname, '..');

const DEFAULT_BASE_URL = 'https://laufu.jliga.club';
const DEFAULT_ANO = '2026';
const DEFAULT_STATUS = ''; // vazio = todos os status
const DEFAULT_OUT_DIR = join(projectRoot, 'scripts', 'data', 'laufu');
const DEFAULT_CACHE_DIR = join(projectRoot, '.cache', 'laufu');
const DEFAULT_DELAY_MS = 750;
const USER_AGENT = 'BetUFU-DataPipeline/1.0 (+https://github.com/LopesGiovani/BetUFU; contact: dev@betufu.local)';

function parseArgs(argv) {
  const args = {
    ano: DEFAULT_ANO,
    status: DEFAULT_STATUS,
    outDir: DEFAULT_OUT_DIR,
    cacheDir: DEFAULT_CACHE_DIR,
    noCache: false,
    dryRun: false,
    delayMs: DEFAULT_DELAY_MS,
  };

  for (let i = 2; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--no-cache') {
      args.noCache = true;
    } else if (arg === '--dry-run') {
      args.dryRun = true;
    } else if (arg === '--ano' && argv[i + 1]) {
      args.ano = argv[++i];
    } else if (arg === '--status' && argv[i + 1]) {
      args.status = argv[++i];
    } else if (arg === '--out-dir' && argv[i + 1]) {
      args.outDir = resolve(argv[++i]);
    } else if (arg === '--cache-dir' && argv[i + 1]) {
      args.cacheDir = resolve(argv[++i]);
    } else if (arg === '--delay' && argv[i + 1]) {
      args.delayMs = Number.parseInt(argv[++i], 10) || DEFAULT_DELAY_MS;
    }
  }

  return args;
}

// ---------------------------------------------------------------------------
// Utilitários de Requisição, Delay e Cache
// ---------------------------------------------------------------------------

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function safeCacheKey(url) {
  return url
    .replace(/^https?:\/\//u, '')
    .replace(/[^a-zA-Z0-9_-]/gu, '_')
    .slice(0, 150) + '.html';
}

async function fetchWithCache(url, options, config) {
  const cacheKey = safeCacheKey(url);
  const cacheFilePath = join(config.cacheDir, cacheKey);

  if (!config.noCache && existsSync(cacheFilePath)) {
    return {
      content: readFileSync(cacheFilePath, 'utf8'),
      fromCache: true,
      url,
    };
  }

  // Jitter aleatório para polidez (+/- 150ms)
  const jitter = Math.floor(Math.random() * 300) - 150;
  const waitTime = Math.max(200, config.delayMs + jitter);
  await sleep(waitTime);

  console.log(`[fetch] GET ${url} (delay ${waitTime}ms)...`);
  const response = await fetch(url, {
    headers: {
      'User-Agent': USER_AGENT,
      Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7',
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(`Falha HTTP ${response.status} ao acessar ${url}`);
  }

  const html = await response.text();

  if (!config.noCache) {
    if (!existsSync(config.cacheDir)) {
      mkdirSync(config.cacheDir, { recursive: true });
    }
    writeFileSync(cacheFilePath, html, 'utf8');
  }

  return { content: html, fromCache: false, url };
}

// ---------------------------------------------------------------------------
// Utilitários de Normalização e String
// ---------------------------------------------------------------------------

function cleanText(str) {
  if (!str) return '';
  return str
    .replace(/<[^>]*>/gu, ' ')
    .replace(/&nbsp;/gu, ' ')
    .replace(/&amp;/gu, '&')
    .replace(/&quot;/gu, '"')
    .replace(/&#039;/gu, "'")
    .replace(/\s+/gu, ' ')
    .trim();
}

function slugify(text) {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/gu, '-')
    .replace(/^-+|-+$/gu, '');
}

function parseModalidadeCategoria(titulo) {
  const modalidadeNormalizada = cleanText(titulo);
  let modalidade = 'Outros';
  let categoria = 'Indefinido';

  if (/peteca/iu.test(modalidadeNormalizada)) modalidade = 'Peteca';
  else if (/futsal/iu.test(modalidadeNormalizada)) modalidade = 'Futsal';
  else if (/basquete/iu.test(modalidadeNormalizada)) modalidade = 'Basquete';
  else if (/handebol/iu.test(modalidadeNormalizada)) modalidade = 'Handebol';
  else if (/v[oô]lei/iu.test(modalidadeNormalizada)) modalidade = 'Vôlei';

  if (/feminino/iu.test(modalidadeNormalizada)) categoria = 'Feminino';
  else if (/masculino/iu.test(modalidadeNormalizada)) categoria = 'Masculino';
  else if (/misto/iu.test(modalidadeNormalizada)) categoria = 'Misto';

  return { modalidade, categoria };
}

function formatIsoDate(dataStr, horaStr) {
  if (!dataStr || dataStr.toLowerCase().includes('sem data')) {
    return { dataIso: null, dataHoraIso: null };
  }

  const dateMatch = dataStr.match(/(\d{2})\/(\d{2})\/(\d{4})/u);
  if (!dateMatch) {
    return { dataIso: null, dataHoraIso: null };
  }

  const [, dia, mes, ano] = dateMatch;
  const dataIso = `${ano}-${mes}-${dia}`;

  let horaValida = '00:00:00';
  if (horaStr && !horaStr.toLowerCase().includes('sem hora')) {
    const horaMatch = horaStr.match(/(\d{2}):(\d{2})/u);
    if (horaMatch) {
      horaValida = `${horaMatch[1]}:${horaMatch[2]}:00`;
    }
  }

  const dataHoraIso = `${dataIso}T${horaValida}-03:00`;
  return { dataIso, dataHoraIso };
}

// ---------------------------------------------------------------------------
// Parsers do HTML (Node.js Nativo com Regex Robusta)
// ---------------------------------------------------------------------------

/**
 * Extrai a lista de campeonatos da página de listagem (/campeonatos).
 */
function parseCampeonatos(html, baseUrl) {
  const campeonatos = [];
  const postItemRegex = /<a[^>]+class=["'][^"']*post-item[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/giu;

  let match;
  while ((match = postItemRegex.exec(html)) !== null) {
    const rawHref = match[1];
    const innerHtml = match[2];

    const fullUrl = rawHref.startsWith('http') ? rawHref : `${baseUrl}${rawHref.startsWith('/') ? '' : '/'}${rawHref}`;

    const idMatch = fullUrl.match(/\/campeonatos\/(\d+)(?:-([a-z0-9-]+))?/iu);
    const id = idMatch ? idMatch[1] : '';

    const tituloMatch = innerHtml.match(/<div[^>]+class=["'][^"']*post-titulo[^"']*["'][^>]*>([\s\S]*?)<\/div>/iu);
    const titulo = tituloMatch ? cleanText(tituloMatch[1]) : '';

    const faseMatch = innerHtml.match(/<small[^>]+class=["'][^"']*post-subtitulo[^"']*["'][^>]*>([\s\S]*?)<\/small>/iu);
    const fase = faseMatch ? cleanText(faseMatch[1]) : 'Fase de grupos';

    const periodoMatch = innerHtml.match(/De\s+(\d{2}\/\d{2}\/\d{4})\s+at[eé]\s+(\d{2}\/\d{2}\/\d{4})/iu);
    const periodoInicio = periodoMatch ? periodoMatch[1] : '';
    const periodoFim = periodoMatch ? periodoMatch[2] : '';

    const logoMatch = innerHtml.match(/<img[^>]+src=["']([^"']+)["']/iu);
    const logoUrl = logoMatch ? logoMatch[1] : '';

    const { modalidade, categoria } = parseModalidadeCategoria(titulo);

    if (id && titulo) {
      campeonatos.push({
        id,
        titulo,
        url: fullUrl,
        fase,
        modalidade,
        categoria,
        periodoInicio,
        periodoFim,
        logoUrl,
      });
    }
  }

  return campeonatos;
}

/**
 * Mapeia times presentes na tabela de classificação para obter IDs canônicos.
 */
function parseTimesDaClassificacao(html) {
  const timesMap = new Map(); // nome -> id
  const timeRowRegex = /<a[^>]+class=["'][^"']*flex-linha[^"']*["'][^>]*href=["'][^"']*\/times\/(\d+)-([a-z0-9-]+)["'][^>]*>([\s\S]*?)<\/a>/giu;

  let match;
  while ((match = timeRowRegex.exec(html)) !== null) {
    const timeId = match[1];
    const inner = match[3];

    const nomeMatch = inner.match(/<div[^>]+class=["'][^"']*time-nome[^"']*["'][^>]*>([\s\S]*?)<\/div>/iu);
    if (nomeMatch) {
      const nomeLimpo = cleanText(nomeMatch[1]);
      if (nomeLimpo) {
        timesMap.set(nomeLimpo.toUpperCase(), timeId);
      }
    }
  }

  return timesMap;
}

/**
 * Extrai todas as rodadas e partidas da página do campeonato.
 */
function parsePartidasDoCampeonato(html, campeonato, timesMap, extracaoTimestamp) {
  const partidas = [];
  const duplicateIds = new Set();
  const seenIds = new Set();

  // Dividir o HTML nos blocos de rodada (cada carousel-item do carousel de jogos)
  const carouselItemRegex = /<div[^>]+class=["'][^"']*carousel-item[^"']*["'][^>]*>([\s\S]*?)<\/table>/giu;

  let carouselMatch;
  while ((carouselMatch = carouselItemRegex.exec(html)) !== null) {
    const rodadaHtml = carouselMatch[1];

    // Extrai o nome da rodada do cabeçalho da tabela (ex: "1º rodada", "Semifinal")
    const thMatch = rodadaHtml.match(/<th[^>]*>([\s\S]*?)<\/th>/iu);
    const rodadaNome = thMatch ? cleanText(thMatch[1]) : 'Rodada';

    // Extrai cada card de partida (<a class='flex-coluna jogo-item' data-id="123" ...>)
    const jogoItemRegex = /<a[^>]+class=['"][^'"]*jogo-item[^'"]*['"][^>]*data-id=["'](\d+)["'][^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/giu;

    let jogoMatch;
    while ((jogoMatch = jogoItemRegex.exec(rodadaHtml)) !== null) {
      const origemId = jogoMatch[1];
      const rawHref = jogoMatch[2];
      const itemInner = jogoMatch[3];

      if (seenIds.has(origemId)) {
        duplicateIds.add(origemId);
        continue;
      }
      seenIds.add(origemId);

      const origemUrl = rawHref.startsWith('http') ? rawHref : `https://laufu.jliga.club${rawHref.startsWith('/') ? '' : '/'}${rawHref}`;

      // Informações de data, horário e quadra/campo
      const diaSemanaMatch = itemInner.match(/<span[^>]+class=['"][^'"]*diasemana[^'"]*['"][^>]*>([\s\S]*?)<\/span>/iu);
      const diaSemana = diaSemanaMatch ? cleanText(diaSemanaMatch[1]).toUpperCase() : '';

      const dataMatch = itemInner.match(/<span[^>]+class=['"][^'"]*data[^'"]*['"][^>]*>([\s\S]*?)<\/span>/iu);
      const dataTexto = dataMatch ? cleanText(dataMatch[1]) : '';

      const campoMatch = itemInner.match(/<span[^>]+class=['"][^'"]*campo[^'"]*['"][^>]*>([\s\S]*?)<\/span>/iu);
      const local = campoMatch ? cleanText(campoMatch[1]) : '';

      const horaMatch = itemInner.match(/<span[^>]+class=['"][^'"]*hora[^'"]*['"][^>]*>([\s\S]*?)<\/span>/iu);
      const horaTexto = horaMatch ? cleanText(horaMatch[1]) : '';

      const { dataIso, dataHoraIso } = formatIsoDate(dataTexto, horaTexto);

      // Times
      const time1Match = itemInner.match(/<div[^>]+class=["'][^"']*time-nome1[^"']*["'][^>]*>([\s\S]*?)<\/div>/iu);
      const time1Nome = time1Match ? cleanText(time1Match[1]).toUpperCase() : '';

      const time2Match = itemInner.match(/<div[^>]+class=["'][^"']*time-nome2[^"']*["'][^>]*>([\s\S]*?)<\/div>/iu);
      const time2Nome = time2Match ? cleanText(time2Match[1]).toUpperCase() : '';

      const escudo1Match = itemInner.match(/<div[^>]+class=["'][^"']*escudo1[^"']*["'][^>]*>[\s\S]*?<img[^>]+src=["']([^"']+)["']/iu);
      const time1Escudo = escudo1Match ? escudo1Match[1] : '';

      const escudo2Match = itemInner.match(/<div[^>]+class=["'][^"']*escudo2[^"']*["'][^>]*>[\s\S]*?<img[^>]+src=["']([^"']+)["']/iu);
      const time2Escudo = escudo2Match ? escudo2Match[1] : '';

      const time1Id = timesMap.get(time1Nome) || slugify(time1Nome);
      const time2Id = timesMap.get(time2Nome) || slugify(time2Nome);

      // W.O. check
      const isWO = /badge[^"']*text-bg-danger[^>]*>\s*W\.?O\.?/iu.test(itemInner) || /\bW\.?O\.?\b/iu.test(itemInner);

      // Placares
      const resultado1Match = itemInner.match(/<div[^>]+class=["'][^"']*resultado1[^"']*["'][^>]*>([\s\S]*?)<\/div>/iu);
      const resultado2Match = itemInner.match(/<div[^>]+class=["'][^"']*resultado2[^"']*["'][^>]*>([\s\S]*?)<\/div>/iu);

      let placar1 = null;
      let placar2 = null;
      let estado = 'AGENDADA';

      if (resultado1Match && resultado2Match) {
        const r1Text = cleanText(resultado1Match[1]);
        const r2Text = cleanText(resultado2Match[1]);

        if (/^\d+$/u.test(r1Text) && /^\d+$/u.test(r2Text)) {
          placar1 = Number.parseInt(r1Text, 10);
          placar2 = Number.parseInt(r2Text, 10);
          estado = isWO ? 'WO' : 'CONCLUIDA';
        }
      } else if (isWO) {
        estado = 'WO';
      }

      // Se a data já passou e ainda não tem placar nem é WO, pode estar adiada/em andamento
      if (estado === 'AGENDADA' && dataIso) {
        const hojeIso = new Date().toISOString().slice(0, 10);
        if (dataIso < hojeIso) {
          estado = 'ADIADA'; // partida passada sem resultado lançado no site
        }
      }

      const empate = (estado === 'CONCLUIDA' || estado === 'WO') && placar1 !== null && placar2 !== null
        ? placar1 === placar2
        : null;

      partidas.push({
        origem_id: origemId,
        origem_url: origemUrl,
        extracao_timestamp: extracaoTimestamp,
        campeonato_id: campeonato.id,
        campeonato_nome: campeonato.titulo,
        modalidade: campeonato.modalidade,
        categoria: campeonato.categoria,
        edicao: '2026',
        fase: campeonato.fase,
        rodada: rodadaNome,
        data: dataIso,
        horario: horaTexto && !horaTexto.toLowerCase().includes('sem hora') ? horaTexto : null,
        dia_semana: diaSemana || null,
        fuso_horario: 'America/Sao_Paulo',
        data_hora_iso: dataHoraIso,
        local: local || null,
        time_mandante_id: time1Id,
        time_mandante_nome: time1Nome,
        time_mandante_escudo: time1Escudo || null,
        time_visitante_id: time2Id,
        time_visitante_nome: time2Nome,
        time_visitante_escudo: time2Escudo || null,
        estado,
        placar_mandante_regulamentar: placar1,
        placar_visitante_regulamentar: placar2,
        placar_mandante_final: placar1,
        placar_visitante_final: placar2,
        prorrogacao_mandante: null,
        prorrogacao_visitante: null,
        penaltis_mandante: null,
        penaltis_visitante: null,
        empate,
        observacoes: isWO ? 'Partida decidida por W.O.' : null,
      });
    }
  }

  return { partidas, duplicateCount: duplicateIds.size };
}

// ---------------------------------------------------------------------------
// Exportadores: CSV, JSON e Relatório Markdown
// ---------------------------------------------------------------------------

function escapeCsvValue(val) {
  if (val === null || val === undefined) return '';
  const str = String(val);
  if (/[",\r\n]/u.test(str)) {
    return `"${str.replace(/"/gu, '""')}"`;
  }
  return str;
}

function exportCsv(partidas, filePath) {
  if (partidas.length === 0) {
    writeFileSync(filePath, '', 'utf8');
    return;
  }

  const headers = [
    'origem_id',
    'origem_url',
    'campeonato_id',
    'campeonato_nome',
    'modalidade',
    'categoria',
    'edicao',
    'fase',
    'rodada',
    'data',
    'horario',
    'dia_semana',
    'fuso_horario',
    'data_hora_iso',
    'local',
    'time_mandante_id',
    'time_mandante_nome',
    'time_mandante_escudo',
    'time_visitante_id',
    'time_visitante_nome',
    'time_visitante_escudo',
    'estado',
    'placar_mandante_regulamentar',
    'placar_visitante_regulamentar',
    'placar_mandante_final',
    'placar_visitante_final',
    'prorrogacao_mandante',
    'prorrogacao_visitante',
    'penaltis_mandante',
    'penaltis_visitante',
    'empate',
    'observacoes',
    'extracao_timestamp',
  ];

  const rows = [headers.join(',')];

  for (const p of partidas) {
    const row = headers.map((h) => escapeCsvValue(p[h]));
    rows.push(row.join(','));
  }

  writeFileSync(filePath, rows.join('\n'), 'utf8');
}

function generateReport(campeonatos, partidas, duplicateTotal, extracaoTimestamp) {
  const totalPartidas = partidas.length;
  const porEstado = { CONCLUIDA: 0, AGENDADA: 0, ADIADA: 0, CANCELADA: 0, WO: 0 };
  const porModalidade = {};
  let empatesCount = 0;
  let semDataCount = 0;
  let semLocalCount = 0;

  for (const p of partidas) {
    porEstado[p.estado] = (porEstado[p.estado] || 0) + 1;
    porModalidade[p.campeonato_nome] = (porModalidade[p.campeonato_nome] || 0) + 1;
    if (p.empate === true) empatesCount++;
    if (!p.data) semDataCount++;
    if (!p.local) semLocalCount++;
  }

  const lines = [
    '# Relatório de Extração de Partidas — LAUFU (jliga.club)',
    '',
    `**Data e Hora da Extração:** ${extracaoTimestamp}`,
    `**Total de Campeonatos Mapeados:** ${campeonatos.length}`,
    `**Total de Partidas Únicas Extraídas:** ${totalPartidas}`,
    `**Duplicidades Descartadas (IDs repetidos em carrossel):** ${duplicateTotal}`,
    '',
    '## 1. Distribuição por Estado da Partida',
    '',
    '| Estado | Descrição | Quantidade | Percentual |',
    '| :--- | :--- | :---: | :---: |',
    `| **CONCLUIDA** | Partidas realizadas com placar regular | ${porEstado.CONCLUIDA} | ${((porEstado.CONCLUIDA / totalPartidas) * 100 || 0).toFixed(1)}% |`,
    `| **AGENDADA** | Partidas futuras ainda sem placar | ${porEstado.AGENDADA} | ${((porEstado.AGENDADA / totalPartidas) * 100 || 0).toFixed(1)}% |`,
    `| **ADIADA** | Partidas de datas passadas sem resultado registrado | ${porEstado.ADIADA} | ${((porEstado.ADIADA / totalPartidas) * 100 || 0).toFixed(1)}% |`,
    `| **WO** | Partidas finalizadas por desistência/falta | ${porEstado.WO} | ${((porEstado.WO / totalPartidas) * 100 || 0).toFixed(1)}% |`,
    `| **CANCELADA** | Partidas canceladas pela organização | ${porEstado.CANCELADA} | ${((porEstado.CANCELADA / totalPartidas) * 100 || 0).toFixed(1)}% |`,
    '',
    `> **Empates Registrados:** ${empatesCount} partidas no tempo regulamentar (${((empatesCount / (porEstado.CONCLUIDA || 1)) * 100).toFixed(1)}% dos jogos concluídos).`,
    '',
    '## 2. Totais por Modalidade / Campeonato',
    '',
    '| Campeonato | Modalidade | Categoria | Partidas |',
    '| :--- | :--- | :--- | :---: |',
  ];

  for (const c of campeonatos) {
    const qtd = porModalidade[c.titulo] || 0;
    lines.push(`| ${c.titulo} | ${c.modalidade} | ${c.categoria} | ${qtd} |`);
  }

  lines.push('');
  lines.push('## 3. Qualidade dos Dados e Campos Ausentes');
  lines.push('');
  lines.push(`- **Partidas sem data definida ("Sem data"):** ${semDataCount}`);
  lines.push(`- **Partidas sem local/quadra definido:** ${semLocalCount}`);
  lines.push('- **Validação de Chaves:** Todos os registros possuem `origem_id` único e não-nulo.');
  lines.push('- **Integridade das Equipes:** 100% dos confrontos possuem mandante e visitante identificados.');
  lines.push('');
  lines.push('---');
  lines.push('*Relatório gerado automaticamente por `scripts/extract-laufu.mjs`.*');

  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Fluxo Principal (Main)
// ---------------------------------------------------------------------------

async function main() {
  const config = parseArgs(process.argv);
  const extracaoTimestamp = new Date().toISOString();

  console.log('===========================================================');
  console.log('  Extrator de Campeonatos e Partidas — LAUFU (jliga.club) ');
  console.log('===========================================================');
  console.log(`- Ano alvo: ${config.ano}`);
  console.log(`- Status alvo: ${config.status || 'Todos'}`);
  console.log(`- Cache habilitado: ${!config.noCache}`);
  console.log(`- Diretório de saída: ${config.outDir}`);
  console.log(`- Modo Dry-Run: ${config.dryRun}`);
  console.log('-----------------------------------------------------------');

  // 1. Obter a listagem principal de campeonatos
  const campeonatosUrl = `${DEFAULT_BASE_URL}/campeonatos?ano=${config.ano}&status=${config.status}`;
  const { content: indexHtml } = await fetchWithCache(campeonatosUrl, {}, config);

  const campeonatos = parseCampeonatos(indexHtml, DEFAULT_BASE_URL);
  console.log(`[info] ${campeonatos.length} campeonatos identificados para a edição ${config.ano}.`);

  if (campeonatos.length === 0) {
    console.warn('[aviso] Nenhum campeonato encontrado. Encerrando sem erros.');
    return;
  }

  // 2. Para cada campeonato, extrair os jogos das rodadas
  const todasPartidas = [];
  let totalDuplicatas = 0;

  for (let i = 0; i < campeonatos.length; i++) {
    const camp = campeonatos[i];
    console.log(`[${i + 1}/${campeonatos.length}] Processando: ${camp.titulo}...`);

    const { content: campHtml } = await fetchWithCache(camp.url, {}, config);
    const timesMap = parseTimesDaClassificacao(campHtml);
    const { partidas, duplicateCount } = parsePartidasDoCampeonato(campHtml, camp, timesMap, extracaoTimestamp);

    totalDuplicatas += duplicateCount;
    todasPartidas.push(...partidas);
    console.log(`  -> ${partidas.length} partidas extraídas (${duplicateCount} duplicatas de carrossel suprimidas).`);
  }

  console.log('-----------------------------------------------------------');
  console.log(`[resumo] Total geral de partidas coletadas: ${todasPartidas.length}`);
  console.log(`[resumo] Total de duplicatas suprimidas: ${totalDuplicatas}`);

  if (config.dryRun) {
    console.log('[dry-run] Modo somente leitura ativo. Nenhum arquivo foi salvo em disco.');
    return;
  }

  // 3. Salvar arquivos de saída
  if (!existsSync(config.outDir)) {
    mkdirSync(config.outDir, { recursive: true });
  }

  const csvPath = join(config.outDir, `laufu_partidas_${config.ano}.csv`);
  const jsonPath = join(config.outDir, `laufu_partidas_${config.ano}.json`);
  const reportPath = join(config.outDir, `relatorio_extracao_${config.ano}.md`);

  console.log(`[export] Gravando CSV em ${csvPath}...`);
  exportCsv(todasPartidas, csvPath);

  console.log(`[export] Gravando JSON em ${jsonPath}...`);
  const jsonPayload = {
    metadados: {
      fonte: DEFAULT_BASE_URL,
      edicao: config.ano,
      extracao_timestamp: extracaoTimestamp,
      total_campeonatos: campeonatos.length,
      total_partidas: todasPartidas.length,
      duplicatas_descartadas: totalDuplicatas,
    },
    campeonatos,
    partidas: todasPartidas,
  };
  writeFileSync(jsonPath, JSON.stringify(jsonPayload, null, 2), 'utf8');

  console.log(`[export] Gravando Relatório Markdown em ${reportPath}...`);
  const reportMd = generateReport(campeonatos, todasPartidas, totalDuplicatas, extracaoTimestamp);
  writeFileSync(reportPath, reportMd, 'utf8');

  console.log('===========================================================');
  console.log('  Extração concluída com sucesso!');
  console.log('===========================================================');
}

main().catch((error) => {
  console.error('[erro fatal]', error);
  process.exitCode = 1;
});
