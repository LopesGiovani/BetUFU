# Walking Skeleton — BETUFU

**Phase:** 1
**Generated:** 2026-08-02

## Capability Proven End-to-End

Uma conta técnica local entra pelo fluxo OIDC hospedado do Keycloak usando `http://localhost:4200` como origem pública same-origin, recebe somente uma sessão BFF e, exclusivamente em development/homolog ou harness de teste, aciona pelo cliente Angular gerado o vínculo `/api/v1` que persiste e lê `(iss, sub)` no PostgreSQL sem token no armazenamento do navegador. O build production contém somente a estrutura inicial da interface BETUFU e não contém rota, link, URL divulgada, import ou lazy chunk alcançável do tracer.

A prova usa `PUT /api/v1/tracer/identity/current` como interação idempotente no harness não-prod do 01-21 e, após 01-08, repete a jornada com cookie BFF e XSRF pelo proxy/Nginx da mesma origem. 01-21 e 01-28 constroem production e falham se o tracer for divulgado ou alcançável. Claims allowlisted vêm do ID token/OidcUser sem consulta UserInfo; invalidações internas revogam o refresh token server-side, enquanto `Sair` no 01-22 encerra somente o dispositivo atual.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Java 25 + Spring Boot 4.1.0 + Spring Modulith 2.1.0 via Maven; Angular 22.1.x + TypeScript 6.0.x | Preserva ARCH-01/02; `scripts/maven.mjs` seleciona o Wrapper em Windows/Linux e os smokes bloqueiam downgrade. |
| Data layer | PostgreSQL 18 + Flyway; Redis somente auxiliar; contrato S3 privado por `BETUFU_S3_*`, com SeaweedFS somente local | PostgreSQL permanece a fonte autoritativa, o backend não depende do provedor local e toda mudança de schema é versionada. |
| Auth | Keycloak OIDC Authorization Code com `state`, `nonce` e PKCE; Spring como BFF; UserInfo OPT-OUT; revogação de refresh token e end-session via discovery | Mantém credenciais/tokens fora do Angular, prova D-15 no login nativo e fecha IDEN-01–IDEN-04 sem invalidar outras sessões. |
| API boundary | REST JSON `/api/v1`, OpenAPI canônico, Problem Details e cliente `typescript-angular` gerado inicialmente no 01-20 e novamente no 01-26 | API-01/API-02 exigem contrato único, operationIds estáveis e check read-only de drift no gate. |
| Deployment target | Dois modos locais na origem `http://localhost:4200`: daily com proxy Angular e full com Spring + Nginx | D-10/OPS-01 exigem desenvolvimento rápido e uma composição integral com o mesmo contrato same-origin. |
| Directory layout | Monorepo `backend/`, `frontend/`, `infra/`, `docs/` e scripts cross-platform na raiz | Mantém os limites de ARCH-01 e permite que fases posteriores adicionem slices sem mudar a espinha dorsal. |

## Stack Touched in Phase 1

- [ ] Project scaffold — wrappers, launcher Maven portátil, runtimes, builds, lint/test e lockfiles fixados
- [ ] Routing — estrutura inicial autenticada; diagnóstico e tracer materialmente ausentes do build de produção
- [ ] Database — criação idempotente do vínculo `(iss, sub)` e leitura real via PostgreSQL 18/Flyway
- [ ] UI — diagnóstico `Verificar novamente` autorizado em dev/homolog e tracer apenas no harness não-prod
- [ ] Deployment — modos `daily` e `full`, proxy/Nginx same-origin e orquestrador de validação documentados

## Out of Scope (Deferred to Later Slices)

- Cadastro, recuperação, verificação de e-mail e MFA customizados no Angular; esses fluxos permanecem nativos do Keycloak.
- Verificação de matrícula, catálogo esportivo, odds, créditos, palpites, ledger de negócio, liquidação, ranking, grupos e chat.
- Microsserviços, Kafka, Kubernetes, NgRx, Nx, microfrontends e aplicativos nativos.
- Nova confirmação de MFA por operação sensível e opção voluntária de encerrar todas as sessões.

## Subsequent Slice Plan

- Phase 2: contas BETUFU, verificação UFU e operações governadas.
- Phase 3: dados esportivos confiáveis do piloto.
- Phase 4: mercados pré-jogo e odds transparentes.
- Phase 5: créditos virtuais e palpites protegidos.
- Phase 6: resultados, liquidação, ranking, grupos e conquistas.
- Phase 7: chat moderado e prontidão pública do piloto.
