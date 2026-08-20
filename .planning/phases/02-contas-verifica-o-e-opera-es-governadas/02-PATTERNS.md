# Fase 2: Contas, Verificação e Operações Governadas — Mapa de Padrões

**Mapeado em:** 2026-08-20
**Arquivos classificados:** 61
**Análogos encontrados:** 55 / 61

> **Como usar este documento.** Cada arquivo novo desta fase tem um análogo real no repositório,
> com `caminho:linha`. O planejador deve citar o análogo na seção de ações do PLAN, e o executor
> deve copiar a forma — não reinventá-la. Onde não há análogo, o documento diz explicitamente e
> aponta para a seção do `02-RESEARCH.md` que serve de referência.
>
> Nomes de arquivo marcados **(proposto)** vêm do esboço do `02-RESEARCH.md` §5/§7 e **não são
> decisões travadas**; o rigor de forma é que é obrigatório.

---

## Sumário executivo dos padrões

1. **Migration Flyway** — `V1__create_local_identity.sql` é o gabarito único: schema `public.`
   explícito, PK `uuid DEFAULT gen_random_uuid()`, `COLLATE "C"` em toda coluna textual comparada
   por igualdade, `timestamptz(6) DEFAULT CURRENT_TIMESTAMP(6)`, **toda** constraint nomeada
   (`pk_`/`uq_`/`ck_`), `CHECK` de não-vazio **e** de `octet_length` por coluna textual, `CHECK` de
   ordem temporal. `V2` acrescenta o precedente de semente idempotente na própria migration.
2. **Fatia persistida (entidade→repo→service→controller→DTO)** — `br.com.betufu.identity.internal`
   é a fatia de referência completa e a única do repositório. Perfil, matrícula, papéis e auditoria
   copiam essa forma inteira, inclusive `@Profile("runtime | integrated")` e o par
   `insertIfAbsent` + `findByNaturalKey`.
3. **Erros** — `ApiErrorCode` é um enum fechado de códigos publicados (`BETUFU-<status>-<slug>`) com
   `title`/`detail`/`action` em pt-BR. Códigos novos entram **no enum existente**, nunca em estrutura
   paralela. `FieldViolation` já omite deliberadamente o valor rejeitado — isso fecha, por
   construção, o vetor de vazamento nº 2 apontado no `02-RESEARCH.md` §6.
4. **Fatia Angular** — `frontend/src/app/features/diagnostic/` é o gabarito de 7 arquivos:
   `*.models.ts` (tipos readonly + estados nomeados), `*-api.adapter.ts` (adapta o cliente gerado e
   **falha fechada** com erros `CONTRATO_*`), `*.store.ts` (`signal` + `computed`, `providedIn:
   'root'`, retry limitado, `sanitizeError`), `*-page.component.ts/.html/.scss/.spec.ts`.
5. **Autoridade e ausência material** — `DiagnosticController.java:11-33` documenta o padrão de
   "ausência, não 403" via `@Profile`, e delega a autoridade para a cadeia única em
   `SecurityConfiguration`. As regras de papel da Fase 2 entram **na mesma cadeia**.
6. **Cliente gerado** — `frontend/src/app/api/generated/**` é *machine-owned* (20 arquivos, digest
   governado). **Nunca editar à mão.** Endpoint novo ⇒ editar `backend/openapi/betufu.yaml` no
   mesmo plano ⇒ regenerar ⇒ atualizar o digest aprovado (aprovação do responsável).

---

## Classificação dos arquivos

### Backend — migrations

| Arquivo (novo/modificado) | Papel | Fluxo de dados | Análogo mais próximo | Qualidade |
|---|---|---|---|---|
| `backend/src/main/resources/db/migration/V3__create_betufu_profile.sql` *(proposto)* | migration | DDL | `db/migration/V1__create_local_identity.sql:1-21` | exata |
| `.../V4__create_ufu_enrollment.sql` *(proposto)* | migration | DDL + índice único parcial | `V1:1-21` (forma) + **sem análogo** para `CREATE UNIQUE INDEX ... WHERE` | parcial |
| `.../V5__create_operational_role_grant.sql` *(proposto)* | migration | DDL + índice único parcial | `V1:1-21` | parcial |
| `.../V6__create_audit_event.sql` *(proposto)* | migration | DDL + trigger de imutabilidade | `V1:1-21` (forma); **sem análogo** para trigger/rule | parcial |

### Backend — módulo `identity` (perfil)

| Arquivo | Papel | Fluxo | Análogo | Qualidade |
|---|---|---|---|---|
| `identity/internal/BetufuProfile.java` *(proposto)* | entity | CRUD | `identity/internal/LocalIdentity.java:15-48` | exata |
| `identity/internal/BetufuProfileRepository.java` | repository | CRUD | `identity/internal/LocalIdentityRepository.java:11-31` | exata |
| `identity/internal/BetufuProfileService.java` | service | CRUD | `identity/internal/LocalIdentityService.java:8-25` | exata |
| `identity/internal/ProfileController.java` | controller | request-response | `identity/internal/LocalIdentityController.java:12-47` | exata |
| `identity/internal/PublicProfileResponse.java` | DTO | response | `identity/internal/CurrentIdentityResponse.java:6-10` | exata |
| `identity/internal/ProfileUpdateRequest.java` | DTO | request + Bean Validation | **sem análogo** — não existe DTO de entrada validado no repositório | ausente |
| `identity/internal/AdminSeedInitializer.java` *(D-47)* | initializer | batch idempotente | `LocalIdentityService.java:18-25` (`insertIfAbsent`) + `V2:26-27` (semente) | papel-parcial |

### Backend — módulo `verification` (matrícula)

