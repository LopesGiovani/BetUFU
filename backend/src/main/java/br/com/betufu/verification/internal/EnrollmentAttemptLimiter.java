package br.com.betufu.verification.internal;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@Profile("runtime | integrated")
public class EnrollmentAttemptLimiter {

  public static final int ACCOUNT_MAX_REJECTIONS = 5;
  public static final Duration ACCOUNT_WINDOW = Duration.ofMinutes(15);

  public static final int ORIGIN_MAX_REJECTIONS = 50;
  public static final Duration ORIGIN_WINDOW = Duration.ofHours(1);

  private static final String ACCOUNT_KEY_PREFIX = "betufu:verification:limiter:account:";
  private static final String ORIGIN_KEY_PREFIX = "betufu:verification:limiter:origin:";

  private static final String ACQUIRE_LUA_SCRIPT =
      """
            local accVal = redis.call('get', KEYS[1])
            local accCount = 0
            if accVal then
                local num = tonumber(accVal)
                if num == nil or num < 0 then
                    return -1
                end
                local ttl = redis.call('ttl', KEYS[1])
                if ttl == -1 then
                    return -2
                end
                accCount = num
            end

            local origVal = redis.call('get', KEYS[2])
            local origCount = 0
            if origVal then
                local num = tonumber(origVal)
                if num == nil or num < 0 then
                    return -3
                end
                local ttl = redis.call('ttl', KEYS[2])
                if ttl == -1 then
                    return -4
                end
                origCount = num
            end

            local accLimit = tonumber(ARGV[1])
            local origLimit = tonumber(ARGV[3])

            if accCount >= accLimit or origCount >= origLimit then
                return 0
            end

            local newAcc = redis.call('incr', KEYS[1])
            if newAcc == 1 or redis.call('ttl', KEYS[1]) == -1 then
                redis.call('expire', KEYS[1], tonumber(ARGV[2]))
            end

            local newOrig = redis.call('incr', KEYS[2])
            if newOrig == 1 or redis.call('ttl', KEYS[2]) == -1 then
                redis.call('expire', KEYS[2], tonumber(ARGV[4]))
            end

            return 1
            """;

  private static final String RELEASE_LUA_SCRIPT =
      """
            local accVal = redis.call('get', KEYS[1])
            if accVal then
                local num = tonumber(accVal)
                if num and num > 0 then
                    redis.call('decr', KEYS[1])
                end
            end

            local origVal = redis.call('get', KEYS[2])
            if origVal then
                local num = tonumber(origVal)
                if num and num > 0 then
                    redis.call('decr', KEYS[2])
                end
            end

            return 1
            """;

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final RedisScript<Long> acquireScript;
  private final RedisScript<Long> releaseScript;

  public EnrollmentAttemptLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.acquireScript = new DefaultRedisScript<>(ACQUIRE_LUA_SCRIPT, Long.class);
    this.releaseScript = new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class);
  }

  public boolean tryAcquire(UUID identityId, String originIp) {
    if (identityId == null || originIp == null || originIp.isBlank()) {
      return false;
    }

    StringRedisTemplate redisTemplate =
        (redisTemplateProvider != null) ? redisTemplateProvider.getIfAvailable() : null;
    if (redisTemplate == null) {
      return false;
    }

    String accountKey = ACCOUNT_KEY_PREFIX + identityId;
    String originKey = ORIGIN_KEY_PREFIX + originIp.trim();

    try {
      Long result =
          redisTemplate.execute(
              acquireScript,
              List.of(accountKey, originKey),
              String.valueOf(ACCOUNT_MAX_REJECTIONS),
              String.valueOf(ACCOUNT_WINDOW.toSeconds()),
              String.valueOf(ORIGIN_MAX_REJECTIONS),
              String.valueOf(ORIGIN_WINDOW.toSeconds()));

      return result != null && result == 1L;
    } catch (Exception e) {
      return false;
    }
  }

  public void release(UUID identityId, String originIp) {
    if (identityId == null || originIp == null || originIp.isBlank()) {
      return;
    }

    StringRedisTemplate redisTemplate =
        (redisTemplateProvider != null) ? redisTemplateProvider.getIfAvailable() : null;
    if (redisTemplate == null) {
      return;
    }

    String accountKey = ACCOUNT_KEY_PREFIX + identityId;
    String originKey = ORIGIN_KEY_PREFIX + originIp.trim();

    try {
      redisTemplate.execute(releaseScript, List.of(accountKey, originKey));
    } catch (Exception ignored) {
    }
  }

  @Override
  public String toString() {
    return "EnrollmentAttemptLimiter[]";
  }
}
