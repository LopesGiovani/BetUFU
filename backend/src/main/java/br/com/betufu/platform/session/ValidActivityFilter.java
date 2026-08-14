package br.com.betufu.platform.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public final class ValidActivityFilter extends OncePerRequestFilter {

    private static final RequestMatcher CURRENT_IDENTITY_UPDATE =
            PathPatternRequestMatcher.withDefaults().matcher(
                    HttpMethod.PUT, "/api/v1/tracer/identity/current");

    private final SessionLifecycleService sessionLifecycleService;

    public ValidActivityFilter(SessionLifecycleService sessionLifecycleService) {
        this.sessionLifecycleService = Objects.requireNonNull(
                sessionLifecycleService,
                "sessionLifecycleService must not be null");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CURRENT_IDENTITY_UPDATE.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isRealAuthentication(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper bufferedResponse =
                new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, bufferedResponse);

            if (request.isAsyncStarted()) {
                throw new IllegalStateException(
                        "Allowlisted session activity must complete synchronously");
            }
            if (isSuccessful(bufferedResponse.getStatus())) {
                sessionLifecycleService.recordSuccessfulActivity(request, authentication);
            }
            bufferedResponse.copyBodyToResponse();
        } catch (IOException | ServletException | RuntimeException | Error failure) {
            if (!response.isCommitted()) {
                response.reset();
            }
            throw failure;
        }
    }

    private static boolean isRealAuthentication(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static boolean isSuccessful(int status) {
        return status >= HttpServletResponse.SC_OK && status < 300;
    }
}
