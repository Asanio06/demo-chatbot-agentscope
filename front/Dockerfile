# --- Stage 1 : build front (Node + Vite) ---
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Stage 2 : serve statique (nginx, non-root) ---
FROM nginx:1.27-alpine
# Vite emits a static SPA build in /app/dist; copy it as the web root, plus a custom
# nginx config that reverse-proxies /api (CopilotKit runtimeUrl="/api/copilotkit")
# to the « back » service of the compose.
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
