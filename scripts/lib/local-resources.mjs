#!/usr/bin/env node

/**
 * scripts/lib/local-resources.mjs
 *
 * Seleção fail-closed dos recursos Docker locais da BETUFU.
 *
 * Este módulo é DELIBERADAMENTE PURO: sem I/O, sem spawn, sem Docker. Ele recebe
 * um inventário já coletado e devolve o que pode ser removido, com o motivo de
 * cada exclusão. É essa pureza que permite provar o escopo da remoção sem
 * remover nada de verdade — um teste que criasse recurso homônimo real e
 * rodasse o reset em cima causaria justamente a perda que deveria detectar.
 *
 * Por que a conjunção de critérios, e não o label de projeto sozinho:
 * `scripts/lib/compose.mjs` fixa `--project-name infra`, e o Compose deriva o
 * nome do projeto do diretório. `infra` é banal — qualquer outra composição num
 * diretório com esse nome produz labels idênticos. O discriminador que de fato
 * amarra um recurso a ESTE checkout é `com.docker.compose.project.config_files`,
 * verificado presente em containers nesta máquina (Compose 5.3.1).
 *
 * Volumes e redes NÃO carregam esse label — verificado, não presumido. Volumes
 * sobrevivem porque `infra/compose.yaml` fixa `name:` explícito, o que lhes dá
 * nome global e distintivo (`betufu-local-*`). Redes ficam fora da allowlist:
 * não guardam dado, são recriadas pelo `up`, e não há critério forte para elas.
 *
 * A assimetria que governa cada decisão aqui: remover a mais é perda de dados
 * de terceiro; remover a menos é rodar o comando de novo. Na dúvida, ignora.
 */

export const COMPOSE_PROJECT = 'infra';

export const CONFIRMATION_PHRASE = 'APAGAR DADOS LOCAIS BETUFU';

/** Serviços declarados em infra/compose.yaml (profiles daily e full). */
export const LOCAL_SERVICES = Object.freeze([
  'postgres',
  'redis',
  'keycloak',
  'seaweedfs',
  'mailpit',
  'backend',
  'frontend',
]);

/** Volumes com `name:` explícito em infra/compose.yaml:187-195. */
export const LOCAL_VOLUMES = Object.freeze([
  'betufu-local-postgres-data',
  'betufu-local-redis-data',
  'betufu-local-seaweed-data',
  'betufu-local-mailpit-data',
]);

const LABEL_PROJECT = 'com.docker.compose.project';
const LABEL_CONFIG_FILES = 'com.docker.compose.project.config_files';
const LABEL_SERVICE = 'com.docker.compose.service';
const LABEL_ONEOFF = 'com.docker.compose.oneoff';
const LABEL_VOLUME = 'com.docker.compose.volume';

const SERVICE_SET = new Set(LOCAL_SERVICES);
const VOLUME_SET = new Set(LOCAL_VOLUMES);

/**
 * Normaliza um caminho para comparação entre plataformas.
 *
 * O label `config_files` volta com barras invertidas no Windows e barras
 * normais no Linux, então comparar cru geraria falso negativo — o reset
 * ignoraria os próprios containers. Case-folding é aplicado SOMENTE em win32,
 * onde o sistema de arquivos é case-insensitive; aplicá-lo no Linux tornaria a
 * comparação frouxa e permitiria casar um checkout vizinho de nome parecido.
 *
 * @param {string} value
 * @param {string} platform - valor de process.platform
 * @returns {string|null}
 */
export function normalizeConfigPath(value, platform) {
  if (typeof value !== 'string' || value.trim() === '') {
    return null;
  }
  const separatorsUnified = value.replace(/\\/gu, '/');
  // Colapsa barras repetidas SEM tocar num prefixo UNC (`//host/share`).
  // Colapsar `//host/x` para `/host/x` o aproximaria de um caminho local
  // homônimo — improvável, mas na direção perigosa.
  const uncPrefix = separatorsUnified.startsWith('//') ? '//' : '';
  const body = separatorsUnified.substring(uncPrefix.length).replace(/\/{2,}/gu, '/');
  const unified = uncPrefix + body;
  return platform === 'win32' ? unified.toLowerCase() : unified;
}

/**
 * Compara a confirmação digitada com a frase exigida.
 *
 * Byte a byte, sem `trim()` e sem case-folding: a frase é a última barreira
 * antes de uma remoção irreversível, e tolerar variação a transformaria em
 * ritual. Um `\r` de terminal Windows é removido porque é artefato de
 * transporte da linha, não conteúdo digitado.
 *
 * @param {string} input
 * @returns {boolean}
 */
export function isExactConfirmation(input) {
  if (typeof input !== 'string') {
    return false;
  }
  const withoutLineEnding = input.replace(/\r?\n$/u, '').replace(/\r$/u, '');
  return withoutLineEnding === CONFIRMATION_PHRASE;
}

