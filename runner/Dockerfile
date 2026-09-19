FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN wget -q https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.tar.gz -O /tmp/maven.tgz && tar -xzf /tmp/maven.tgz -C /opt
COPY src src
RUN /opt/apache-maven-3.9.12/bin/mvn -q test package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S forgeloop && adduser -S forgeloop -G forgeloop
WORKDIR /app
COPY --from=build /app/target/runner-0.1.0.jar runner.jar
RUN mkdir /state && chown forgeloop:forgeloop /state
VOLUME ["/state"]
ENV FORGELOOP_RUNNER_STATE_FILE=/state/runner
USER forgeloop
ENTRYPOINT ["java", "-cp", "runner.jar", "io.forgeloop.runner.RunnerMain"]
