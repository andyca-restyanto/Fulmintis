# filepath: /backend/Dockerfile
# syntax=docker/dockerfile:1
# ---- Tahap 1: build jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Test dijalankan di CI (lihat .github/workflows/ci.yml); build image tidak mengulanginya.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && cp target/*.jar /build/app.jar

# ---- Tahap 2: runtime (JRE saja, user non-root) ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
 && mkdir -p /var/data/frontline-uploads \
 && chown -R app:app /var/data/frontline-uploads
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# Folder evidence harus di volume persisten (lihat docker-compose.yml).
VOLUME ["/var/data/frontline-uploads"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
