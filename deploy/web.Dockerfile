FROM maven:3.9.12-eclipse-temurin-21-alpine AS runner-package
RUN apk add --no-cache git zip
WORKDIR /runner
ARG FORGELOOP_REVISION=unknown
COPY runner/pom.xml .
COPY runner/src src
RUN mvn -q "-Dforgeloop.revision=${FORGELOOP_REVISION}" verify
COPY runner/install /package
COPY runner/provider-policy.example.json /package/provider-policy.example.json
RUN cp target/runner-0.1.0.jar /package/runner.jar && mkdir /downloads && cd /package && zip -q -r /downloads/forgeloop-runner.zip . && cd /downloads && sha256sum forgeloop-runner.zip > forgeloop-runner.zip.sha256

FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=frontend /app/dist /usr/share/nginx/html
COPY --from=runner-package /downloads /usr/share/nginx/html/downloads
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/nginx-security-headers.conf /etc/nginx/snippets/forgeloop-security-headers.conf
# Nginx needs writable cache/pid paths, but does not need a root master process.
RUN apk upgrade --no-cache \
    && sed -i 's@^[[:space:]]*pid[[:space:]].*;@pid /tmp/nginx.pid;@' /etc/nginx/nginx.conf \
    && grep -q '^pid /tmp/nginx.pid;' /etc/nginx/nginx.conf \
    && chown -R nginx:nginx /var/cache/nginx
USER nginx
EXPOSE 80
