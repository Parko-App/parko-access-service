# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY .github/maven-settings.xml ./maven-settings.xml
COPY src ./src

RUN --mount=type=secret,id=github_actor \
    --mount=type=secret,id=packages_read_token \
    GITHUB_ACTOR="$(cat /run/secrets/github_actor)" \
    PACKAGES_READ_TOKEN="$(cat /run/secrets/packages_read_token)" \
    mvn -s maven-settings.xml clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
