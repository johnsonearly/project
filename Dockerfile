# === Stage 1: Build the application using Maven ===
FROM eclipse-temurin:21-jdk as builder

# Set working directory inside the container
WORKDIR /app

# Copy your project files from the subdirectory `project`
COPY demo/pom.xml .
COPY demo/mvnw .
COPY demo/.mvn .mvn
COPY demo/src ./src

# Make Maven wrapper executable
RUN chmod +x mvnw

# Build the application without tests
RUN ./mvnw clean package -DskipTests

# === Stage 2: Run the built JAR ===
FROM eclipse-temurin:21-jre

WORKDIR /demo

# Copy the built JAR from the previous stage
COPY --from=builder /demo/target/*.jar demo.jar

# Expose the port Spring Boot runs on
EXPOSE 8080

# Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "demo.jar"]
