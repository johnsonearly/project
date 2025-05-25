# === Stage 1: Build ===
# Use a base image with Java and Maven for building the application.
# We use Eclipse Temurin for a reliable OpenJDK distribution.
FROM eclipse-temurin:21-jdk-jammy AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven wrapper files and the pom.xml first.
# This allows Docker to cache these layers if only source code changes.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy the entire source code.
# The '.' after 'target' in .dockerignore prevents copying the local target directory.
COPY src ./src

# Make the Maven wrapper script executable. This is crucial for it to run.
RUN chmod +x mvnw

# Build the application using the Maven wrapper.
# -DskipTests: Skips running tests to speed up the build.
# -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true:
# These are sometimes needed in specific network environments to handle SSL certificates,
# but might not be necessary for all setups. You can remove them if not experiencing SSL issues.
RUN ./mvnw clean package -DskipTests

# === Stage 2: Run ===
# Use a smaller JRE-only base image for the final application, reducing image size.
FROM eclipse-temurin:21-jre-jammy

# Set the working directory for the running application
WORKDIR /app

# Copy the built JAR file from the 'build' stage to the current stage.
# The JAR file is typically found in 'target' directory and named 'your-app-name.jar'.
# Since your project is called 'demo', the JAR file will likely be named 'demo-VERSION.jar'
# or similar. We'll copy it as 'demo.jar' inside the container for simplicity.
COPY --from=build /app/target/*.jar demo.jar

# Expose the port your Spring Boot application listens on (default is 8080)
EXPOSE 8080

# Define the command to run your Spring Boot application.
# 'java -jar demo.jar' executes the packaged application.
ENTRYPOINT ["java", "-jar", "demo.jar"]