FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY gateway/pom.xml gateway/pom.xml
COPY mock-inference/pom.xml mock-inference/pom.xml
COPY gateway/src gateway/src
RUN mvn --batch-mode -pl gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
ENV JAVA_TOOL_OPTIONS="-Xmx128m"
WORKDIR /app
COPY --from=build /workspace/gateway/target/gateway-1.0.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
