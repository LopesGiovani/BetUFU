# Multi-stage Dockerfile para BETUFU Backend (Java 25 / Spring Boot 4.1.0)
# Estágio 1: Builder multi-stage hermético com digest OCI fixado
FROM docker.io/library/eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce AS builder
WORKDIR /workspace

# Copia fontes e artefatos de build validados
COPY .mvn/ .mvn/
COPY mvnw* ./
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY backend/target/betufu-backend-0.1.0.jar* /workspace/prebuilt/
RUN if [ -f /workspace/prebuilt/betufu-backend-0.1.0.jar ]; then \
      cp /workspace/prebuilt/betufu-backend-0.1.0.jar /workspace/app.jar; \
    else \
      echo "Artefato target/betufu-backend-0.1.0.jar necessário para o empacotamento."; exit 1; \
    fi

# Estágio 2: Imagem final de execução mínima JRE 25 com digest OCI fixado
FROM docker.io/library/eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce AS runtime
WORKDIR /app

# Instala curl e socat para probe de healthcheck e roteamento de loopback OIDC
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl socat && \
    rm -rf /var/lib/apt/lists/*

# Cria usuário não-privilegiado para segurança (UID 10001)
RUN groupadd --system --gid 10001 betufu && \
    useradd --system --uid 10001 --gid betufu --no-create-home betufu

# Copia o JAR do estágio builder
COPY --from=builder /workspace/app.jar /app/app.jar

RUN chown -R betufu:betufu /app

USER betufu:betufu

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "if [ -n \"$BETUFU_LOCAL_OIDC_PROXY\" ]; then socat TCP-LISTEN:8081,fork,bind=127.0.0.1 TCP:keycloak:8080 & fi; exec java $JAVA_OPTS -jar /app/app.jar"]
