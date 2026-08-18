# --- Stage 1 : build (Maven + JDK 25) ---
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Cache dependencies first (only re-resolve when pom.xml changes)
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# --- Stage 2 : runtime (JRE 25, non-root) ---
FROM eclipse-temurin:25-jre
RUN useradd --create-home --uid 10001 appuser
USER appuser
WORKDIR /app

COPY --from=build /build/target/demo-chatbot-agentscope-back-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
