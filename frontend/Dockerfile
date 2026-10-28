# ---------- Stage 1: build Vue (Vite + TS + Tailwind) ----------
FROM node:lts-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
# API dipanggil lewat path relatif /api (diteruskan Nginx ke backend)
ENV VITE_API_BASE_URL=/api
RUN npm run build

# ---------- Stage 2: serve dengan Nginx ----------
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
