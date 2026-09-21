FROM maven:3.9.12-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
# The worktree integration test exercises the same Git primitive used by a runner.
RUN apk add --no-cache git
COPY src src
RUN mvn -q test package
FROM eclipse-temurin:21-jre-alpine
# The Docker CLI is used only for policy-selected disposable verification containers.
# Operators must explicitly mount a daemon socket when enabling this capability.
RUN apk add --no-cache docker-cli git && addgroup -S forgeloop && adduser -S forgeloop -G forgeloop
WORKDIR /app
COPY --from=build /app/target/runner-0.1.0.jar runner.jar
RUN mkdir /state && chown forgeloop:forgeloop /state
VOLUME ["/state"]
ENV FORGELOOP_RUNNER_STATE_FILE=/state/runner
USER forgeloop
ENTRYPOINT ["java", "-cp", "runner.jar", "io.forgeloop.runner.RunnerMain"]
