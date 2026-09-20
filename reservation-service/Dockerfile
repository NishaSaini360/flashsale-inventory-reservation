# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Copy only poms first so dependency download is cached across code changes.
COPY pom.xml .
COPY commons/pom.xml             commons/
COPY inventory-service/pom.xml   inventory-service/
COPY reservation-service/pom.xml reservation-service/
COPY order-service/pom.xml       order-service/
RUN mvn -B -pl reservation-service -am dependency:go-offline -DskipTests

# Now the sources.
COPY commons/src             commons/src
COPY reservation-service/src reservation-service/src
RUN mvn -B -pl reservation-service -am package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /src/reservation-service/target/*.jar app.jar
USER appuser
EXPOSE 8082
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
