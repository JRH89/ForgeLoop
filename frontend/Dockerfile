FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build
FROM nginx:1.29-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-security-headers.conf /etc/nginx/snippets/forgeloop-security-headers.conf
# Nginx needs writable cache/pid paths, but does not need a root master process.
RUN apk upgrade --no-cache \
    && sed -i 's@^[[:space:]]*pid[[:space:]].*;@pid /tmp/nginx.pid;@' /etc/nginx/nginx.conf \
    && grep -q '^pid /tmp/nginx.pid;' /etc/nginx/nginx.conf \
    && chown -R nginx:nginx /var/cache/nginx
USER nginx
EXPOSE 80
