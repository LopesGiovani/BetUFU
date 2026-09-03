package br.com.betufu.platform.diagnostic;

import br.com.betufu.platform.observability.LogSanitizer;
import br.com.betufu.platform.persistence.PostgresAuthorityProbe;
import br.com.betufu.platform.persistence.PrivateObjectStorageProbe;
import br.com.betufu.platform.persistence.RedisAuxiliaryProbe;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * Aggregates all six platform dependency probes with a global deadline, graceful degradation, probe
 * task cancellation, and strict error sanitization per D-03 / D-04.
 */
@Component
public class PlatformDependencyProbes implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(PlatformDependencyProbes.class);
  private static final Duration GLOBAL_DEADLINE = Duration.ofMillis(1500);

  private final PostgresAuthorityProbe postgresAuthorityProbe;
  private final RedisAuxiliaryProbe redisAuxiliaryProbe;
  private final PrivateObjectStorageProbe privateObjectStorageProbe;
  private final ClientRegistrationRepository clientRegistrationRepository;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public PlatformDependencyProbes(
      @Autowired(required = false) PostgresAuthorityProbe postgresAuthorityProbe,
      @Autowired(required = false) RedisAuxiliaryProbe redisAuxiliaryProbe,
      @Autowired(required = false) PrivateObjectStorageProbe privateObjectStorageProbe,
      @Autowired(required = false) ClientRegistrationRepository clientRegistrationRepository) {
    this.postgresAuthorityProbe = postgresAuthorityProbe;
    this.redisAuxiliaryProbe = redisAuxiliaryProbe;
    this.privateObjectStorageProbe = privateObjectStorageProbe;
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  /**
   * Executes all 6 probes concurrently with a global non-cumulative deadline of 1.5s. Guaranteed
   * never to exceed the global deadline regardless of how many probes are blocked.
   */
  public List<DiagnosticComponent> probeAll() {
    List<CompletableFuture<DiagnosticComponent>> futures =
        List.of(
            CompletableFuture.supplyAsync(this::probeApi, executor),
            CompletableFuture.supplyAsync(this::probeBffSession, executor),
            CompletableFuture.supplyAsync(this::probeKeycloak, executor),
            CompletableFuture.supplyAsync(this::probePostgresql, executor),
            CompletableFuture.supplyAsync(this::probeRedis, executor),
            CompletableFuture.supplyAsync(this::probeObjectStorage, executor));

    CompletableFuture<Void> allOf =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

    try {
      allOf.get(GLOBAL_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException te) {
      log.warn("Diagnostic aggregation reached global deadline of {}", GLOBAL_DEADLINE);
    } catch (Exception ex) {
      log.warn("Diagnostic probe aggregation error: {}", LogSanitizer.sanitize(ex.getMessage()));
    }

    List<DiagnosticComponent> results = new ArrayList<>(6);
    for (int i = 0; i < futures.size(); i++) {
      CompletableFuture<DiagnosticComponent> future = futures.get(i);
      if (future.isDone() && !future.isCompletedExceptionally()) {
        results.add(future.join());
      } else {
        future.cancel(true);
        String errorDescription =
            future.isCompletedExceptionally()
                ? "Falha na execução da sondagem"
                : "Tempo limite de resposta excedido";
        results.add(fallbackComponent(i, DiagnosticStatus.DEGRADED, errorDescription));
      }
    }
    return results;
  }

  /**
   * Auto-reporte de vitalidade da API da plataforma. Se o processo JVM está executando e gerando
   * esta resposta de diagnóstico, a API está operacional.
   */
  private DiagnosticComponent probeApi() {
    return new DiagnosticComponent(
        DiagnosticComponent.API, DiagnosticStatus.UP, "API respondendo normalmente");
  }

  private DiagnosticComponent probeBffSession() {
    try {
      if (redisAuxiliaryProbe != null) {
        var redisStatus = redisAuxiliaryProbe.checkAuxiliaryStatus();
        if (!redisStatus.auxiliaryHealthy()) {
          return new DiagnosticComponent(
              DiagnosticComponent.BFF_SESSION,
              DiagnosticStatus.DEGRADED,
              "Sessão BFF degradada por dependência de cache");
        }
      }
      return new DiagnosticComponent(
          DiagnosticComponent.BFF_SESSION, DiagnosticStatus.UP, "Sessão BFF operacional");
    } catch (Exception e) {
      log.warn("BFF session probe failure: {}", LogSanitizer.sanitize(e.getMessage()));
      return new DiagnosticComponent(
          DiagnosticComponent.BFF_SESSION, DiagnosticStatus.DEGRADED, "Sessão BFF degradada");
    }
  }

  private DiagnosticComponent probeKeycloak() {
    try {
      if (clientRegistrationRepository == null) {
        return new DiagnosticComponent(
            DiagnosticComponent.KEYCLOAK,
            DiagnosticStatus.DEGRADED,
            "Provedor de autenticação não configurado");
      }
      var reg = clientRegistrationRepository.findByRegistrationId("keycloak");
      if (reg != null) {
        return new DiagnosticComponent(
            DiagnosticComponent.KEYCLOAK,
            DiagnosticStatus.UP,
            "Provedor de autenticação disponível");
      }
      return new DiagnosticComponent(
          DiagnosticComponent.KEYCLOAK,
          DiagnosticStatus.DEGRADED,
          "Registro de autenticação indisponível");
    } catch (Exception e) {
      log.warn("Keycloak probe failure: {}", LogSanitizer.sanitize(e.getMessage()));
      return new DiagnosticComponent(
          DiagnosticComponent.KEYCLOAK, DiagnosticStatus.DOWN, "Falha na comunicação com provedor");
    }
  }

  private DiagnosticComponent probePostgresql() {
    try {
      if (postgresAuthorityProbe == null) {
        return new DiagnosticComponent(
            DiagnosticComponent.POSTGRESQL,
            DiagnosticStatus.DEGRADED,
            "Banco de dados não configurado");
      }
      var status = postgresAuthorityProbe.checkAuthority();
      if (status.authoritative()) {
        return new DiagnosticComponent(
            DiagnosticComponent.POSTGRESQL, DiagnosticStatus.UP, "PostgreSQL 18 operacional");
      }
      return new DiagnosticComponent(
          DiagnosticComponent.POSTGRESQL,
          DiagnosticStatus.DEGRADED,
          "Banco de dados em estado não autoritativo");
    } catch (Exception e) {
      log.warn("Postgres probe failure: {}", LogSanitizer.sanitize(e.getMessage()));
      return new DiagnosticComponent(
          DiagnosticComponent.POSTGRESQL,
          DiagnosticStatus.DOWN,
          "Falha na conexão com banco de dados");
    }
  }

  private DiagnosticComponent probeRedis() {
    try {
      if (redisAuxiliaryProbe == null) {
        return new DiagnosticComponent(
            DiagnosticComponent.REDIS, DiagnosticStatus.DEGRADED, "Cache não configurado");
      }
      var status = redisAuxiliaryProbe.checkAuxiliaryStatus();
      if (status.auxiliaryHealthy()) {
        return new DiagnosticComponent(
            DiagnosticComponent.REDIS, DiagnosticStatus.UP, "Cache e sessões operacionais");
      }
      return new DiagnosticComponent(
          DiagnosticComponent.REDIS,
          DiagnosticStatus.DEGRADED,
          "Cache auxiliar operando em modo degradado");
    } catch (Exception e) {
      log.warn("Redis probe failure: {}", LogSanitizer.sanitize(e.getMessage()));
      return new DiagnosticComponent(
          DiagnosticComponent.REDIS, DiagnosticStatus.DOWN, "Falha na conexão com cache");
    }
  }

  private DiagnosticComponent probeObjectStorage() {
    try {
      if (privateObjectStorageProbe == null) {
        return new DiagnosticComponent(
            DiagnosticComponent.OBJECT_STORAGE,
            DiagnosticStatus.DEGRADED,
            "Armazenamento não configurado");
      }
      var status = privateObjectStorageProbe.checkStorageStatus();
      if (status.configured()) {
        return new DiagnosticComponent(
            DiagnosticComponent.OBJECT_STORAGE,
            DiagnosticStatus.UP,
            "Armazenamento de objetos operacional");
      }
      return new DiagnosticComponent(
          DiagnosticComponent.OBJECT_STORAGE,
          DiagnosticStatus.DEGRADED,
          "Armazenamento operando em modo degradado");
    } catch (Exception e) {
      log.warn("Object storage probe failure: {}", LogSanitizer.sanitize(e.getMessage()));
      return new DiagnosticComponent(
          DiagnosticComponent.OBJECT_STORAGE,
          DiagnosticStatus.DOWN,
          "Falha no armazenamento de objetos");
    }
  }

  private DiagnosticComponent fallbackComponent(
      int index, DiagnosticStatus status, String description) {
    String[] names = {
      DiagnosticComponent.API,
      DiagnosticComponent.BFF_SESSION,
      DiagnosticComponent.KEYCLOAK,
      DiagnosticComponent.POSTGRESQL,
      DiagnosticComponent.REDIS,
      DiagnosticComponent.OBJECT_STORAGE
    };
    String name = (index >= 0 && index < names.length) ? names[index] : DiagnosticComponent.API;
    return new DiagnosticComponent(name, status, description);
  }

  @Override
  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }
}