| Arquivo | Papel | Fluxo | Análogo | Qualidade |
|---|---|---|---|---|
| `verification/internal/UfuEnrollment.java` | entity | CRUD | `LocalIdentity.java:15-48` | exata |
| `verification/internal/UfuEnrollmentRepository.java` | repository | CRUD + `ON CONFLICT` | `LocalIdentityRepository.java:13-21` | exata |
| `verification/internal/MatriculaNormalizer.java` | utility | transform puro | **sem análogo direto**; forma mais próxima: `LogSanitizer.java:38-61` (constantes `Pattern` estáticas finais) | parcial |
| `verification/internal/MatriculaHmac.java` | utility | transform criptográfico | **sem análogo**; ver `02-RESEARCH.md` §3 | ausente |
| `verification/internal/MatriculaHmacKeyProperties.java` (falha fechada, D-33) | config | bootstrap | `platform/session/SessionSecurityConfiguration.java:135-139` (`requireProperty` → `IllegalStateException` em `postProcessAfterInitialization`) | papel-match |
| `verification/internal/EnrollmentService.java` | service | CRUD + guarda de unicidade | `LocalIdentityService.java:18-25` | papel-match |
| `verification/internal/EnrollmentController.java` | controller | request-response | `LocalIdentityController.java:24-47` | exata |
| `verification/internal/EnrollmentAttemptLimiter.java` (D-30) | service | contador Redis com TTL | `platform/persistence/RedisAuxiliaryProbe.java` (uso de `RedisConnectionFactory`) | parcial |

### Backend — papéis operacionais e sessão

| Arquivo | Papel | Fluxo | Análogo | Qualidade |
|---|---|---|---|---|
| `identity/internal/OperationalRoleGrant.java` | entity | CRUD | `LocalIdentity.java:15-48` | exata |
| `identity/internal/OperationalRoleGrantRepository.java` | repository | CRUD + `FOR UPDATE` | `LocalIdentityRepository.java:11-31` | papel-match |
| `identity/internal/RoleGrantService.java` (guardas D-48a/b) | service | CRUD transacional | `LocalIdentityService.java:18-25` (`@Transactional(isolation = …)`) | papel-match |
| `identity/internal/RoleGrantController.java` | controller | request-response | `LocalIdentityController.java` + `platform/session/CurrentDeviceLogoutController.java` | exata |
| `platform/security/BetufuRoleAuthorityMapper.java` *(ou extensão)* | security mapper | event-driven | `platform/security/OidcTechnicalAuthorityMapper.java:33-87` (allowlist fechada, "acrescenta, nunca substitui") | exata |
| `platform/session/IdentitySessionTerminationService.java` (`terminateAllSessionsFor`) | service | fan-out por sessão | `platform/session/OidcSessionRevocationService.java:97-208` + `SessionInvalidationListener.java:29-49` | papel-match |
| `platform/session/SessionSecurityConfiguration.java` **(modificado)** | config | bootstrap guard | ele mesmo — `:83-98` é o guard que precisa ser reformulado | própria |
| `platform/security/SecurityConfiguration.java` **(modificado)** | config | request-response | ele mesmo — `:216-266` | própria |

### Backend — auditoria

| Arquivo | Papel | Fluxo | Análogo | Qualidade |
|---|---|---|---|---|
| `audit/internal/AuditEvent.java` | entity | append-only | `LocalIdentity.java:15-48` (`@Immutable` + `updatable = false`) | exata |
| `audit/internal/AuditEventRepository.java` | repository | insert-only | `LocalIdentityRepository.java:13-21` | exata |
| `audit/internal/AuditService.java` | service | event-driven | `LocalIdentityService.java:8-25` | papel-match |
| `audit/package-info.java` **(existente, sem alteração esperada)** | module decl | — | `identity/package-info.java:1-4` | exata |

### Backend — contrato de erro

| Arquivo | Papel | Fluxo | Análogo | Qualidade |
|---|---|---|---|---|
| `platform/api/ApiErrorCode.java` **(modificado)** | enum de contrato | — | ele mesmo — `:13-43` e `fromStatus` `:79-87` | própria |
| `platform/api/ProblemDetailsHandler.java` **(modificado)** | exception handler | request-response | ele mesmo | própria |

### Backend — testes

| Arquivo | Papel | Fluxo | Análogo | Qualidade |
|---|---|---|---|---|
| `…/ProfilePersistenceIT`, `UfuEnrollmentUniquenessIT`, `UfuEnrollmentRotationIT`, `UfuEnrollmentConcurrencyIT`, `AuditImmutabilityIT` | IT PostgreSQL | CRUD | `platform/persistence/FlywayMigrationIT.java`, `AuthoritativeStorageIT.java` | exata |
| `…/RoleChangeSessionTerminationIT` | IT Redis | event-driven | `platform/session/RedisSessionIT.java:137` (`GenericContainer` + `DockerImageName` fixado) | exata |
| `…/SelfRegistrationKeycloakIT`, `VerifyEmailFlowKeycloakIT`, `PasswordResetSessionTerminationIT` | IT Keycloak | request-response | `platform/session/OidcRevocationKeycloakIT.java:71-85` | exata |
| `…/MatriculaHmacTest`, `RoleGuardTest`, `EnrollmentEligibilityTest`, `RoleScopeTest` | unit | transform | `platform/session/SessionPolicyTest.java`, `platform/security/OidcTechnicalAuthorityMapperTest.java` | exata |
| `…/HmacKeyFailClosedTest` | unit de contexto | bootstrap | `platform/EnvironmentConfigurationTest.java` | exata |
| `…/ProfileControllerIT`, `RoleGrantControllerIT`, `OperationalPanelIT`, `AuditEventIT` | IT MockMvc | request-response | `platform/diagnostic/DiagnosticControllerIT.java`, `platform/session/SessionControllerIT.java`, `platform/api/ProblemDetailsIT.java` | exata |
| Regra ArchUnit de privacidade (matrícula/HMAC fora de logger e DTO) | test arch | estático | `ModuleDependencyRulesTest.java`, `ModulithArchitectureTests.java` | exata |