/**
 * Decide o que pode ser removido.
 *
 * @param {{containers?: Array<{name: string, id?: string, labels?: Record<string,string>}>,
 *          volumes?: Array<{name: string, labels?: Record<string,string>}>}} inventory
 * @param {{composeFile: string, platform: string}} anchors
 * @returns {{remove: {containers: Array<{name: string, id: string}>,
 *                     volumes: Array<{name: string, id: string}>},
 *            ignored: Array<{kind: string, name: string, reason: string}>}}
 */
export function selectRemovable(inventory, anchors) {
  if (inventory === null || typeof inventory !== 'object') {
    throw new TypeError('inventory deve ser um objeto');
  }
  if (anchors === null || typeof anchors !== 'object') {
    throw new TypeError('anchors deve ser um objeto');
  }

  const expectedConfig = normalizeConfigPath(anchors.composeFile, anchors.platform);
  if (expectedConfig === null) {
    // Sem âncora não há como distinguir este checkout de outro. Fail-closed.
    throw new Error('anchors.composeFile é obrigatório para ancorar o checkout');
  }

  const containers = Array.isArray(inventory.containers) ? inventory.containers : [];
  const volumes = Array.isArray(inventory.volumes) ? inventory.volumes : [];

  const remove = { containers: [], volumes: [] };
  const ignored = [];

  for (const container of containers) {
    const name = readName(container);
    if (name === null) {
      // Recurso sem nome utilizável é recusado E registrado. Descartá-lo em
      // silêncio faria a contagem de "examinados" do dry-run mentir.
      ignored.push({ kind: 'container', name: '(sem nome)', reason: 'nome ausente ou inválido' });
      continue;
    }
    const labels = readLabels(container);
    const reason = containerRejection(labels, expectedConfig, anchors.platform);
    if (reason === null) {
      remove.containers.push({ name, id: readId(container) ?? name });
    } else {
      ignored.push({ kind: 'container', name, reason });
    }
  }

  for (const volume of volumes) {
    const name = readName(volume);
    if (name === null) {
      ignored.push({ kind: 'volume', name: '(sem nome)', reason: 'nome ausente ou inválido' });
      continue;
    }
    const labels = readLabels(volume);
    const reason = volumeRejection(name, labels);
    if (reason === null) {
      // Volumes não têm ID separado no Docker: o nome é a identidade.
      remove.volumes.push({ name, id: name });
    } else {
      ignored.push({ kind: 'volume', name, reason });
    }
  }

  return { remove, ignored };
}

/**
 * Quatro condições conjuntas. Devolve o motivo da recusa, ou null para aprovar.
 */
function containerRejection(labels, expectedConfig, platform) {
  const service = labels[LABEL_SERVICE];
  if (!SERVICE_SET.has(service)) {
    return `serviço "${service ?? '(ausente)'}" fora da allowlist BETUFU`;
  }
  if (labels[LABEL_PROJECT] !== COMPOSE_PROJECT) {
    return `projeto Compose "${labels[LABEL_PROJECT] ?? '(ausente)'}" != "${COMPOSE_PROJECT}"`;
  }
  if (labels[LABEL_ONEOFF] !== 'False') {
    return 'container one-off (criado por docker compose run)';
  }
  const actualConfig = normalizeConfigPath(labels[LABEL_CONFIG_FILES], platform);
  if (actualConfig === null) {
    return 'sem label config_files — não ancorável a este checkout';
  }
  if (actualConfig !== expectedConfig) {
    return 'config_files aponta para outro checkout';
  }
  return null;
}

/**
 * Três condições conjuntas. Volumes não têm config_files — verificado nesta
 * máquina —, então o nome literal é o discriminador forte.
 */
function volumeRejection(name, labels) {
  if (!VOLUME_SET.has(name)) {
    return 'nome fora da allowlist literal BETUFU';
  }
  if (labels[LABEL_PROJECT] !== COMPOSE_PROJECT) {
    return `projeto Compose "${labels[LABEL_PROJECT] ?? '(ausente)'}" != "${COMPOSE_PROJECT}"`;
  }
  if (labels[LABEL_VOLUME] !== name) {
    return 'label de volume divergente do nome';
  }
  return null;
}

function readName(resource) {
  if (resource === null || typeof resource !== 'object') return null;
  const name = resource.name;
  return typeof name === 'string' && name.trim() !== '' ? name : null;
}

function readId(resource) {
  const id = resource.id;
  return typeof id === 'string' && id.trim() !== '' ? id : null;
}

function readLabels(resource) {
  const labels = resource.labels;
  return labels !== null && typeof labels === 'object' ? labels : {};
}
