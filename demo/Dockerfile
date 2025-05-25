# === Stage 1: Build ===
FROM eclipse-temurin:21-jdk as builder

# Set working directory inside container
WORKDIR /app

# Copy Maven wrapper and project files
COPY . .

# Grant execution permissions to Maven wrapper
RUN chmod +x mvnw

# Build the application without tests
RUN ./mvnw clean package -DskipTests

# === Stage 2: Run ===
FROM eclipse-temurin:21-jre

# Set working directory for the runtime container
WORKDIR /demo

# Copy the built JAR file from the builder stage
COPY --from=builder /demo/target/*.jar demo.jar

# Expose the port your Spring Boot app listens on
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
