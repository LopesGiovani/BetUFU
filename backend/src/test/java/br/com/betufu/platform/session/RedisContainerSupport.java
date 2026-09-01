package br.com.betufu.platform.session;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Suporte reutilizável para containers Redis herméticos em testes de integração da BETUFU.
 *
 * <p>Mantém a mesma imagem fixada por digest SHA-256 de {@link RedisSessionIT}, com política de
 * pull desativada (garantindo execução hermética local) e comando configurado com senha de teste.
 */
public final class RedisContainerSupport {

  public static final String REDIS_PASSWORD = "betufu-redis-session-it-only";
  public static final int REDIS_PORT = 6379;
  public static final String REDIS_IMAGE_REFERENCE =
      "docker.io/library/redis:8.10.0-trixie@sha256:"
          + "344e3945a0b431c8ff1eecd58c5573538126bd756f02fc7e218ddf1fc2546366";

  public static final DockerImageName REDIS_IMAGE =
      DockerImageName.parse(REDIS_IMAGE_REFERENCE).asCompatibleSubstituteFor("redis");

  public static final GenericContainer<?> REDIS =
      new GenericContainer<>(REDIS_IMAGE)
          .withImagePullPolicy(imageName -> false)
          .withExposedPorts(REDIS_PORT)
          .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
          .waitingFor(Wait.forListeningPort());

  private RedisContainerSupport() {}

  /**
   * Registra as propriedades dinâmicas de conexão ao Redis para o contexto Spring.
   *
   * @param registry o registro de propriedades do Spring Test
   */
  public static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("BETUFU_REDIS_HOST", REDIS::getHost);
    registry.add("BETUFU_REDIS_PORT", () -> REDIS.getMappedPort(REDIS_PORT));
    registry.add("BETUFU_REDIS_PASSWORD", () -> REDIS_PASSWORD);
  }
}
