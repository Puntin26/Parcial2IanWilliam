# --- Etapa 1: Solo copia el JAR ya compilado ---
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY build/libs/app.jar app.jar

EXPOSE 7000

ENTRYPOINT ["java", "-jar", "app.jar"]
