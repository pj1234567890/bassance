# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
# Download dependencies first to leverage Docker layer caching
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -B package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre
WORKDIR /app

# Turbocharged JVM flags engineered for 0.1 vCPU / 512MB RAM environments:
# - CICompilerCount=1: Restricts JIT compilation threads to 1 to prevent CPU thrashing.
# - UseStringDeduplication: Reduces memory footprint during heavy context initialization.
# - TieredStopAtLevel=1: Disables C2 compiler to speed up startup time.
# - UseSerialGC: Low-overhead garbage collection ideal for single-core/fractional CPUs.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC \
-XX:MaxRAMPercentage=60.0 \
-XX:InitialRAMPercentage=20.0 \
-XX:TieredStopAtLevel=1 \
-XX:MaxMetaspaceSize=128m \
-Xss256k \
-XX:+ExitOnOutOfMemoryError \
-XX:CICompilerCount=1 \
-XX:+UseStringDeduplication \
-Dspring.main.lazy-initialization=true \
-Djava.security.egd=file:/dev/./urandom"

RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=10001:10001 /workspace/target/login-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 3000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
