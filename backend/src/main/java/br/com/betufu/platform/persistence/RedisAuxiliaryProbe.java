package br.com.betufu.platform.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisAuxiliaryProbe {

    private final RedisConnectionFactory connectionFactory;

    public RedisAuxiliaryProbe(@Autowired(required = false) RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public RedisAuxiliaryStatus checkAuxiliaryStatus() {
        if (connectionFactory == null) {
            return new RedisAuxiliaryStatus(false, "UNAVAILABLE");
        }

        try (RedisConnection connection = connectionFactory.getConnection()) {
            String ping = connection.ping();
            boolean ok = "PONG".equalsIgnoreCase(ping);
            return new RedisAuxiliaryStatus(ok, ping);
        } catch (Exception e) {
            return new RedisAuxiliaryStatus(false, "ERROR");
        }
    }

    public record RedisAuxiliaryStatus(boolean auxiliaryHealthy, String pingStatus) {}
}
