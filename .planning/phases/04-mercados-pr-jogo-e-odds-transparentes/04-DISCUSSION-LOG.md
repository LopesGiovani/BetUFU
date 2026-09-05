# Phase 4: Mercados Pre-jogo e Odds Transparentes - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in '04-CONTEXT.md'; this log preserves the alternatives considered.

**Date:** 2026-09-02
**Phase:** 4 - Mercados Pre-jogo e Odds Transparentes
**Areas discussed:** modelo de forca, empate e dados escassos, estados e margem,
governanca e explicacao, dados LAUFU e evolucao futura

> Este registro formaliza decisoes tomadas interativamente nas mensagens
> anteriores. Nenhuma escolha foi inferida a partir de silencio nesta passada.

---

## Modelo de forca, recencia e edicoes

| Option | Description | Selected |
|--------|-------------|----------|
| Rating global | Mistura modalidades e categorias em uma escala comum | |
| Elo separado | Rating inicial 1500 por modalidade/categoria, configuravel e versionado | ✓ |
| Elo com decaimento mensal | Aplica meia-vida fixa para resultados antigos | |
| Atualizacao cronologica | Ordena pelo instante esportivo e evita meia-vida mensal arbitraria | ✓ |
| K fixo | Usa a mesma velocidade de ajuste para toda equipe | |
| K por experiencia | K=32 para equipe provisoria e K=16 depois de limiar escolhido por backtest | ✓ |
| Sem regressao entre edicoes | Continua o rating indefinidamente | |
| Regressao parcial | Regride 50% em direcao a 1500 a cada nova edicao | ✓ |

**User's choice:** Elo separado por modalidade/categoria, processamento
cronologico, K provisoriamente 32 e depois 16, e regressao de 50% entre edicoes.

**Notes:** O limiar de troca do K e os detalhes numericos restantes devem ser
medidos em backtest; nao sao uma autorizacao para escolher silenciosamente um
numero durante a implementacao.

---

## Empate, dados escassos e fontes

| Option | Description | Selected |
|--------|-------------|----------|
| Sempre dois resultados | Ignora a possibilidade de empate em qualquer esporte | |
| Davidson condicional | Dois resultados quando empate e impossivel; tres quando regulamento/fase permitem | ✓ |
| Empate por categoria desde o inicio | Estima uma taxa independente para cada modalidade/categoria | |
| Tendencia compartilhada | Usa sinal conservador compartilhado e especializa apenas com amostra suficiente | ✓ |
| Bloquear mercados sem historico | Nao publica ate existir amostra grande | |
| Publicar neutro com sinal | Publica estimativa neutra e marca baixa confianca | ✓ |

**User's choice:** Davidson somente onde fizer sentido esportivo; tendencia de
empate inicialmente compartilhada e conservadora; mercados podem existir com
baixa confianca, sem odds manuais rotineiras.

**Notes:** A raspagem LAUFU encontrou poucos empates, mas isso nao justifica
forcar empate em modalidades que nao o admitem. Baixa confianca nao aumenta a
margem.

---

## Estados, W.O. e remarcacao

| Option | Description | Selected |
|--------|-------------|----------|
| W.O. como placar esportivo | Converter automaticamente para 20 x 0, 10 x 0 ou equivalente | |
| W.O. administrativo | Registrar vitoria administrativa sem placar e excluir da calibragem esportiva inicial | ✓ |
| Cancelamento como resultado | Alimentar o modelo com um resultado neutro | |
| Cancelamento/anulacao | Nao altera o modelo; mercado e aposta futura podem ser anulados | ✓ |
| Adiamento sem mudanca | Manter a mesma versao de preco | |
| Suspender e versionar | Suspender durante adiamento; remarcacao cria nova versao | ✓ |

**User's choice:** W.O. nao e 20 x 0 nem outro placar esportivo sintetico.
Cancelamento, W.O. ou abandono sem resultado valido anulam o mercado e devolvem
creditos quando a camada de apostas existir; adiamento suspende e remarcacao
versiona.

