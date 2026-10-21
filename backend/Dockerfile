# Backend image for Render (Docker runtime — Render has no native Java buildpack).
# Build context is backend/ (Render: dockerContext "backend", dockerfilePath "./Dockerfile").
# The Spring Boot entrypoint now exists at
# src/main/java/com/caregiver/config/CaregiverApplication.java, so `mvn package`
# produces a runnable jar. The CI image build is still marked non-blocking
# because it has not been re-verified green since the entrypoint landed —
# make it enforcing (drop `continue-on-error` in .github/workflows/ci.yml)
# once someone confirms a successful build.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /app/target/caregiver-backend-*.jar app.jar
RUN chown appuser app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
