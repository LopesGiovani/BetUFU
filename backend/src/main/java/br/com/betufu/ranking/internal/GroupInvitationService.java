package br.com.betufu.ranking.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GroupInvitationService {
  private final SecureRandom random = new SecureRandom();

  String newCode() {
    byte[] secret = new byte[32];
    random.nextBytes(secret);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
  }

  Optional<byte[]> hash(String code) {
    if (code == null || !code.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
    try {
      byte[] secret = Base64.getUrlDecoder().decode(code);
      return secret.length == 32 ? Optional.of(sha256(secret)) : Optional.empty();
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }

  private static byte[] sha256(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }
}
