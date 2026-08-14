package br.com.betufu.platform.security;

import br.com.betufu.platform.session.SessionLifecycleService;
import br.com.betufu.platform.session.ValidActivityFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax"));
        return repository;
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
