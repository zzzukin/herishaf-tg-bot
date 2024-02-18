FROM maven:3.8.6-openjdk-8 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:8-jdk-slim
COPY --from=build /target/heryshaf-tg-bot-0.0.1-SNAPSHOT.jar heryshaf-tg-bot.jar
ENTRYPOINT ["java","-jar","heryshaf-tg-bot.jar"]
