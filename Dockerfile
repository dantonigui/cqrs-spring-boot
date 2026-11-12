# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/cqrs-0.0.1-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
