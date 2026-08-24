# Multi-stage Dockerfile para BETUFU Keycloak com Password Event Listener SPI (Java 21 / Keycloak 26.7.1)
# Estágio 1: builder com toolchain Java 21 pinada. O build não é hermético: o Maven
# pode acessar os repositórios configurados para resolver dependências durante esta etapa.
FROM maven:3.9.6-eclipse-temurin-21@sha256:8d63d4c1902cb12d9e79a70671b18ebe26358cb592561af33ca1808f00d935cb AS builder
WORKDIR /workspace

# Copia pom.xml e código-fonte do provider
COPY keycloak-password-event-listener/pom.xml keycloak-password-event-listener/pom.xml
COPY keycloak-password-event-listener/src keycloak-password-event-listener/src

# Empacota o JAR do provider com release Java 21
RUN mvn -f keycloak-password-event-listener/pom.xml -B clean package -DskipTests

# Estágio 2: Imagem final de execução Keycloak 26.7.1 com digest OCI pinado
FROM quay.io/keycloak/keycloak:26.7.1@sha256:f1f1f01e472c8a78df40d8f2a49a925274eda4d3d80d5f6edbb5c880ee3c01c6 AS runtime

# Copia somente o JAR do provider para o diretório de providers do Keycloak
COPY --from=builder /workspace/keycloak-password-event-listener/target/keycloak-password-event-listener-0.1.0.jar /opt/keycloak/providers/keycloak-password-event-listener-0.1.0.jar

# Executa otimização de build do Keycloak para indexar os providers adicionados
RUN /opt/keycloak/bin/kc.sh build
