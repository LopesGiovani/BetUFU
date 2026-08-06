# BETUFU — Handoff de contexto arquivado

> Registro histórico arquivado em 2026-08-06. Não representa o estado atual; consulte `.planning/STATE.md`.

**Atualizado em:** 2026-08-01
**Estado:** definições consolidadas em rascunho, aguardando revisão do usuário
**Próximo passo:** validar e apresentar o diff de PROJECT.md, REQUIREMENTS.md e config.json
**Não fazer ainda:** roadmap, STATE.md, código ou commit

## Regra de colaboração

O usuário decide conscientemente produto, arquitetura e commits. Não transformar sugestão em decisão, não criar novas fases e não commitar automaticamente.

`.planning/config.json` foi alterado para `"commit_docs": false`. Isso impede o GSD de commitar documentos automaticamente; os documentos poderão ser versionados manualmente somente depois de o usuário revisar o diff e autorizar.

## Situação do projeto

- BETUFU é um projeto greenfield; nenhum código foi criado.
- GSD Core 1.9.1 está instalado corretamente para Codex.
- `.planning/PROJECT.md` existia, mas estava desatualizado.
- `.planning/REQUIREMENTS.md` existia como rascunho não rastreado e continha decisões superadas.
- Os dois documentos foram consolidados com as decisões aprovadas na conversa.
- A consolidação ainda é um rascunho: o usuário precisa revisar o diff.
- Não existem `.planning/ROADMAP.md` nem `.planning/STATE.md`.
- Nenhum commit foi criado nesta consolidação.

## Produto aprovado

- Aplicação web responsiva, independente e não oficial, inicialmente para uma edição das Olimpíadas UFU.
- Créditos apenas virtuais, sem compra, venda, transferência, saque, conversão ou prêmio econômico.
- Navegação pública e cadastro aberto de conta BETUFU.
- Não existe login ou SSO UFU.
- Verificação de matrícula é opcional para a conta em geral, mas obrigatória para apostar em eventos internos e participar do ranking.
- Comprovante privado, revisão manual, exclusão após decisão e HMAC normalizado para unicidade.
- Páginas de equipes, grupos privados, chat por partida, moderação e conquistas integram o MVP.
- Notificações de engajamento sobre partidas, liquidação, chat e ranking, contestação formal de resultados, mercados multicompetidor e aplicativos nativos ficam depois do MVP; avisos obrigatórios de segurança e privacidade continuam no MVP.

## Economia e ranking aprovados

- Todo estudante verificado começa com a mesma quantidade de créditos.
- O valor inicial exato ainda será definido.
- Saldo persiste entre edições e não reinicia automaticamente no MVP.
- Usuário pode zerar e permanecer sem conseguir apostar por tempo indeterminado.
- Não existem missões, recargas ou recuperação no MVP.
- O desenho específico do “palpite de recuperação” foi rejeitado e não deve ser sugerido novamente; qualquer outra fonte futura de créditos exigirá nova decisão.
- O ranking principal já é o ranking UFU, pois apostar exige verificação.
- Ranking UFU é vitalício por enquanto.
- Métrica principal: total de acertos.
- Primeiro desempate: taxa de acerto.
- Se o empate persistir, usuários compartilham a posição.
- Aposta anulada, cancelada ou devolvida não conta como acerto nem erro.
- Um mercado gera no máximo um acerto por usuário, sem multiplicação por stake ou odd.
- Grupos privados usam as mesmas métricas do ranking UFU, filtradas aos integrantes.

## Equipes, atletas e matrícula

- Administrador da competição cadastra equipes e elencos no próprio escopo.
- Atleta e usuário BETUFU são registros distintos.
- Atleta pode constar no elenco sem possuir conta BETUFU.
- Para cadastrar o vínculo, o administrador apresenta comprovante aceito ou fonte confiável; matrícula isolada não basta.
- O sistema gera HMAC e elimina o número original e o comprovante conforme a política.
- Se o atleta criar conta e verificar a mesma matrícula depois, a coincidência de HMAC permite o vínculo.
- Associação deve ser única e auditável; conflito exige revisão manual.
- A pessoa é notificada e pode contestar vínculo incorreto.
- Elenco é travado antes da abertura do mercado; mudanças posteriores são auditadas e podem exigir análise das apostas afetadas.

