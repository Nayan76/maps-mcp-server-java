# =============================================================================
# Stage 1: Build the application using Maven and Java 21 LTS
# =============================================================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# =============================================================================
# Stage 2: Minimal Production Runtime Image (Java 21 JRE)
# =============================================================================
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="Maps MCP Architecture Team"
LABEL description="Production-Ready Google Maps-inspired MCP Server in Java 21"

RUN groupadd -r mcpuser && useradd -r -g mcpuser mcpuser

WORKDIR /app

COPY --from=builder /workspace/target/google-maps-mcp-server.jar /app/google-maps-mcp-server.jar

RUN chown -R mcpuser:mcpuser /app
USER mcpuser

ENV SERVER_PORT=8080 \
    MCP_TRANSPORT=http \
    MCP_HTTP_PROTOCOL=STREAMABLE \
    MAP_PROVIDER=openstreetmap \
    CACHE_ENABLED=true \
    CACHE_TTL=3600 \
    LOG_LEVEL=INFO \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/google-maps-mcp-server.jar"]
