FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy the built jar into the image
COPY target/rate-limiter-api-1.0.0.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]