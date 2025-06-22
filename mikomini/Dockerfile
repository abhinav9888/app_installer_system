# Use Eclipse Temurin JDK 17 (lightweight Alpine version)
FROM eclipse-temurin:17-jdk-alpine

# Set working directory inside the container
WORKDIR /mikomini

# Copy the JAR file into the container and rename it to app.jar
COPY target/mikomini-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port (default 8080 for Vert.x/Spring Boot)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
