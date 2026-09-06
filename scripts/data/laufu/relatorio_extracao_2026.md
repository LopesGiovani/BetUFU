# Relatório de Extração de Partidas — LAUFU (jliga.club)

**Data e Hora da Extração:** 2026-09-06T02:32:52.781Z
**Total de Campeonatos Mapeados:** 10
**Total de Partidas Únicas Extraídas:** 671
**Duplicidades Descartadas (IDs repetidos em carrossel):** 0

## 1. Distribuição por Estado da Partida

| Estado | Descrição | Quantidade | Percentual |
| :--- | :--- | :---: | :---: |
| **CONCLUIDA** | Partidas realizadas com placar regular | 333 | 49.6% |
| **AGENDADA** | Partidas futuras ainda sem placar | 291 | 43.4% |
| **ADIADA** | Partidas de datas passadas sem resultado registrado | 1 | 0.1% |
| **WO** | Partidas finalizadas por desistência/falta | 46 | 6.9% |
| **CANCELADA** | Partidas canceladas pela organização | 0 | 0.0% |

> **Empates Registrados:** 8 partidas no tempo regulamentar (2.4% dos jogos concluídos).

## 2. Totais por Modalidade / Campeonato

| Campeonato | Modalidade | Categoria | Partidas |
| :--- | :--- | :--- | :---: |
| Peteca Feminino - LEU | Peteca | Feminino | 55 |
| Peteca Masculino - LEU | Peteca | Masculino | 66 |
| Futsal Masculino - LEU | Futsal | Masculino | 78 |
| Basquete Masculino - LEU | Basquete | Masculino | 91 |
| Handebol Masculino - LEU | Handebol | Masculino | 55 |
| Vôlei Masculino - LEU | Vôlei | Masculino | 78 |
| Futsal Feminino - LEU | Futsal | Feminino | 55 |
| Basquete Feminino - LEU | Basquete | Feminino | 36 |
| Handebol Feminino - LEU | Handebol | Feminino | 66 |
| Vôlei Feminino - LEU | Vôlei | Feminino | 91 |

## 3. Qualidade dos Dados e Campos Ausentes

- **Partidas sem data definida ("Sem data"):** 251
- **Partidas sem local/quadra definido:** 275
- **Validação de Chaves:** Todos os registros possuem `origem_id` único e não-nulo.
- **Integridade das Equipes:** 100% dos confrontos possuem mandante e visitante identificados.

---
*Relatório gerado automaticamente por `scripts/extract-laufu.mjs`.*