### Frontend — sete superfícies (S1–S7)

Toda superfície copia a **fatia de 7 arquivos** de `features/diagnostic/`.

| Superfície / rota | Arquivos | Análogo | Qualidade |
|---|---|---|---|
| S1 `/conta/perfil/completar` | `features/profile-gate/profile-gate-page.component.{ts,html,scss,spec.ts}` | `features/diagnostic/diagnostic-page.component.*` | exata |
| S2 `/conta/perfil` | `features/profile/` (7 arquivos) | `features/diagnostic/` completo | exata |
| S3 `/conta/matricula` | `features/enrollment/` (7 arquivos) | `features/diagnostic/` completo | exata |
| S4 `/operacao/painel` | `features/operations-panel/` (7 arquivos) | `features/diagnostic/` completo — **é literalmente um painel de cartões agregados** | exata |
| S5 `/operacao/papeis` | `features/role-admin/` (7 arquivos) | `features/diagnostic/` completo | exata |
| S6 `/operacao/matriculas/corrigir` | `features/enrollment-admin/` (7 arquivos) | `features/diagnostic/` completo | exata |
| S7 cabeçalho e sessão | `layout/authenticated-layout.component.*` **(modificado)** | ele mesmo — `:13-31` | própria |
| Guarda de perfil incompleto | `core/profile/profile-completion.guard.ts` | **sem análogo** — não existe `CanActivate` no repositório | ausente |
| `app.routes.production.ts` **(modificado)** | route config | — | `app.routes.development.ts:4-25` (forma de `loadComponent` + layout pai) | exata |

### Scripts Node — validadores

| Arquivo (modificado) | Papel | Análogo / referência |
|---|---|---|
| `scripts/validate-keycloak-realm.mjs` (`:91`, `:93`) + `.test.mjs` | validator fail-closed | ele mesmo; contrato-alvo em `02-RESEARCH.md` §1 *"Contrato que o validador deve passar a ter"* |
| `scripts/validate-runtime-prerequisites.mjs` (`:973`, `:1298-1307`) + `.test.mjs` (`:878`, `:1029`) | validator fail-closed | ele mesmo |
| `scripts/validate-angular-route-boundary.mjs` (`:1378-1406`) + `.test.mjs` | validator fail-closed | ele mesmo; **preservar `PRODUCTION_TRACER_FORBIDDEN` intacto** |
| `scripts/validate-keycloak-theme.mjs` | validator | ele mesmo (páginas novas `register`, `login-verify-email`) |

### Infra / contrato

| Arquivo (modificado) | Papel | Nota |
|---|---|---|
| `infra/keycloak/realm-betufu.json` | config | `registrationAllowed: true`, `verifyEmail: true`, e `users[0].emailVerified: true` no mesmo bloco |
| `backend/openapi/betufu.yaml` | contrato | 4 paths hoje; endpoints novos **no mesmo plano** que o código |
| `frontend/src/app/api/generated/**` | **gerado** | *machine-owned*, 20 arquivos, digest governado — nunca editar à mão |

---

## Atribuições de padrão (excertos concretos)

### Migrations `V3+` — análogo `V1__create_local_identity.sql`

**Gabarito integral** (`backend/src/main/resources/db/migration/V1__create_local_identity.sql:1-21`):

```sql
CREATE TABLE public.local_identity (
    identity_id uuid NOT NULL DEFAULT gen_random_uuid(),
    issuer varchar(2048) COLLATE "C" NOT NULL,
    subject varchar(255) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_local_identity PRIMARY KEY (identity_id),
    CONSTRAINT uq_local_identity_issuer_subject UNIQUE (issuer, subject),
    CONSTRAINT ck_local_identity_issuer_not_empty CHECK (issuer <> ''),
    CONSTRAINT ck_local_identity_subject_not_empty CHECK (subject <> ''),
    CONSTRAINT ck_local_identity_issuer_octets CHECK (octet_length(issuer) <= 2048),
    CONSTRAINT ck_local_identity_subject_octets CHECK (octet_length(subject) <= 255),
    CONSTRAINT ck_local_identity_timestamp_order CHECK (updated_at >= created_at)
);
```

**Estilo de constraint em bloco separado** (`V2__add_platform_guards.sql:7-23`) — quando a lista é
longa, cada `CONSTRAINT` ocupa duas linhas, nome na primeira:

```sql
    CONSTRAINT pk_platform_guard
        PRIMARY KEY (guard_id),

    CONSTRAINT uq_platform_guard_key
        UNIQUE (guard_key),

    CONSTRAINT ck_platform_guard_key_not_empty
        CHECK (guard_key <> ''),
```

**Semente idempotente na migration** (`V2__add_platform_guards.sql:26-27`):

```sql
INSERT INTO public.platform_guard (guard_key, description)
VALUES ('PLATFORM_SCHEMA_AUTHORITATIVE', 'PostgreSQL 18 authoritative schema guarded by Flyway migrations and validated DDL');
```

> **Limite do análogo.** `V1`/`V2` não têm índice único parcial nem trigger. `CREATE UNIQUE INDEX
> … WHERE status = 'ATIVO'` (matrícula, papéis) e o trigger de imutabilidade de `audit_event` são
> construções **novas** neste repositório — seguir `02-RESEARCH.md` §5 e §7, e note que
> `ddl-auto: validate` **não** valida índices, então cada índice parcial precisa de um IT que o
> exercite.

