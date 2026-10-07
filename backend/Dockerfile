# Backend image for Render (Docker runtime — Render has no native Java buildpack).
# Build context is backend/ (Render: dockerContext "backend", dockerfilePath "./Dockerfile").
# NOTE: no @SpringBootApplication main class exists yet (foundation work pending,
# Member 3 #12), so the `mvn package` step currently fails at spring-boot:repackage
# ("Unable to find main class"). The image becomes buildable AND runnable once
# CaregiverApplication.java lands — no Dockerfile change needed then.

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
