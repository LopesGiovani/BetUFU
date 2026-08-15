package br.com.betufu.platform.security;

import br.com.betufu.platform.session.SessionLifecycleService;
import br.com.betufu.platform.session.ValidActivityFilter;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    private static final Duration REVOCATION_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REVOCATION_READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax"));
        return repository;
    }

    /**
     * Enforces the {@code userinfo_endpoint} opt-out recorded in the phase COVERAGE matrix.
     *
     * <p>Spring Security resolves an {@code OAuth2UserService<OidcUserRequest, OidcUser>} bean
     * from the context and only falls back to a default {@link OidcUserService} when none exists,
     * so declaring this bean replaces the framework default for the whole login pipeline.
     *
     * <p>The opt-out uses the framework's own {@code retrieveUserInfo} predicate rather than a
     * bespoke user service: the principal keeps being built from the validated ID token by
     * Spring Security itself, and only the UserInfo round trip is suppressed. Without this bean
     * the opt-out would rest solely on the requested scopes, and adding {@code profile} or
     * {@code email} to the registration would silently reintroduce the call.
     */
    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService userService = new OidcUserService();
        userService.setRetrieveUserInfo(userRequest -> false);
        return userService;
    }

    /**
     * The revocation client is infrastructure, not a scanned component: it needs an explicitly
     * built {@link RestClient}, and declaring it here keeps it beside the rest of the OIDC
     * client wiring. {@code OidcSessionRevocationService} depends on it.
     *
     * <p>Both timeouts are set explicitly. {@code RestClient.builder().build()} defaults to a
     * {@link JdkClientHttpRequestFactory} with no read timeout over a JDK {@code HttpClient} with
     * no connect timeout, so an unresponsive revocation endpoint would block the calling thread
     * forever. That would strand the single-flight guard in
     * {@code OidcSessionRevocationService}: the retry window would expire without the caller ever
     * seeing a failure, turning a failed revocation into a silent one. The values are kept far
     * below the pending TTL so a hung provider always surfaces as a remote failure in time to be
     * retried.
     */
    @Bean
    OidcRevocationClient oidcRevocationClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(REVOCATION_CONNECT_TIMEOUT)
                        .build());
        requestFactory.setReadTimeout(REVOCATION_READ_TIMEOUT);

        return new OidcRevocationClient(
                RestClient.builder().requestFactory(requestFactory).build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            ObjectProvider<SessionLifecycleService> sessionLifecycleServiceProvider,
            ObjectProvider<ValidActivityFilter> validActivityFilterProvider) throws Exception {
        SessionLifecycleService sessionLifecycleService =
                sessionLifecycleServiceProvider.getIfAvailable();
        ValidActivityFilter validActivityFilter =
                validActivityFilterProvider.getIfAvailable();
        if ((sessionLifecycleService == null) != (validActivityFilter == null)) {
            throw new IllegalStateException(
                    "Session lifecycle and valid-activity filter must be configured together");
        }

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**",
                                "/actuator/health/liveness")
                        .permitAll()
                        .requestMatchers("/api/v1/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId()));

        if (sessionLifecycleService == null) {
            http.oauth2Login(Customizer.withDefaults());
        } else {
            http
                    .addFilterAfter(validActivityFilter, AuthorizationFilter.class)
                    .oauth2Login(oauth2 -> oauth2
                            .successHandler(sessionLifecycleService));
        }

        return http.build();
    }
}
