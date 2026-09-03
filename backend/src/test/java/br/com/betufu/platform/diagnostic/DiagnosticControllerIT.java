package br.com.betufu.platform.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.betufu.platform.security.OidcTechnicalAuthorityMapper;
import br.com.betufu.platform.security.SecurityConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Prova os tres eixos que se cruzam no endpoint de diagnostico: profile, autenticacao e paridade
 * com o contrato publicado.
 *
 * <p>Os dois profiles exigem contextos distintos, entao cada um vive num {@link Nested} proprio. O
 * eixo de producao NAO assere 403: assere ausencia de bean e de mapping, porque um teste que afirma
 * apenas o status nao distingue "negado" de "inexistente" — e D-02 exige inexistente.
 *
 * <p>O contexto lista as classes explicitamente, como os demais ITs focais da fase. Sem isso o
 * profile {@code dev} ativaria o grupo {@code runtime} e arrastaria a pilha de identidade, cujo
 * repositorio JPA nao existe sem banco.
 */
class DiagnosticControllerIT {

  private static final String DIAGNOSTIC_PATH = "/api/v1/diagnostic";
  private static final String ROLES_CLAIM = "betufu_roles";
  private static final ObjectMapper JSON = new ObjectMapper();

  /** O mapper real da Task 1, para que o IT prove o handoff e nao apenas a regra. */
  private static final OidcTechnicalAuthorityMapper MAPPER = new OidcTechnicalAuthorityMapper();

