# Multi-stage: build web + sidecar (Node) và engine (JDK 21) → 1 image chạy JRE 21 + Node 22.
# Ngữ cảnh build là GỐC repo: docker build -f docker/Dockerfile .  (docker compose đã cấu hình sẵn)

# ---- 1. Web (Angular) → file tĩnh
FROM node:22-slim AS web
WORKDIR /src/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# ---- 2. Sidecar phân tích Angular (analyzer-node)
FROM node:22-slim AS sidecar
WORKDIR /src
COPY contracts/ contracts/
COPY analyzer-node/package*.json analyzer-node/
RUN cd analyzer-node && npm ci
COPY analyzer-node/ analyzer-node/
RUN cd analyzer-node && npm run build && npm prune --omit=dev

# ---- 3. Engine (Spring Boot); web build được đưa vào static/ để Engine phục vụ ở cổng 8600
FROM eclipse-temurin:21-jdk AS engine
WORKDIR /src/engine
COPY contracts/ /src/contracts/
COPY engine/ ./
COPY --from=web /src/web/dist/flowtracer-web/browser/ flowtracer-app/src/main/resources/static/
# Thử lại tối đa 3 lần: tải thư viện từ Maven Central thỉnh thoảng đứt giữa chừng (mạng chập chờn)
RUN for i in 1 2 3; do sh ./mvnw -B -q -DskipTests package && exit 0; echo "mvn lần $i lỗi, thử lại sau 10 giây"; sleep 10; done; exit 1

# ---- 4. Image chạy: JRE 21 + Node 22 (chỉ để chạy sidecar), không chạy bằng root
FROM eclipse-temurin:21-jre
COPY --from=node:22-slim /usr/local/bin/node /usr/local/bin/node
RUN useradd --system --create-home flowtracer && mkdir -p /data && chown flowtracer /data
WORKDIR /app
COPY --from=engine /src/engine/flowtracer-app/target/flowtracer-app.jar app.jar
COPY --from=sidecar /src/analyzer-node/dist analyzer-node/dist
COPY --from=sidecar /src/analyzer-node/node_modules analyzer-node/node_modules
COPY --from=sidecar /src/analyzer-node/package.json analyzer-node/package.json
USER flowtracer
# BIND_HOST=0.0.0.0 chỉ trong container; docker-compose chỉ publish 127.0.0.1:8600 ra máy (NFR-16)
ENV SOURCE_ROOT=/sources DATA_DIR=/data SIDECAR_CLI=/app/analyzer-node/dist/cli.js BIND_HOST=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
EXPOSE 8600
# JRE không có curl/wget; dùng chính Node đã có để gọi /api/health
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:8600/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
