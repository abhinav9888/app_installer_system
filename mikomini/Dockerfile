# Use Eclipse Temurin JDK 17
FROM eclipse-temurin:17-jdk-alpine

# Create app directory
WORKDIR /appinstall

# Copy the jar file (update filename as needed)
COPY target/mikomini-0.0.1-SNAPSHOT.jar mikomini-0.0.1-SNAPSHOT.jar

# Expose port 8080 (Vert.x default)
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "mikomini-0.0.1-SNAPSHOT.jar"]
