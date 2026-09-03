FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src/apps/api
COPY apps/api/ ./
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home gole
WORKDIR /app
COPY --from=build /src/apps/api/build/libs/api-0.0.1-SNAPSHOT.jar /app/api.jar
USER gole
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx1536m", "-jar", "/app/api.jar"]
