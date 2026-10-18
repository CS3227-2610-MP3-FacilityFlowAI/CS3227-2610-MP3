# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /workspace
COPY --chmod=0755 gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-noble

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/facilityflow.jar

ENV PORT=8080
EXPOSE 8080
USER 10001:10001

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/facilityflow.jar"]
