# =============================================================================
# Multi-stage Dockerfile for Odoo Emulator (Quarkus)
# =============================================================================
# Build:  docker build -t odoo-emulator .
# Run:    docker run -p 8069:8069 odoo-emulator
#
# Override DB at runtime:
#   docker run -p 8069:8069 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/odoo_payments odoo-emulator
# =============================================================================


# Use JDK instead of JRE for development tools
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Create non-root user (optional, but kept for consistency)
RUN addgroup -g 1000 app && adduser -u 1000 -G app -s /bin/sh -D app

# Copy Maven wrapper files first for layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies to cache them in the image layer
RUN ./mvnw dependency:go-offline -B

# Copy the source code [cite: 3, 4]
COPY src src

# Fix permissions for the app user
RUN chown -R app:app /app && chmod +x mvnw

USER app

# Expose HTTP port and Debug port
EXPOSE 8069 5005

# Run in dev mode. 
# -Dquarkus.http.host=0.0.0.0 is critical for container accessibility.
CMD ["./mvnw", "quarkus:dev", "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8069"]
