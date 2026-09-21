FROM maven:3.9.12-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -q verify
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S forgeloop && adduser -S forgeloop -G forgeloop
WORKDIR /app
COPY --from=build /app/target/control-plane-0.1.0.jar app.jar
USER forgeloop
EXPOSE 8090
ENTRYPOINT ["java","-jar","app.jar"]
