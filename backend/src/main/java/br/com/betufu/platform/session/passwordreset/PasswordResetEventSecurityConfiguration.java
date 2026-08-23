package br.com.betufu.platform.session.passwordreset;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Configuração de segurança isolada e dedicada ao endpoint interno de password-reset.
 *
 * <p>Propriedades de segurança:
 * <ul>
 *   <li>Precedência alta (@Order) para interceptar exclusivamente a rota exata de password reset;
 *   <li>Filtro dedicado de limite de tamanho de corpo antes da materialização em memória (mitigação DoS);
 *   <li>Totalmente stateless, sem criação de sessões HTTP e sem aceitar autenticação por cookie/sessão;
 *   <li>Desabilita CSRF, CORS, HTTP Basic, Form Login e OAuth2 na rota interna;
 *   <li>Autenticação restrita e exclusiva por envelope HMAC no endpoint (fail-closed sem chave);
 *   <li>Permanece inativa sem os profiles autorizados (runtime | integrated).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Profile("runtime | integrated")
@EnableConfigurationProperties(PasswordResetEventProperties.class)
public class PasswordResetEventSecurityConfiguration {

    public static final int SECURITY_FILTER_CHAIN_ORDER = Ordered.HIGHEST_PRECEDENCE + 50;

    @Bean
    @Order(SECURITY_FILTER_CHAIN_ORDER)
    SecurityFilterChain passwordResetEventSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<PasswordResetEventProperties> propertiesProvider) throws Exception {
        http
                .securityMatcher(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .addFilterBefore(
                        new PasswordResetBodySizeLimitFilter(propertiesProvider),
                        SecurityContextHolderFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(RequestCacheConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .oauth2Login(AbstractHttpConfigurer::disable)
                .oauth2Client(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PasswordResetEventEndpoint.ENDPOINT_PATH).permitAll()
                        .anyRequest().denyAll());

        return http.build();
    }

    /**
     * Filtro dedicado que aplica o limite de tamanho do corpo antes da materialização do payload pelo Spring MVC.
     */
    static final class PasswordResetBodySizeLimitFilter extends OncePerRequestFilter {

        private final ObjectProvider<PasswordResetEventProperties> propertiesProvider;

        PasswordResetBodySizeLimitFilter(ObjectProvider<PasswordResetEventProperties> propertiesProvider) {
            this.propertiesProvider = propertiesProvider;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            PasswordResetEventProperties properties =
                    propertiesProvider != null ? propertiesProvider.getIfAvailable() : null;
            int maxBytes = properties != null
                    ? properties.getMaxBodySizeBytes()
                    : PasswordResetEventProperties.DEFAULT_MAX_BODY_SIZE_BYTES;

            long contentLength = request.getContentLengthLong();
            if (contentLength > maxBytes) {
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }

            BoundedBodyRequestWrapper requestWrapper = new BoundedBodyRequestWrapper(request, maxBytes);
            try {
                filterChain.doFilter(requestWrapper, response);
            } catch (PayloadTooLargeException ex) {
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                }
            }
        }
    }

    static final class PayloadTooLargeException extends RuntimeException {
        PayloadTooLargeException(String message) {
            super(message);
        }
    }

    static final class BoundedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final int maxBytes;
        private ServletInputStream inputStream;

        BoundedBodyRequestWrapper(HttpServletRequest request, int maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new BoundedServletInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }
    }

    static final class BoundedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final int maxBytes;
        private int bytesRead = 0;

        BoundedServletInputStream(ServletInputStream delegate, int maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                bytesRead++;
                if (bytesRead > maxBytes) {
                    throw new PayloadTooLargeException("Request body exceeds maximum size of " + maxBytes + " bytes");
                }
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read != -1) {
                bytesRead += read;
                if (bytesRead > maxBytes) {
                    throw new PayloadTooLargeException("Request body exceeds maximum size of " + maxBytes + " bytes");
                }
            }
            return read;
        }
    }
}
