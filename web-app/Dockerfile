# Multi-stage build: Vite production bundle served by nginx (proxies API + WebSocket).
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
# Prefer lockfile when in sync; fall back to install for optional platform packages.
RUN npm ci || npm install

COPY index.html vite.config.ts tsconfig.json tsconfig.app.json tsconfig.node.json ./
COPY public ./public
COPY src ./src

# Relative API path so the browser calls same-origin /api (proxied to Spring Boot).
ARG VITE_API_URL=/api/v1
ENV VITE_API_URL=${VITE_API_URL}

RUN npm run build

FROM nginx:1.27-alpine AS runtime
RUN apk add --no-cache curl
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD curl -fsS http://127.0.0.1/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
