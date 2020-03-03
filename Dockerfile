FROM maven:3.5-jdk-8-alpine AS build
WORKDIR /app
COPY . /app/
RUN mvn clean package

FROM openjdk:latest
WORKDIR /app
COPY --from=build /app/target/ /app
COPY --from=build /app/res/ /app/res
CMD java -jar discord-bot-1.0-SNAPSHOT-jar-with-dependencies.jar
