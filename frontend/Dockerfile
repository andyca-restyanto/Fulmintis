# filepath: /frontend/Dockerfile
# syntax=docker/dockerfile:1
# ---- Tahap 1: build SPA ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Default "/api": diteruskan Nginx ke backend (satu origin, tanpa CORS).
# Ganti lewat build arg bila API di domain lain.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---- Tahap 2: Nginx menyajikan dist + proxy /api ----
FROM nginx:1.27-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/app.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1/healthz || exit 1
