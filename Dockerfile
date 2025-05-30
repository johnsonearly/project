# === Stage 1: Build the application using Maven ===
FROM eclipse-temurin:21-jdk as builder

# Set working directory inside the container
WORKDIR /app

# Copy your project files
COPY demo/pom.xml .
COPY demo/mvnw .
COPY demo/.mvn .mvn
COPY demo/src ./src

# Make Maven wrapper executable
RUN chmod +x mvnw

# Build the application without running tests
RUN ./mvnw clean package -DskipTests

# === Stage 2: Run the built JAR ===
FROM eclipse-temurin:21-jre

# Working directory for the running app
WORKDIR /demo

# Copy the built JAR file from the builder stage
COPY --from=builder /app/target/*.jar demo.jar

# Copy the qtable.ser file from your project (host) to the container
COPY q_tables.ser qtables.ser

# Expose the port the Spring Boot application runs on
EXPOSE 8080

# Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "demo.jar"]
