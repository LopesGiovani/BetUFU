# Multi-stage Dockerfile para BETUFU Frontend (Angular zoneless / Nginx)
# Estágio 1: Builder multi-stage hermético com digest OCI fixado
FROM docker.io/nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03 AS builder
WORKDIR /workspace/frontend

COPY package.json package-lock.json ./
COPY angular.json tsconfig*.json ./
COPY src/ src/
COPY dist/ /workspace/frontend/dist/

# Estágio 2: Imagem final de execução com Nginx não-privilegiado com digest OCI fixado
FROM docker.io/nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03 AS runtime

USER root
RUN apk upgrade --no-cache curl libcurl brotli-libs zlib
USER 101

# Copia os arquivos compilados de produção do estágio builder
COPY --chown=nginx:nginx --from=builder /workspace/frontend/dist/betufu/browser /usr/share/nginx/html

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
