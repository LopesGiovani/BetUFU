# Multi-stage Dockerfile para BETUFU Frontend (Angular zoneless / Nginx)
# Estágio 1: Builder multi-stage hermético com digest OCI fixado
FROM docker.io/nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0 AS builder
WORKDIR /workspace/frontend

COPY package.json package-lock.json ./
COPY angular.json tsconfig*.json ./
COPY src/ src/
COPY dist/ /workspace/frontend/dist/

# Estágio 2: Imagem final de execução com Nginx não-privilegiado com digest OCI fixado
FROM docker.io/nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0 AS runtime

# Copia os arquivos compilados de produção do estágio builder
COPY --chown=nginx:nginx --from=builder /workspace/frontend/dist/betufu/browser /usr/share/nginx/html

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
