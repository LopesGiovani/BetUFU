package br.com.betufu.platform.realtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Intercepts inbound STOMP frames to enforce session authentication and XSRF double-submit on the
 * STOMP CONNECT command, and prohibits unapproved chat traffic in Phase 01.
 */
@Component
public class StompConnectSecurityInterceptor implements ChannelInterceptor {

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    StompCommand command = accessor.getCommand();

    if (StompCommand.CONNECT.equals(command)) {
      validateConnectFrame(accessor);
    } else if (StompCommand.SEND.equals(command)) {
      throw new AccessDeniedException("Sending application messages is forbidden in this phase");
    } else if (StompCommand.SUBSCRIBE.equals(command)) {
      validateSubscribeDestination(accessor);
    }

    return message;
  }

  private void validateConnectFrame(StompHeaderAccessor accessor) {
    Map<String, java.util.List<String>> nativeHeaders = accessor.toNativeHeaderMap();
    if (nativeHeaders != null) {
      for (String headerName : nativeHeaders.keySet()) {
        if ("authorization".equalsIgnoreCase(headerName)) {
          throw new IllegalArgumentException(
              "Bearer or Authorization tokens are forbidden in STOMP headers");
        }
      }
    }

    Principal user = accessor.getUser();
    if (user == null || user.getName() == null || user.getName().isBlank()) {
      throw new AccessDeniedException("Session authentication is required for STOMP CONNECT");
    }

    String stompXsrfToken = accessor.getFirstNativeHeader("X-XSRF-TOKEN");
    if (stompXsrfToken == null || stompXsrfToken.isBlank()) {
      throw new AccessDeniedException("Missing X-XSRF-TOKEN header on STOMP CONNECT");
    }

    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    String cookieXsrfToken =
        sessionAttributes != null
            ? (String) sessionAttributes.get(WebSocketConfiguration.XSRF_COOKIE_ATTRIBUTE)
            : null;

    if (cookieXsrfToken == null || cookieXsrfToken.isBlank()) {
      throw new AccessDeniedException("Missing XSRF-TOKEN cookie in WebSocket handshake session");
    }

    if (!MessageDigest.isEqual(
        cookieXsrfToken.getBytes(StandardCharsets.UTF_8),
        stompXsrfToken.getBytes(StandardCharsets.UTF_8))) {
      throw new AccessDeniedException("Divergent XSRF token value on STOMP CONNECT");
    }
  }

  private void validateSubscribeDestination(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith("/user/")) {
      throw new AccessDeniedException("Subscription to destination is forbidden");
    }
  }
}
