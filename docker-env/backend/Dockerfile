FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080 8081

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
