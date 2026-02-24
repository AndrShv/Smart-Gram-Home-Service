FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update && apt-get install -y \
    libfreetype6 \
    fontconfig \
    libx11-6 \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/SmartGram-Home-Service-1.0-SNAPSHOT.jar app.jar
EXPOSE 8083

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]