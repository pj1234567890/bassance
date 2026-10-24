FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
# Download dependencies first to leverage Docker layer caching
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# Optimized JVM flags for low CPU (0.1 vCPU) and 512MB RAM
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC \
-XX:MaxRAMPercentage=55.0 \
-XX:InitialRAMPercentage=15.0 \
-XX:TieredStopAtLevel=1 \
-XX:MaxMetaspaceSize=128m \
-Xss256k \
-XX:+ExitOnOutOfMemoryError \
-Dspring.main.lazy-initialization=true"

RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=10001:10001 /workspace/target/login-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 3000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
