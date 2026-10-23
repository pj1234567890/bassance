FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:InitialRAMPercentage=10.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=10001:10001 /workspace/target/login-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
