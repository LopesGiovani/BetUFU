package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.identity.internal.LocalIdentityController;
import br.com.betufu.identity.internal.LocalIdentityRepository;
import br.com.betufu.identity.internal.LocalIdentityService;
import br.com.betufu.identity.internal.OperationalRoleGrantRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ApplicationModuleTest
@ActiveProfiles("integrated")
@TestPropertySource(
    properties = {
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-identity-test",
      "BETUFU_OIDC_CLIENT_SECRET=unused-secret",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration,"
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration"
    })
@Import(IdentityModuleTest.TestIdentityInfrastructureConfiguration.class)
class IdentityModuleTest {

  // `audit` saiu desta lista no Plano 02-23, por ratificação do responsável. A lista nasceu no
  // commit 8725992 (Plano 01-23), quando o módulo de auditoria ainda não existia e era, de fato,
  // futuro. Ele foi entregue pelos Planos 02-04 e 02-21, e
  // `verification/internal/EnrollmentService`
  // já o consome sem que nenhuma guarda o impeça. Manter `audit` aqui bloquearia a trilha de
  // auditoria de concessões e recusas de papel exigida por D-30 e D-48.
  // O Plano 03-02 autoriza identity -> sports somente pelo contrato publico
  // CompetitionScopeCatalog; ApplicationModules.verify() continua vedando sports.internal.
  // O 06-04 autoriza identity -> ranking somente pela porta publica de apelido.
  private static final List<String> FUTURE_BUSINESS_MODULES =
      List.of("verification", "odds", "betting", "ledger", "settlement", "chat");

  @Autowired private ApplicationContext applicationContext;

  @Autowired(required = false)
  private LocalIdentityService identityService;

  @Autowired(required = false)
  private LocalIdentityController identityController;

  @Test
  @DisplayName(
      "Módulo identity inicializa seu contexto isolado com seus serviços e controllers internos")
  void bootstrapsIdentityModuleContextInIsolation() {
    assertThat(identityService).isNotNull();
    assertThat(identityController).isNotNull();
    assertThat(applicationContext).isNotNull();
  }

  @Test
  @DisplayName(
      "Módulo identity não possui dependências diretas não autorizadas para módulos de negócio futuros")
  void verifiesIdentityModuleBoundaryInApplicationModulesGraph() {
    ApplicationModules modules = ApplicationModules.of(br.com.betufu.BetufuApplication.class);

    assertThat(modules.getModuleByName("identity")).isPresent();
    modules
        .getModuleByName("identity")
        .ifPresent(
            module -> {
              assertThat(module.getIdentifier().toString()).isEqualTo("identity");

              List<String> targetModuleNames =
                  module.getDirectDependencies(modules).stream()
                      .map(dep -> dep.getTargetModule().getIdentifier().toString())
                      .toList();

              assertThat(targetModuleNames).doesNotContainAnyElementsOf(FUTURE_BUSINESS_MODULES);
            });
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestIdentityInfrastructureConfiguration {

    @Bean
    LocalIdentityRepository localIdentityRepository() {
      return mock(LocalIdentityRepository.class);
    }

    @Bean
    OperationalRoleGrantRepository operationalRoleGrantRepository() {
      return mock(OperationalRoleGrantRepository.class);
    }

    @Bean
    AuditRecorder auditRecorder() {
      return mock(AuditRecorder.class);
    }

    @Bean
    ProfileEnrollmentStatusLookup profileEnrollmentStatusLookup() {
      return mock(ProfileEnrollmentStatusLookup.class);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-identity-test")
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
              .clientName("BETUFU Identity Test")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
