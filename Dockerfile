# === Stage 1: Build ===
FROM eclipse-temurin:21-jdk as build

# Copy Maven wrapper and project files
COPY .idea .

# Build the application without tests
RUN ./mvnw clean package -DskipTests

# === Stage 2: Run ===
FROM eclipse-temurin:21-jre

# Copy the built JAR file from the builder stage
COPY --from=build /demo/target/*.jar demo.jar

# Expose the port your Spring Boot app listens on
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "demo.jar"]
