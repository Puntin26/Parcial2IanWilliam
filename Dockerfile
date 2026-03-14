# --- Etapa 1: Construcción (Build) ---
FROM gradle:8.7-jdk25 AS builder
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src ./src

# Usamos 'build' normal en lugar de 'shadowJar'
RUN gradle clean build -x test

# --- Etapa 2: Ejecución (Runtime) ---
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copiamos el JAR generado nativamente (ahora se llama app.jar directamente)
COPY --from=builder /app/build/libs/app.jar app.jar

EXPOSE 7000

ENTRYPOINT ["java", "-jar", "app.jar"]