FROM node:22-alpine AS frontend-build

WORKDIR /build

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/index.html frontend/tsconfig.json frontend/tsconfig.app.json frontend/tsconfig.node.json frontend/vite.config.ts ./
COPY frontend/public ./public
COPY frontend/src ./src

ARG VITE_API_BASE_URL=
ARG VITE_GOOGLE_CLIENT_ID
RUN VITE_API_BASE_URL="${VITE_API_BASE_URL}" \
    VITE_GOOGLE_CLIENT_ID="${VITE_GOOGLE_CLIENT_ID}" \
    npm run build

FROM nginx:1.28-alpine

COPY --from=frontend-build /build/dist /usr/share/nginx/html