**Notes:** O comportamento de credito/liquidacao e contrato para as fases
posteriores, nao implementacao desta fase.

---

## Margem, publicacao e odds aceitas

| Option | Description | Selected |
|--------|-------------|----------|
| Margem variavel por demanda | Ajusta o preco conforme volume de palpites | |
| Margem fixa inicial | 5%, explicita, configuravel e versionada | ✓ |
| Arredondamento livre | Exibe o valor calculado sem regra documentada | |
| Publicacao deterministica | Duas casas, arredondamento reproduzivel e valor congelado | ✓ |
| Probabilidade/odd unificadas | Guarda somente a odd final | |
| Probabilidade justa separada | Registra probabilidade sem margem e odd publicada como fatos distintos | ✓ |

**User's choice:** O volume de apostas nao move o preco no MVP. A margem
comeca em 5%; odds exibidas usam duas casas e a aposta futura usa exatamente a
odd/versao que foi mostrada e aceita.

**Notes:** A separacao entre probabilidade justa e preco publicado preserva a
transparencia e permite uma evolucao comercial posterior sem reescrever o MVP.

---

## Governanca, explicacao e H2H

| Option | Description | Selected |
|--------|-------------|----------|
| Override unilateral | Administrador altera a odd diretamente | |
| Override excepcional governado | Suspensao, fonte corrigida/recalculo tentado, motivo/evidencia e dois administradores | ✓ |
| Explicacao resumida | Mostra somente a odd atual | |
| Explicacao em duas camadas | Resumo simples mais detalhe auditavel, historico e confianca | ✓ |
| H2H como bonus | Aumenta ou reduz automaticamente a odd por confronto direto | |
| H2H contextual | Aparece como evidencia explicativa, sem bonus no MVP | ✓ |

**User's choice:** Override exige proposta comparavel ao modelo, auditoria,
segundo administrador distinto, nova versao imutavel e nunca muda aposta aceita
ou mercado CLOSED/LOCKED.

**Notes:** A visibilidade de capabilities continua conveniencia de navegacao;
autorizacao real permanece no backend. O H2H pode ser reavaliado apos varias
edicoes e backtests.

---

## Dados LAUFU e evolucao futura

| Option | Description | Selected |
|--------|-------------|----------|
| Adaptar o modelo ao site | Codificar regras especificas da LAUFU como contrato permanente | |
| Usar como amostra | Usar a extracao para exploracao/backtest, mantendo o dominio generico | ✓ |
| Precificacao estilo sportsbook agora | Mover odds pela exposicao e pelo comportamento dos apostadores no MVP | |
| Caminho futuro condicionado | Separar probabilidade justa e so estudar ajustes comerciais com liquidez, antifraude e revisao | ✓ |

**User's choice:** A BETUFU deve caminhar futuramente para praticas de
bookmakers tradicionais quando houver base e governanca, mas o MVP permanece
estatistico, fixo e transparente.

**Notes:** A extracao atual tem 671 partidas (326 regulares, 299 agendadas e
46 W.O.) e serve para investigacao. Os artefatos nao rastreados nao foram
incluidos na allowlist desta fase; problemas de cache, identidade e separacao
de placares ficam como manutencao de dados separada.

---

## the agent's Discretion

- Estruturas internas, nomes de classes, algoritmo numericamente equivalente e
  detalhes de apresentacao, desde que preservem o contrato acima.
- Limiar de experiencia para K=16 e parametros Davidson, sempre com backtest,
  documentacao e checkpoint apropriado.

## Deferred Ideas

- Bonus H2H e especializacao por pool somente com amostra e evidencia.
- Ajuste de preco por exposicao/liquidez e outras tecnicas comerciais de
  sportsbook, depois de revisao formal de ODDS-05.
- Mercados de varios competidores, acumuladas, liquidacao e hardening do
  extrator pertencem a trabalho posterior.
