FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY target/SmartGram-Home-Service-1.0-SNAPSHOT.jar app.jar
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