## Odds e resultados

- Odds pré-jogo são calculadas automaticamente.
- Baseline: Elo separado por modalidade e categoria, recência, regressão entre edições, peso limitado de confronto direto e margem inicial configurável de 5%.
- Volume apostado não influencia odds.
- Dados insuficientes geram estimativa neutra com baixa confiança.
- Não há edição manual rotineira.
- Override excepcional exige corrigir a fonte primeiro quando possível, suspender mercado, justificar e anexar evidência, comparar modelo e proposta, obter aprovação de segundo administrador distinto com MFA e criar nova versão imutável.
- Override não ocorre após o fechamento e não altera odds já aceitas.
- Um administrador registra a versão inicial ou corretiva do resultado; outro, distinto, confirma; só então ocorre liquidação, reversão ou reliquidação.
- Cancelamento devolve créditos, adiamento suspende e correções usam reversões e lançamentos compensatórios.

## Integridade e privacidade

- Atleta não aposta na própria equipe.
- Administrador de competição não aposta na competição atribuída.
- Administrador geral não aposta em nenhum evento.
- Ações administrativas e acessos sensíveis são auditados.
- A identidade pessoal do fundador não será exposta publicamente; operação usa identidade institucional legítima.
- Não existe promessa de irrastrabilidade perante provedores, contratos ou autoridades.
- Revisão jurídica e LGPD são condição pré-lançamento.

## Arquitetura aprovada

- Frontend: Angular 22, TypeScript 6, standalone components, Material/CDK, Signals, RxJS, Reactive Forms e cliente OpenAPI gerado.
- Backend: Java 25 LTS, Spring Boot 4.1, Maven e Spring Modulith 2.1.
- Autenticação: Keycloak e padrão BFF com sessão no servidor; sem tokens no localStorage.
- Dados: PostgreSQL 18, Flyway, JPA para o comum, SQL/locks no crítico, ledger e odds imutáveis.
- Infra auxiliar: Redis, armazenamento S3 privado, Docker Compose, Nginx e bancos separados de BETUFU e Keycloak.
- API: REST JSON `/api/v1`, OpenAPI, Problem Details, idempotência e WebSocket/STOMP.
- Monorepo: `backend/`, `frontend/`, `infra/` e `docs/`.
- Sem microsserviços, Kafka, Kubernetes, NgRx, Nx ou microfrontends no início.
- Testes: JUnit, Testcontainers, Modulith Test, ArchUnit, contrato, componentes Angular, Playwright, concorrência e carga.

## Decisões ainda abertas

- Quantidade inicial de créditos.
- Limites por aposta, precisão, fórmula de pagamento e arredondamento.
- Prazo de validade da verificação UFU.
- Visibilidade no ranking durante expiração da verificação.
- Matrículas reemitidas ou múltiplas matrículas legítimas da mesma pessoa.
- Correção quando pagamento incorreto já foi gasto.
- Aprovação de ajuste compensatório direto no ledger; dupla aprovação foi recomendada, mas ainda não aprovada.
- Aposta aceita antes de vínculo tardio de atleta, elenco ou papel revelar conflito.
- Parâmetros do Elo para empate e fixtures numéricas por modalidade.
- Rotação e resposta a comprometimento da chave HMAC.
- Sustentabilidade da oferta total de créditos sem reset ou reposição.
- Modalidades e tabelas exatas do piloto.
- Provedor de nuvem e CI/CD.

## Próxima sequência segura

1. Validar sintaxe, IDs, cobertura e conflitos dos documentos consolidados.
2. Mostrar ao usuário o diff e um resumo das mudanças.
3. Ajustar qualquer ponto indicado pelo usuário.
4. Pedir autorização explícita antes de qualquer commit.
5. Somente depois discutir a estrutura vertical do MVP e criar o roadmap.
