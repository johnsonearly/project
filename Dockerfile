# === Stage 1: Build the application using Maven ===
FROM eclipse-temurin:21-jdk as builder

# Set working directory inside the container
WORKDIR /app

# Copy your project files from the subdirectory `project`
COPY project/pom.xml .
COPY project/mvnw .
COPY project/.mvn .mvn
COPY project/src ./src

# Make Maven wrapper executable
RUN chmod +x mvnw

# Build the application without tests
RUN ./mvnw clean package -DskipTests

# === Stage 2: Run the built JAR ===
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built JAR from the previous stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the port Spring Boot runs on
EXPOSE 8080

# Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