  private static final String[] CONTEXT_PROPERTIES = {
    "spring.docker.compose.enabled=false",
    "management.endpoints.web.exposure.include=health",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
        + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
        + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
        + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
    "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
    "BETUFU_OIDC_CLIENT_ID=betufu-bff",
    "BETUFU_OIDC_CLIENT_SECRET=unused"
  };

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-bff")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri("https://idp.invalid")
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Diagnostic IT")
              .build());
    }
  }

  /**
   * Constroi o principal fazendo a claim passar pelo MAPPER REAL da Task 1.
   *
   * <p>{@code oidcLogin()} injeta authorities diretamente e nao executa o pipeline de autenticacao,
   * entao um teste que apenas fornecesse {@code ROLE_TECHNICAL} pronto provaria a regra de
   * autorizacao e nao o handoff. Aqui a claim bruta atravessa {@link OidcTechnicalAuthorityMapper}
   * exatamente como no provider, e o resultado alimenta o principal — de modo que 200 e 403 sao
   * consequencia da claim, nao da fixture.
   */
  private static RequestPostProcessor principalWithRoles(Object rolesClaim) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put(IdTokenClaimNames.ISS, "https://idp.invalid");
    claims.put(IdTokenClaimNames.SUB, "technical");
    if (rolesClaim != null) {
      claims.put(ROLES_CLAIM, rolesClaim);
    }

    OidcIdToken idToken =
        new OidcIdToken("token-de-teste", Instant.EPOCH, Instant.EPOCH.plusSeconds(300), claims);
    Collection<? extends GrantedAuthority> mapped =
        MAPPER.mapAuthorities(
            Set.of(
                new OidcUserAuthority(idToken, null), new SimpleGrantedAuthority("SCOPE_openid")));

    return oidcLogin()
        .idToken(token -> claims.forEach(token::claim))
        .authorities(List.copyOf(mapped));
  }

  /**
   * Lista plana de todos os paths registrados no {@code RequestMappingHandlerMapping}.
   *
   * <p>E esta a fonte que distingue "negado" de "inexistente": um endpoint que responde 403 aparece
   * aqui; um que nao foi registrado, nao. E o equivalente Spring da inspecao de bundle que o Plano
   * 01-21 usou para provar a ausencia do tracer em production.
   */
  /** Requisicao de API: sem Accept nao-HTML o entry point redireciona ao IdP com 302. */
  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      apiGet() {
    return get(DIAGNOSTIC_PATH).accept(MediaType.APPLICATION_JSON);
  }

  private static List<String> mappedPaths(ApplicationContext context) {
    // Ha dois beans deste tipo: o do MVC e o do actuator. Buscar por tipo falha com
    // NoUniqueBeanDefinition, entao o nome canonico do MVC e usado.
    RequestMappingHandlerMapping mapping =
        context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

    return mapping.getHandlerMethods().keySet().stream()
        .filter(info -> info.getPathPatternsCondition() != null)
        .flatMap(info -> info.getPathPatternsCondition().getPatternValues().stream())
        .toList();
  }

  // -----------------------------------------------------------------------
  // Eixo dev: o endpoint existe e e restrito
  // -----------------------------------------------------------------------

  @Nested
  @SpringBootTest(
      classes = {
        SecurityConfiguration.class,
        DiagnosticController.class,
        DiagnosticService.class,
        PlatformDependencyProbes.class,
        DiagnosticControllerIT.TestApplication.class
      },
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      properties = {
        "spring.docker.compose.enabled=false",
        "management.endpoints.web.exposure.include=health",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
            + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
            + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
            + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
            + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
            + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
        "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
        "BETUFU_OIDC_CLIENT_ID=betufu-bff",
        "BETUFU_OIDC_CLIENT_SECRET=unused"
      })
  @ActiveProfiles("dev")
  class DevProfile {

    @Autowired private WebApplicationContext applicationContext;

    private MockMvc mockMvc() {
      return MockMvcBuilders.webAppContextSetup(applicationContext)
          .apply(SecurityMockMvcConfigurers.springSecurity())
          .build();
    }

    @Test
    @DisplayName("o bean e o mapping existem em dev")
    void beanAndMappingExistInDev() {
      assertThat(applicationContext.getBeanNamesForType(DiagnosticController.class)).hasSize(1);
      assertThat(mappedPaths(applicationContext)).contains(DIAGNOSTIC_PATH);
    }

    @Test
    @DisplayName("claim tecnica valida recebe 200 com o contrato do 01-13")
    void validTechnicalClaimGetsDiagnostic() throws Exception {
      MvcResult result =
          mockMvc().perform(apiGet().with(principalWithRoles(List.of("TECHNICAL")))).andReturn();

      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
      assertThat(body.path("status").asText()).isIn("UP", "DEGRADED", "DOWN");
      assertThat(body.path("components").isArray()).isTrue();
      assertThat(body.path("components").size()).isEqualTo(6);
      for (JsonNode component : body.path("components")) {
        assertThat(component.path("status").asText()).isIn("UP", "DEGRADED", "DOWN");
      }
      assertThat(body.has("correlationId")).isTrue();
    }

    @Test
    @DisplayName("anonimo recebe 401")
    void anonymousGets401() throws Exception {
      assertThat(mockMvc().perform(apiGet()).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("autenticado sem a claim recebe 403 pelo handoff real")
    void authenticatedWithoutClaimGets403() throws Exception {
      assertThat(
              mockMvc()
                  .perform(apiGet().with(principalWithRoles(null)))
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .isEqualTo(403);
    }

    @Test
    @DisplayName("papel inesperado recebe 403, inclusive nas formas que enganam substring")
    void unexpectedRolesGet403() throws Exception {
      for (Object claim :
          List.of(
              List.of("ADMIN"),
              List.of("NOT_TECHNICAL"),
              List.of("TECHNICAL_ADMIN"),
              List.of("technical"),
              List.of(),
              "TECHNICAL")) {
        assertThat(
                mockMvc()
                    .perform(apiGet().with(principalWithRoles(claim)))
                    .andReturn()
                    .getResponse()
                    .getStatus())
            .as("claim %s", claim)
            .isEqualTo(403);
      }
    }

    @Test
    @DisplayName("a resposta nao reexpoe nada alem do DiagnosticResponse do 01-13")
    void responseDoesNotLeakBeyondProjection() throws Exception {
      String body =
          mockMvc()
              .perform(apiGet().with(principalWithRoles(List.of("TECHNICAL"))))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(body)
          .doesNotContain("password", "secret", "jdbc:", "Exception", "stackTrace")
          .doesNotContain("betufu_roles", "id_token", "access_token");
    }
  }

  // -----------------------------------------------------------------------
  // Eixo producao: ausencia material, per D-02
  // -----------------------------------------------------------------------

  @Nested
  @SpringBootTest(
      classes = {
        SecurityConfiguration.class,
        DiagnosticController.class,
        DiagnosticService.class,
        PlatformDependencyProbes.class,
        DiagnosticControllerIT.TestApplication.class
      },
      webEnvironment = SpringBootTest.WebEnvironment.MOCK,
      properties = {
        "spring.docker.compose.enabled=false",
        "management.endpoints.web.exposure.include=health",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
            + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
            + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
            + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
            + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
            + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
        "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
        "BETUFU_OIDC_CLIENT_ID=betufu-bff",
        "BETUFU_OIDC_CLIENT_SECRET=unused",
        "BETUFU_PUBLIC_ORIGIN=https://betufu.invalid",
        "BETUFU_BACKEND_ORIGIN=https://api.betufu.invalid",
        "BETUFU_TRUSTED_PROXIES=127.0.0.1/32"
      })
  @ActiveProfiles("prod")
  class ProdProfile {

    @Autowired private WebApplicationContext applicationContext;

    @Test
    @DisplayName("D-02: o bean do controller nao existe em producao")
    void controllerBeanDoesNotExist() {
      assertThat(applicationContext.getBeanNamesForType(DiagnosticController.class)).isEmpty();
    }

    @Test
    @DisplayName("D-02: o mapping nao existe em producao, e ausencia e nao 403")
    void mappingDoesNotExist() {
      assertThat(mappedPaths(applicationContext)).doesNotContain(DIAGNOSTIC_PATH);
    }
  }

  // -----------------------------------------------------------------------
  // Eixo paridade: todo mapping /api/v1 esta no contrato publicado
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("todo mapping /api/v1 do codigo tem path correspondente no betufu.yaml")
  void everyApiMappingIsDocumented() throws Exception {
    String contract = Files.readString(Path.of("openapi", "betufu.yaml"));

    for (String path :
        List.of(
            "/api/v1/tracer/identity/current",
            "/api/v1/session",
            "/api/v1/session/logout",
            DIAGNOSTIC_PATH)) {
      assertThat(contract)
          .as("path %s ausente do contrato publicado", path)
          .contains("  " + path + ":");
    }
  }
}
