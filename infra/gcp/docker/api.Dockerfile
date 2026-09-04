FROM eclipse-temurin:21-jdk-jammy@sha256:ce5767b7222312d42395f5bab033cd91f09e44032a2f21bdfd7b5b912dbe1e77 AS build
WORKDIR /src/apps/api
COPY apps/api/ ./
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home gole
WORKDIR /app
COPY --from=build /src/apps/api/build/libs/api-0.0.1-SNAPSHOT.jar /app/api.jar
USER gole
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx1536m", "-jar", "/app/api.jar"]
