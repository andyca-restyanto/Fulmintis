# ---------- Stage 1: build JAR (pakai image Maven resmi, tidak butuh mvnw) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Download dependency dulu (di-cache selama pom.xml tidak berubah)
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

# Build aplikasi
COPY src src
RUN mvn -q -B package -DskipTests

# ---------- Stage 2: runtime (JRE saja, user non-root) ----------
FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
