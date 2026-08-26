# ─── Multi-stage Build for Spring Boot Backend ──────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build jar package
COPY src ./src
RUN mvn package -DskipTests -B

# ─── Production Runtime Stage ────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy built artifact from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set permissions
RUN chown -R appuser:appgroup /app
USER appuser

# Expose port (Render overrides with PORT env var)
EXPOSE 8080

# Run Spring Boot app
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
