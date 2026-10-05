package br.com.betufu.chat.internal;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration(proxyBeanMethods = false)
@Profile("runtime | integrated")
@EnableWebSocket
public class ChatPushConfiguration implements WebSocketConfigurer {
  private final ChatPushEndpoint endpoint;
  private final String origin;

  public ChatPushConfiguration(
      ChatPushEndpoint endpoint,
      @Value("${betufu.web.public-origin:http://localhost:4200}") String origin) {
    this.endpoint = endpoint;
    this.origin = origin;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(endpoint, "/api/v1/chat/push")
        .setAllowedOrigins(origin)
        .addInterceptors(
            new HandshakeInterceptor() {
              @Override
              public boolean beforeHandshake(
                  ServerHttpRequest request,
                  ServerHttpResponse response,
                  WebSocketHandler handler,
                  Map<String, Object> attributes) {
                if (!origin.equals(request.getHeaders().getOrigin())
                    || request.getHeaders().getFirst("Authorization") != null) {
                  response.setStatusCode(HttpStatus.FORBIDDEN);
                  return false;
                }
                String csrf = cookie(request, "XSRF-TOKEN");
                if (csrf == null || csrf.isBlank() || csrf.length() > 256) {
                  response.setStatusCode(HttpStatus.FORBIDDEN);
                  return false;
                }
                attributes.put("chat.csrf", csrf);
                String session = cookie(request, "SESSION");
                if (session != null) {
                  String resolved = endpoint.resolveSession(session);
                  if (resolved == null) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                  }
                  attributes.put("chat.session", resolved);
                }
                return true;
              }

              @Override
              public void afterHandshake(
                  ServerHttpRequest request,
                  ServerHttpResponse response,
                  WebSocketHandler handler,
                  Exception exception) {}
            });
  }

  private static String cookie(ServerHttpRequest request, String name) {
    String result = null;
    for (String header : request.getHeaders().getOrEmpty("Cookie")) {
      for (String part : header.split(";")) {
        String trimmed = part.trim();
        if (trimmed.startsWith(name + "=")) {
          if (result != null) return null;
          result = trimmed.substring(name.length() + 1);
        }
      }
    }
    return result;
  }
}
