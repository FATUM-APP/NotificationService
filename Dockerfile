# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero el pom: si no cambian las dependencias, esta capa se reutiliza.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- run ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r fatum && useradd -r -g fatum fatum
COPY --from=build /app/target/*.jar app.jar
USER fatum

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
