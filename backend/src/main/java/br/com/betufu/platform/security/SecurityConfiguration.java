package br.com.betufu.platform.security;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.session.OidcLogoutSuccessHandler;
import br.com.betufu.platform.session.SessionLifecycleService;
import br.com.betufu.platform.session.ValidActivityFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

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
     * Enforces explicit origin allowlist per environment without wildcard.
     *
     * <p>CORS preflight and cross-origin mutating requests must strictly match the configured
     * public origin. In local development this defaults to {@code http://localhost:4200}.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${betufu.web.public-origin:http://localhost:4200}") String publicOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(publicOrigin));
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name(),
                HttpMethod.HEAD.name()));
        configuration.setAllowedHeaders(List.of(
                "Content-Type", "X-XSRF-TOKEN", "X-Requested-With", "Accept", "Origin"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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

    /**
     * OIDC client wiring, like the beans above: it turns discovery metadata into the RP-initiated
     * logout URI. Declared here rather than beside the session policy because it depends on the
     * client registration, not on the session contract.
     */
    @Bean
    OidcLogoutSuccessHandler oidcLogoutSuccessHandler() {
        return new OidcLogoutSuccessHandler();
    }

    /**
     * Contribuidor de autoridade de papéis (técnico e operacionais), declarado como o único bean
     * {@code GrantedAuthoritiesMapper}. O Spring Security o instala automaticamente no provider
     * OIDC usado por {@code oauth2Login}.
     *
     * <p>Consome exclusivamente o contrato consumidor {@link OperationalIdentityLookup} pertencente
     * ao platform. A resolução pelo bean preserva a autoridade técnica e acrescenta autoridades
     * operacionais independentemente de os serviços opcionais de sessão estarem presentes.
     */
    @Bean
    BetufuRoleAuthorityContributor betufuRoleAuthorityContributor(
            ObjectProvider<OperationalIdentityLookup> identityLookupProvider) {
        return new BetufuRoleAuthorityContributor(identityLookupProvider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            CookieCsrfTokenRepository csrfTokenRepository,
            CorsConfigurationSource corsConfigurationSource,
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

        AuthenticationEntryPoint problemDetailsEntryPoint = (request, response, authException) ->
                writeProblemDetails(
                        response,
                        objectMapper,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        ApiErrorCode.UNAUTHORIZED);

        AccessDeniedHandler problemDetailsAccessDeniedHandler = (request, response, accessDeniedException) ->
                writeProblemDetails(
                        response,
                        objectMapper,
                        HttpServletResponse.SC_FORBIDDEN,
                        ApiErrorCode.FORBIDDEN);

        RequestMatcher apiOrProblemMatcher = request -> {
            String accept = request.getHeader("Accept");
            String requestedWith = request.getHeader("X-Requested-With");
            if (accept != null && (accept.contains("application/json")
                    || accept.contains("application/problem+json")
                    || accept.contains("text/plain"))) {
                return true;
            }
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return true;
            }
            String uri = request.getRequestURI();
            String servletPath = request.getServletPath();
            boolean isApiPath = (uri != null && uri.startsWith("/api/"))
                    || (servletPath != null && servletPath.startsWith("/api/"));
            return isApiPath && (accept != null && !accept.contains("text/html"));
        };

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                problemDetailsEntryPoint,
                                apiOrProblemMatcher)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak"),
                                AnyRequestMatcher.INSTANCE)
                        .accessDeniedHandler(problemDetailsAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**",
                                "/actuator/health/liveness",
                                "/ws",
                                "/ws/**")
                        .permitAll()
                        // Exact pair only. The SPA must be able to ask whether it is signed in
                        // without being redirected to the hosted IdP, so this single GET is
                        // anonymous while every other /api/v1/** request stays authenticated.
                        // Order matters: a broader matcher placed first would swallow it.
                        .requestMatchers(HttpMethod.GET, "/api/v1/session")
                        .permitAll()
                        // Diagnostico exige o papel tecnico, e o matcher precisa vir ANTES de
                        // /api/v1/** — senao aquele matcher mais largo o engoliria e bastaria
                        // estar autenticado. A autoridade so pode ser produzida pelo
                        // OidcTechnicalAuthorityMapper, a partir da claim aprovada no Gate 1.
                        //
                        // Em producao o controller nao existe (@Profile no bean), entao este
                        // matcher nao tem alvo. Mante-lo na cadeia unica em todos os profiles e
                        // defesa em profundidade: se alguem um dia registrar o mapping em prod
                        // por engano, ele ja nasce exigindo TECHNICAL em vez de apenas sessao.
                        .requestMatchers(HttpMethod.GET, "/api/v1/diagnostic")
                        .hasRole("TECHNICAL")
                        // Recovery de reset de senha exige ADMINISTRADOR_GERAL, e o matcher precisa vir
                        // ANTES de /api/v1/** — senao aquele matcher mais largo o engoliria e bastaria
                        // estar autenticado. O controller correspondente chega no Plano 02-31, entao este
                        // matcher nasce temporariamente sem alvo, exatamente como /api/v1/diagnostic acima.
                        .requestMatchers("/api/v1/operations/password-reset-events/**")
                        .hasRole("ADMINISTRADOR_GERAL")
                        // Painel operacional permite qualquer um dos três papéis operacionais (ADMINISTRADOR_GERAL,
                        // ADMINISTRADOR_COMPETICAO, MODERADOR). A autorização e a filtragem de blocos são decididas
                        // no serviço do painel, garantindo que moderadores recebam resposta com estado vazio em vez de 403.
                        // O matcher precede /api/v1/** na cadeia única para barrar usuários sem papel operacional.
                        .requestMatchers(HttpMethod.GET, "/api/v1/operations/panel")
                        .hasAnyRole("ADMINISTRADOR_GERAL", "ADMINISTRADOR_COMPETICAO", "MODERADOR")
                        // Administrative enrollment correction is the most invasive command in
                        // this phase. Keep the exact POST matcher before /api/v1/** so merely
                        // being authenticated can never authorize it.
                        .requestMatchers(HttpMethod.POST, "/api/v1/operations/enrollments/correction")
                        .hasRole("ADMINISTRADOR_GERAL")
                        .requestMatchers("/api/v1/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId()));

        // O Spring Security aplica automaticamente o unico GrantedAuthoritiesMapper do contexto
        // ao provider OIDC. A configuracao de sessao abaixo e opcional; ela nao governa o mapper.
        if (sessionLifecycleService != null) {
            http.addFilterAfter(validActivityFilter, AuthorizationFilter.class);
        }
        http.oauth2Login(oauth2 -> {
            if (sessionLifecycleService != null) {
                oauth2.successHandler(sessionLifecycleService);
            }
        });

        return http.build();
    }

    private static void writeProblemDetails(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            ApiErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", errorCode.title());
        body.put("status", status);
        body.put("detail", errorCode.detail());
        body.put("code", errorCode.code());
        body.put("action", errorCode.action());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
