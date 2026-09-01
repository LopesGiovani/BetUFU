package br.com.betufu.platform.realtime;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * WebSocket and STOMP message broker configuration for technical platform messaging.
 *
 * <p>Enforces strict origin allowlists on the HTTP WebSocket handshake and attaches the {@link
 * StompConnectSecurityInterceptor} to validate session principal and XSRF double-submit on the
 * STOMP CONNECT frame.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

  public static final String XSRF_COOKIE_ATTRIBUTE = "BETUFU_XSRF_TOKEN";

  private final String publicOrigin;
  private final StompConnectSecurityInterceptor securityInterceptor;
  private final ObjectProvider<SessionRepository<?>> sessionRepositoryProvider;

  public WebSocketConfiguration(
      @Value("${betufu.web.public-origin:http://localhost:4200}") String publicOrigin,
      StompConnectSecurityInterceptor securityInterceptor,
      ObjectProvider<SessionRepository<?>> sessionRepositoryProvider) {
    this.publicOrigin = publicOrigin;
    this.securityInterceptor = securityInterceptor;
    this.sessionRepositoryProvider = sessionRepositoryProvider;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws")
        .setAllowedOrigins(publicOrigin)
        .setHandshakeHandler(
            new DefaultHandshakeHandler() {
              @Override
              protected Principal determineUser(
                  ServerHttpRequest request,
                  WebSocketHandler wsHandler,
                  Map<String, Object> attributes) {
                Object secContext = attributes.get("SPRING_SECURITY_CONTEXT");
                if (secContext instanceof SecurityContext sc) {
                  return sc.getAuthentication();
                } else if (secContext instanceof Authentication auth) {
                  return auth;
                } else if (secContext instanceof Principal p) {
                  return p;
                }
                return request.getPrincipal();
              }
            })
        .addInterceptors(
            new HttpSessionHandshakeInterceptor(),
            new XsrfCookieHandshakeInterceptor(sessionRepositoryProvider));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/queue");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(securityInterceptor);
  }

  static class XsrfCookieHandshakeInterceptor implements HandshakeInterceptor {

    private final ObjectProvider<SessionRepository<?>> sessionRepositoryProvider;

    XsrfCookieHandshakeInterceptor(ObjectProvider<SessionRepository<?>> sessionRepositoryProvider) {
      this.sessionRepositoryProvider = sessionRepositoryProvider;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes) {
      String cookieHeader = request.getHeaders().getFirst("Cookie");
      String sessionId = null;
      String xsrfToken = null;

      if (cookieHeader != null) {
        for (String part : cookieHeader.split(";")) {
          String trimmed = part.trim();
          if (trimmed.startsWith("XSRF-TOKEN=")) {
            xsrfToken = trimmed.substring("XSRF-TOKEN=".length());
          } else if (trimmed.startsWith("SESSION=")) {
            sessionId = trimmed.substring("SESSION=".length());
          }
        }
      }

      if (request instanceof ServletServerHttpRequest servletRequest) {
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
          for (Cookie cookie : cookies) {
            if ("XSRF-TOKEN".equals(cookie.getName())) {
              xsrfToken = cookie.getValue();
            } else if ("SESSION".equals(cookie.getName())) {
              sessionId = cookie.getValue();
            }
          }
        }
      }

      if (xsrfToken != null) {
        attributes.put(XSRF_COOKIE_ATTRIBUTE, xsrfToken);
      }

      if (sessionId != null && !attributes.containsKey("SPRING_SECURITY_CONTEXT")) {
        SessionRepository<?> repo = sessionRepositoryProvider.getIfAvailable();
        if (repo != null) {
          Session session = repo.findById(sessionId);
          if (session == null) {
            try {
              byte[] decoded = Base64.getDecoder().decode(sessionId);
              String decodedId = new String(decoded, StandardCharsets.UTF_8);
              session = repo.findById(decodedId);
            } catch (IllegalArgumentException ignored) {
              // not Base64
            }
          }
          if (session != null) {
            Object secContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
            if (secContext != null) {
              attributes.put("SPRING_SECURITY_CONTEXT", secContext);
            }
          }
        }
      }

      return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception) {
      // no-op
    }
  }
}
