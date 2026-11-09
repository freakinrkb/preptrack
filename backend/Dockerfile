FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S preptrack && adduser -S -G preptrack preptrack
WORKDIR /app
COPY --from=build --chown=preptrack:preptrack /workspace/build/libs/*-0.1.0.jar /app/preptrack.jar
USER preptrack
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=20 -XX:MaxRAMPercentage=60 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=4s --start-period=30s --retries=3 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/preptrack.jar"]
