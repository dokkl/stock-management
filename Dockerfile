# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon help > /dev/null
COPY config config
COPY src/main src/main
RUN ./gradlew --no-daemon bootJar \
    && java -Djarmode=tools -jar build/libs/stock-management-0.1.0-SNAPSHOT.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