---

### Entidade JPA — análogo `LocalIdentity.java`

`backend/src/main/java/br/com/betufu/identity/internal/LocalIdentity.java:15-48`:

```java
@Entity
@Immutable
@Access(AccessType.FIELD)
@Table(
        name = "local_identity",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_local_identity_issuer_subject",
                columnNames = {"issuer", "subject"}))
public class LocalIdentity {

    @Id
    @Column(name = "identity_id", nullable = false, updatable = false)
    private UUID identityId;

    @Column(name = "issuer", nullable = false, updatable = false, length = 2048)
    private String issuer;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamptz(6)")
    private Instant createdAt;

    protected LocalIdentity() {}
```

Pontos que o executor **deve** replicar: `@Access(AccessType.FIELD)`; `schema = "public"`;
`@UniqueConstraint(name = …)` com o mesmo nome da migration; `columnDefinition = "timestamptz(6)"`;
construtor protegido sem argumentos; apenas getters, sem setters.

`@Immutable` + `updatable = false` é o padrão **obrigatório** para `AuditEvent` (`AUDT-02`, camada 1).
Para `BetufuProfile` e `OperationalRoleGrant`, que mudam, **remover `@Immutable`** e manter
`updatable = false` só nas colunas realmente imutáveis (`created_at`, `identity_id`).

---

### Repository — análogo `LocalIdentityRepository.java`

`identity/internal/LocalIdentityRepository.java:11-31` (integral):

```java
public interface LocalIdentityRepository extends Repository<LocalIdentity, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO public.local_identity (issuer, subject)
                    VALUES (:issuer, :subject)
                    ON CONFLICT (issuer, subject) DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(@Param("issuer") String issuer, @Param("subject") String subject);

    @Query("""
            SELECT identity
            FROM LocalIdentity identity
            WHERE identity.issuer = :issuer
              AND identity.subject = :subject
            """)
    Optional<LocalIdentity> findByNaturalKey(
            @Param("issuer") String issuer, @Param("subject") String subject);
}
```

Convenções: estende `org.springframework.data.repository.Repository` (interface **mínima**, não
`JpaRepository` — não expõe superfície que ninguém usa); text blocks para toda query; `@Param`
nomeado sempre; `nativeQuery = true` só quando o SQL precisa de recurso do PostgreSQL
(`ON CONFLICT`); JPQL no resto.

Este é o análogo direto do "insere ou não faz nada" que `AdminSeedInitializer` (D-47) precisa, e o
`ON CONFLICT DO NOTHING` é a mesma família do **árbitro de concorrência** de `VERI-07`
(`02-RESEARCH.md` §3).

---

### Service — análogo `LocalIdentityService.java`

`identity/internal/LocalIdentityService.java:8-25` (integral):

```java
@Service
@Profile("runtime | integrated")
public class LocalIdentityService {

    private final LocalIdentityRepository repository;

    public LocalIdentityService(LocalIdentityRepository repository) {
        this.repository = repository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LocalIdentity persistCurrentIdentity(String issuer, String subject) {
        repository.insertIfAbsent(issuer, subject);

        return repository.findByNaturalKey(issuer, subject)
                .orElseThrow(() -> new IllegalStateException(
                        "Identity insert completed without a readable row"));
    }
}
```

Padrão: `@Profile("runtime | integrated")` em todo service persistido; injeção por construtor com
campo `final`; `@Transactional` com `isolation` **explícito**; falha com `IllegalStateException` e
mensagem que **não contém dado do usuário** — regra que vale em dobro para `EnrollmentService`
(`PRIV-01`: a mensagem nunca pode carregar a matrícula).

Para `RoleGrantService` (D-48b, "último administrador geral") o isolamento `READ_COMMITTED` **não
basta**: `02-RESEARCH.md` §5 recomenda `SELECT … FOR UPDATE` sobre as linhas vigentes de
`ADMINISTRADOR_GERAL` dentro da mesma transação.

---

### Controller — análogo `LocalIdentityController.java`

`identity/internal/LocalIdentityController.java:12-47`:

```java
@RestController
@Profile("runtime | integrated")
public class LocalIdentityController {

    private final LocalIdentityService service;

    public LocalIdentityController(LocalIdentityService service) {
        this.service = service;
    }

    @PutMapping(
            path = "/api/v1/tracer/identity/current",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CurrentIdentityResponse> putCurrentIdentity(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        return OidcClaimsProjection.from(oidcUser)
                .map(this::persist)
                .orElseGet(LocalIdentityController::unauthorized);
    }

    private static ResponseEntity<CurrentIdentityResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

Padrão: sem `@RequestMapping` de classe — o path completo em cada método; `produces` explícito;
`@AuthenticationPrincipal(errorOnInvalidType = false) OidcUser`; a projeção de claims sai por
`OidcClaimsProjection.from(...)` devolvendo `Optional`, nunca leitura direta de `getClaim`;
`ResponseEntity` tipado; helpers privados estáticos para os ramos de erro.

**Variante de constantes de path e Javadoc de decisão** — `platform/diagnostic/DiagnosticController.java:32-53`:

```java
@RestController
@Profile("dev | homolog")
public class DiagnosticController {

    static final String DIAGNOSTIC_PATH = "/api/v1/diagnostic";

