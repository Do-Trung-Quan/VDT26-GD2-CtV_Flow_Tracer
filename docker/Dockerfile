# Multi-stage: build web + sidecar (Node) và engine (JDK 21) → 1 image chạy JRE 21 + Node 22
FROM node:22-slim AS web
WORKDIR /src/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM node:22-slim AS sidecar
WORKDIR /src
COPY contracts/ contracts/
COPY analyzer-node/package*.json analyzer-node/
RUN cd analyzer-node && npm ci
COPY analyzer-node/ analyzer-node/
RUN cd analyzer-node && npm run build && npm prune --omit=dev

FROM eclipse-temurin:21-jdk AS engine
WORKDIR /src/engine
COPY contracts/ /src/contracts/
COPY engine/ ./
COPY --from=web /src/web/dist/flowtracer-web/browser/ flowtracer-app/src/main/resources/static/
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
COPY --from=node:22-slim /usr/local/bin/node /usr/local/bin/node
RUN useradd --system --create-home flowtracer && mkdir -p /data && chown flowtracer /data
WORKDIR /app
COPY --from=engine /src/engine/flowtracer-app/target/flowtracer-app.jar app.jar
COPY --from=sidecar /src/analyzer-node/dist analyzer-node/dist
COPY --from=sidecar /src/analyzer-node/node_modules analyzer-node/node_modules
COPY --from=sidecar /src/analyzer-node/package.json analyzer-node/package.json
USER flowtracer
ENV SOURCE_ROOT=/sources DATA_DIR=/data SIDECAR_CLI=/app/analyzer-node/dist/cli.js BIND_HOST=0.0.0.0
EXPOSE 8600
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
