FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .
COPY src src

RUN chmod +x gradlew
RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/build/libs/order-service.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