    @GetMapping(path = DIAGNOSTIC_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DiagnosticResponse> getDiagnostic() {
        return ResponseEntity.ok(diagnosticService.aggregate(dependencyProbes.probeAll()));
    }
}
```

E o Javadoc `:11-31` é o gabarito de **como documentar a decisão de autorização** — em particular
`:21-26`, que fixa a regra: *"A exigencia de `ROLE_TECHNICAL` vive na cadeia de seguranca unica, em
`SecurityConfiguration` … Uma segunda `SecurityFilterChain` teria precedencia dependente de ordem de
bean, que nao e auditavel por leitura."* **As regras de papel da Fase 2 entram na mesma cadeia
única.** Não criar `SecurityFilterChain` nova.

Também `:44-47` mostra o padrão de `Objects.requireNonNull(x, "x must not be null")` no construtor.

---

### DTO de resposta — análogo `CurrentIdentityResponse.java`

`identity/internal/CurrentIdentityResponse.java:6-10` (integral):

```java
public record CurrentIdentityResponse(
        UUID identityId,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
```

`record`, campos escalares, sem lógica. Para `PublicProfileResponse` (`PROF-02`) o campo-a-campo é
uma **allowlist**: apelido, equipe favorita, identificador público, conquistas. Nada de e-mail,
matrícula ou HMAC — e a regra ArchUnit de privacidade deve provar isso.

> **`identityId` já é o "Identificador público" da tela S2.** É PK `gen_random_uuid()`
> (`V1:2`), aleatório, não sequencial. **Não criar uma segunda coluna de identificador público.**

---

### Contrato de erro — `ApiErrorCode` e `FieldViolation`

`platform/api/ApiErrorCode.java:13-20` — forma de cada constante (código, título, detalhe, ação),
tudo em pt-BR:

```java
public enum ApiErrorCode {

    VALIDATION_FAILED(
            "BETUFU-400-VALIDATION",
            "Erro de validação",
            "Um ou mais campos estão inválidos.",
            "Corrija os campos indicados e tente novamente."),
```

Javadoc `:3-11` fixa o contrato: *"Codes must never be renamed or removed once published — they
become part of the API contract per D-14."* Os códigos novos da Fase 2 (matrícula duplicada, limite
de tentativas, autoconcessão bloqueada, último administrador) seguem o mesmo formato
`BETUFU-<status>-<SLUG>` e os textos **já aprovados** no `02-UI-SPEC.md` §*Recusas de negócio* —
copiar, não reescrever. `fromStatus(int)` `:79-87` mapeia hoje só 400/405/406/415; 401/403/404/409/429
caem em `INTERNAL_ERROR` e provavelmente precisam de entrada nova.

`platform/api/FieldViolation.java:11-23` — **padrão de privacidade já resolvido**, e a nota que o
executor precisa ler antes de tocar em validação:

```java
/**
 * <p>The rejected value is deliberately absent. The frontend already holds the submitted
 * form data and can match fields by name; echoing the value back would risk leaking
 * sensitive input (e.g., a password field's content) in the error response.
 */
public record FieldViolation(String field, String message) implements Comparable<FieldViolation> {

    public FieldViolation {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    @Override
    public int compareTo(FieldViolation other) {
        return this.field.compareTo(other.field);
    }
}
```

**Medido, não suposto** (`platform/api/ProblemDetailsHandler.java:70-74`): o handler constrói cada
`FieldViolation` a partir de `fe.getField()` + `sanitize(fe.getDefaultMessage() …)`. Ele **nunca**
toca `getRejectedValue()`/`getInvalidValue()`, e ainda passa a mensagem pelo `sanitize`.

```java
        List<FieldViolation> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(
                        …,
                        sanitize(fe.getDefaultMessage() != null
                                ? fe.getDefaultMessage()
```

Isto **já fecha** o vetor de vazamento nº 2 do `02-RESEARCH.md` §6. **Risco residual, único e
específico:** a mensagem que chega em `getDefaultMessage()` é o *template* da anotação de validação
já interpolado; um `@Pattern(message = "…${validatedValue}…")` colocaria a matrícula ali. Regra para
o plano: **nenhuma anotação de validação da matrícula pode usar `${validatedValue}` nem `{value}`**,
e isso vira teste, não convenção.

---

### Falha fechada de propriedade — análogo `SessionSecurityConfiguration`

`platform/session/SessionSecurityConfiguration.java:135-139` (padrão `requireProperty`): lança
`IllegalStateException(contractName + " drifted from the approved contract")` durante
`postProcessAfterInitialization`, o que faz o **contexto não subir**. É exatamente o comportamento
que `D-33` pede para `BETUFU_MATRICULA_HMAC_KEY_*` (ausente, curta ou não decodificável ⇒ não sobe),
e o análogo direto de `HmacKeyFailClosedTest`.

Anti-padrão correspondente, já proibido por validador: `@Value("${VAR:default}")`. Ver a regra
`DATASOURCE_PLACEHOLDER` em `scripts/validate-runtime-prerequisites.mjs:961-966` — a chave HMAC
merece uma regra da mesma família.

---

### Autoridades — análogo `OidcTechnicalAuthorityMapper`

`platform/security/OidcTechnicalAuthorityMapper.java:33-87`: allowlist **fechada** (só `TECHNICAL`),
leitura da claim `betufu_roles` exigindo `Collection` de `String`, comparação por `equals`, e o
javadoc `:33-35`: *"As autoridades originais do provedor são sempre preservadas: este mapper
acrescenta, nunca substitui."*

Os papéis da Fase 2 vêm do PostgreSQL (`D-20`, `D-46`), não do Keycloak — mas a **forma** (allowlist
fechada, acrescenta e nunca substitui, comparação exata) é a que se copia.

Aviso vinculante em `SecurityConfiguration.java:252-266`: *"O mapper de autoridade e fiado nos DOIS
ramos. Ligar apenas no ramo completo deixaria os contextos focais de teste sem o handoff, e um teste
que passasse ali estaria provando um pipeline diferente do de producao."* **A mesma disciplina vale
para as autoridades novas.**

---

### Declaração de módulo Modulith — análogo `identity/package-info.java`

`identity/package-info.java:1-4` (integral):

```java
@ApplicationModule(displayName = "Identidade")
package br.com.betufu.identity;

import org.springframework.modulith.ApplicationModule;
```

`verification` e `audit` **já existem** com a mesma forma. Código novo entra em subpacote `internal`
(como `identity.internal`), que é o que mantém `ModuleDependencyRulesTest` verde.

---

### Fatia Angular — análogo `features/diagnostic/`

Sete arquivos, e cada superfície S1–S6 replica os sete.

**`*.models.ts`** (`features/diagnostic/diagnostic.models.ts:6-54`) — união literal de estados,
`Object.freeze` em constantes, tudo `readonly`:

```typescript
export type DiagnosticStateStatus =
  | 'initial' | 'loading' | 'empty' | 'populated' | 'partial' | 'error';

export const CANONICAL_COMPONENT_NAMES = Object.freeze([...] as const);

export interface DiagnosticState {
  readonly status: DiagnosticStateStatus;
  readonly snapshot: DiagnosticSnapshotViewModel | null;
  readonly previousSnapshot: DiagnosticSnapshotViewModel | null;
  readonly errorMessage: string | null;
  readonly errorCorrelationId: string | null;
  readonly isRetrying: boolean;
}
```

**`*-api.adapter.ts`** (`features/diagnostic/diagnostic-api.adapter.ts:14-45`) — consome o **cliente
gerado** e falha fechada com erros prefixados `CONTRATO_`:

```typescript
@Injectable({ providedIn: 'root' })
export class DiagnosticApiAdapter {
  private readonly diagnosticService = inject(DiagnosticService);

  fetchDiagnostic(): Observable<DiagnosticSnapshotViewModel | null> {
    return defer(() => this.diagnosticService.getDiagnostic()).pipe(
      map((response) => this.adapt(response)),
    );
  }

  adapt(response: DiagnosticResponse | null | undefined): DiagnosticSnapshotViewModel | null {
    if (!response || !response.components || response.components.length === 0) {
      return null;
    }
    if (components.length !== CANONICAL_COMPONENT_NAMES.length) {
      throw new Error(`CONTRATO_COMPONENTES_INVALIDO: …`);
    }
```

Os imports vêm sempre de `'../../api/generated/api/<x>.service'` e `'../../api/generated/model/<y>'`
(`:3-5`). **Nunca `HttpClient` direto.**

**`*.store.ts`** (`features/diagnostic/diagnostic.store.ts:16-36, 45-74, 122-144`) — signals privados
com seletores `computed`, retry limitado, e `sanitizeError`:

```typescript
@Injectable({ providedIn: 'root' })
export class DiagnosticStore {
  private readonly adapter = inject(DiagnosticApiAdapter);
  private readonly state = signal<DiagnosticState>(INITIAL_STATE);

  readonly status = computed(() => this.state().status);
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly isError = computed(() => this.state().status === 'error');
```

```typescript
      const adaptedSnapshot = await firstValueFrom(
        this.adapter.fetchDiagnostic().pipe(
          retry({
            count: 2,
            delay: (_error, retryCount) => {
              this.state.update((s) => ({ ...s, isRetrying: true }));
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );
```

```typescript
  private sanitizeError(error: unknown): { message: string; correlationId: string } {
    let message = 'Algo não funcionou como esperado. Tente novamente.';
    let correlationId = 'N/D';

    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        message = 'Sua sessão não permite continuar. Entre novamente.';
      } else if (error.status === 403) {
        message = 'Você não tem acesso a este diagnóstico.';
      }
      if (error.error && typeof error.error === 'object') {
        const errorBody = error.error as Record<string, unknown>;
        if (typeof errorBody['correlationId'] === 'string') {
          correlationId = errorBody['correlationId'];
        }
      }
    }
    return { message, correlationId };
  }
```

> **Nota para a Fase 2:** os textos aqui são do 01-UI-SPEC. Nas telas novas, os textos vêm do
> `02-UI-SPEC.md` §*Recusas de negócio* / *Erros de transporte*, e o `correlationId` extraído é o
> "Código de referência" que o UI-SPEC manda exibir e copiar.

**`*-page.component.ts`** (`features/diagnostic/diagnostic-page.component.ts:5-24, 26-50`) —
`standalone: true`, store injetada e **pública** (o template lê os signals direto), carga no
`ngOnInit`, e o helper de copiar com fallback acessível:

```typescript
@Component({
  selector: 'app-diagnostic-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './diagnostic-page.component.html',
  styleUrl: './diagnostic-page.component.scss',
})
export class DiagnosticPageComponent implements OnInit {
  readonly store = inject(DiagnosticStore);
  readonly copyStatus = signal<'idle' | 'copied' | 'error'>('idle');

  ngOnInit(): void {
    void this.store.loadDiagnostic();
  }
```

O `copyCorrelationId` `:26-50` já implementa o "código copiável" exigido por `D-14`/`02-UI-SPEC.md`,
com fallback textual quando `navigator.clipboard` não existe — **copiar essa função inteira**.

---

### Rotas — análogo `app.routes.development.ts`

`frontend/src/app/app.routes.development.ts:4-25` — layout autenticado como rota pai + filhos por
`loadComponent`:

```typescript
export const BUILD_SPECIFIC_ROUTES: Routes = [
  {
    path: '',
    component: AuthenticatedLayoutComponent,
    children: [
      {
        path: 'diagnostic',
        loadComponent: () =>
          import('./features/diagnostic/diagnostic-page.component').then(
            ({ DiagnosticPageComponent }) => DiagnosticPageComponent,
          ),
      },
    ],
  },
];
```

É esta a forma que `app.routes.production.ts` passa a ter, com os filhos `conta/perfil/completar`,
`conta/perfil`, `conta/matricula`, `operacao/painel`, `operacao/papeis`,
`operacao/matriculas/corrigir` — e **sem** `tracer` nem `diagnostic`, que
`PRODUCTION_TRACER_FORBIDDEN` continua proibindo.

`app.routes.ts:1-5` (integral) não muda: `export const APP_ROUTES: Routes = [...BUILD_SPECIFIC_ROUTES];`.

---

### Layout e sessão — análogo `authenticated-layout.component.ts`

`frontend/src/app/layout/authenticated-layout.component.ts:6-31`:

```typescript
@Component({
  selector: 'app-authenticated-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet],
  templateUrl: './authenticated-layout.component.html',
  styleUrl: './authenticated-layout.component.scss',
})
export class AuthenticatedLayoutComponent implements OnInit {
  readonly sessionStore = inject(SessionStore);

  ngOnInit(): void {
    void this.sessionStore.loadSession();
  }

  onLogin(): void {
    window.location.href = '/oauth2/authorization/keycloak';
  }
}
```

`onLogout()` `:20-26` valida o `endSessionUrl` por prefixo (`http://`, `https://`, `/`) antes de
navegar — **é a guarda de open-redirect e deve ser preservada**. S7 evolui este componente; o link
para o autocadastro do Keycloak segue o mesmo formato de `window.location.href` absoluto.

---

## Padrões compartilhados

### 1. Ausência material em vez de 403
**Fonte:** `platform/diagnostic/DiagnosticController.java:14-19`
**Aplicar a:** qualquer superfície administrativa que não deva existir em produção.
Um endpoint que responde 403 revela que existe; um não registrado não revela nada. Prova-se
inspecionando o **contexto**, não o status HTTP.

### 2. Cadeia de segurança única
**Fonte:** `platform/security/SecurityConfiguration.java:216-266`
**Aplicar a:** todas as regras de papel da Fase 2.
Ordem atual: `permitAll` explícitos → `"/api/v1/**"` autenticado → `anyRequest().denyAll()`. Os
matchers de papel entram **antes** de `/api/v1/**`, no mesmo `SecurityFilterChain`. Nunca uma
segunda chain.

### 3. Falha fechada no bootstrap
**Fonte:** `platform/session/SessionSecurityConfiguration.java:135-139`
**Aplicar a:** chave HMAC (`D-33`), semente do administrador (`D-47`).

### 4. Privacidade estrutural, não por regex
**Fonte:** `platform/observability/LogSanitizer.java:38-61` (os cinco padrões) e
`platform/api/FieldViolation.java:11-13`
**Aplicar a:** todo caminho da matrícula.
Medido: **nenhum** dos cinco padrões alcança matrícula ou HMAC, e acrescentar um sexto não resolve.
A garantia é estrutural: valor cru só como parâmetro de método no caminho
normalizar→validar→HMAC; nunca campo de entidade, atributo de sessão, DTO, mensagem de exceção,
path ou query string. Instrumento recomendado: regra ArchUnit (o classpath já tem ArchUnit e o
repositório já tem `ModuleDependencyRulesTest`).

### 5. `correlationId` único
**Fonte:** `platform/observability/CorrelationIdFilter.java`, consumido em
`diagnostic.store.ts:133-138`
**Aplicar a:** todas as recusas de negócio da fase. **Não inventar um segundo identificador** — o
"Código de referência: {correlationId}" do `02-UI-SPEC.md` é este.

### 6. Testes de container sem módulo Testcontainers
**Fonte:** `platform/session/RedisSessionIT.java:132-149`, `platform/session/OidcRevocationKeycloakIT.java:69-90`
**Aplicar a:** todos os ITs novos de Redis e Keycloak.
Não há módulo Testcontainers de Redis nem de Keycloak no `pom.xml`; usa-se `GenericContainer` com
`DockerImageName` fixado por digest. **Não adicionar dependência** (`02-RESEARCH.md` §Standard Stack).

Fixture Redis (`RedisSessionIT.java:132-149`) — note `withImagePullPolicy(imageName -> false)`, que
torna o teste hermético, e o `@DynamicPropertySource` que injeta as variáveis `BETUFU_*`:

```java
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withImagePullPolicy(imageName -> false)
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("BETUFU_REDIS_HOST", REDIS::getHost);
        registry.add("BETUFU_REDIS_PORT", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("BETUFU_REDIS_PASSWORD", () -> REDIS_PASSWORD);
    }
```

Fixture Keycloak (`OidcRevocationKeycloakIT.java:69-90`) — o realm **e o tema** são montados no
container a partir do disco, e o wait é pelo `.well-known`:

```java
    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
                    DockerImageName.parse(KEYCLOAK_IMAGE_REFERENCE))
            .withCopyFileToContainer(
                    MountableFile.forHostPath(REALM_FILE),
                    "/opt/keycloak/data/import/betufu-local-realm.json")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(THEME_DIR),
                    "/opt/keycloak/themes/betufu")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-test-only")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forPort(8080).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));
```

**Consequência direta para a Fase 2:** este IT monta `infra/keycloak/realm-betufu.json` **real**.
Virar `verifyEmail: true` afeta-o imediatamente — é o efeito colateral medido no `02-RESEARCH.md` §1.
E os ITs de autocadastro / `VERIFY_EMAIL` já têm aqui o dirigível de formulário HTML pronto: os
`Pattern` `FORM_ACTION` e `AUTHORIZATION_CODE` de `OidcRevocationKeycloakIT.java:63-66` são
exatamente o que um teste de fluxo real precisa para percorrer a página hospedada e capturar o `code`.

### 7. Contrato OpenAPI no mesmo plano do código
**Fonte:** `backend/openapi/betufu.yaml` (4 paths), `scripts/validate-phase1.mjs:108-147`
**Aplicar a:** todo plano que acrescente endpoint.
O detector de divergência já existe e varre `backend/src/main/java`. Regenerar o cliente e atualizar
o digest aprovado é **aprovação do responsável**, não decisão do executor.

### 8. Validador fail-closed com regressão negativa que realmente muta
**Fonte:** `scripts/validate-*.mjs` + `*.test.mjs` pareados
**Aplicar a:** as cinco reformulações da `Wave 0` do `02-RESEARCH.md`.
Quando o valor pinado inverte, a regressão negativa antiga deixa de mutar. Cada regra reformulada
precisa de uma mutação **nova** que a prove — é o "prova vazia" do `CLAUDE.md`.

### 9. Mutação multilinha sobre fonte Java em checkout Windows
**Fonte:** `01-VALIDATION.md`; `core.autocrlf=true`
**Aplicar a:** toda prova de discriminação desta fase.
Usar o helper `replaceAcrossLines`, que casa `\r?\n`. Mutação multilinha ingênua fica **inerte** e
produz um PASS falso.

---

## Sem análogo no repositório

O planejador deve usar `02-RESEARCH.md` como referência primária nestes casos.

| Arquivo / construção | Papel | Fluxo | Motivo | Referência |
|---|---|---|---|---|
| `MatriculaHmac` (`javax.crypto.Mac`, `MessageDigest.isEqual`) | utility | transform criptográfico | não existe nenhum uso de HMAC no repositório | `02-RESEARCH.md` §3 |
| Índice único **parcial** (`CREATE UNIQUE INDEX … WHERE`) | migration | DDL | `V1`/`V2` só têm `CONSTRAINT … UNIQUE` | `02-RESEARCH.md` §5 |
| Trigger/rule de imutabilidade em `audit_event` | migration | DDL | nenhum trigger existe hoje | `02-RESEARCH.md` §7 |
| DTO de **entrada** com Bean Validation | DTO | request | `spring-boot-starter-validation` está no `pom.xml` mas nenhum DTO de entrada validado existe | `02-UI-SPEC.md` §Erros de campo |
| Contador de tentativas em Redis (`INCR`+`EXPIRE`) | service | rate-limit | Redis só é usado para sessão e probe | `02-RESEARCH.md` §Standard Stack |
| Guarda Angular (`CanActivate`) para perfil incompleto | guard | request-response | não existe guard no frontend | `02-UI-SPEC.md` S1 |
| Página de tema Keycloak (`register`, `login-verify-email`) | template FTL | — | o tema `betufu` só sobrescreve `footer.ftl` e CSS | `02-UI-SPEC.md` §Fronteira |
| `OidcSessionRegistry` durável / enumeração por principal | infra de sessão | event-driven | `RedisSessionRepository` **não** implementa `FindByIndexNameSessionRepository`; é o bloqueio nº 1 da fase | `02-RESEARCH.md` §2 e §4 |

---

## Riscos de padrão que o planejador deve levar para o PLAN

1. **`SessionSecurityConfiguration.java:83-98` recusa qualquer repositório que não seja
   `RedisSessionRepository`**, e `validate-runtime-prerequisites.test.mjs:878` usa
   `repository-type: indexed` como a mutação negativa. Trocar para indexado **inverte o significado
   de uma regressão existente** — não é uma linha de YAML.
2. **`ddl-auto: validate`** significa que entidade e DDL têm de casar exatamente; qualquer
   divergência derruba o contexto inteiro, não só um teste.
3. **Índice parcial não é validado pelo JPA** — precisa de IT que o exercite de verdade.
4. **Digest do cliente gerado** é aprovação do responsável. Um plano que regenere sem prever a
   aprovação fica bloqueado no final.
5. **Nenhuma dependência nova.** Se um plano propuser `npm install` ou dependência Maven, é desvio
   de escopo e exige aprovação separada (`AGENTS.md`).

---

## Metadados

**Escopo da busca por análogos:** `backend/src/main/java/br/com/betufu/**` (52 arquivos),
`backend/src/main/resources/db/migration/**` (2), `backend/src/test/java/**` (31),
`frontend/src/app/**` exceto `api/generated` (24), `scripts/*.mjs` (13).
**Arquivos lidos integralmente:** `LocalIdentity.java`, `LocalIdentityRepository.java`,
`LocalIdentityService.java`, `LocalIdentityController.java`, `CurrentIdentityResponse.java`,
`identity/package-info.java`, `verification/package-info.java`, `audit/package-info.java`,
`V1__create_local_identity.sql` (via `02-RESEARCH.md` §5), `V2__add_platform_guards.sql`,
`FieldViolation.java`, `DiagnosticController.java`, `diagnostic.models.ts`,
`diagnostic-api.adapter.ts`, `diagnostic.store.ts`, `diagnostic-page.component.ts`,
`app.routes.ts`, `app.routes.development.ts`, `authenticated-layout.component.ts` (cabeçalho).
**Arquivos citados por medição do `02-RESEARCH.md`** (não relidos aqui, para não duplicar leitura):
`SecurityConfiguration.java`, `SessionSecurityConfiguration.java`, `OidcTechnicalAuthorityMapper.java`,
`SessionInvalidationListener.java`, `OidcSessionRevocationService.java`, `LogSanitizer.java`,
`ApiErrorCode.java` (`fromStatus`), os cinco validadores Node.
**Limite material desta análise:** leitura estática. Nenhuma build, container ou suíte foi executada.
**Data:** 2026-08-20
