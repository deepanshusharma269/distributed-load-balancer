FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY gateway/pom.xml gateway/pom.xml
COPY mock-inference/pom.xml mock-inference/pom.xml
COPY mock-inference/src mock-inference/src
RUN mvn --batch-mode -pl mock-inference package -DskipTests

FROM eclipse-temurin:21-jre
ENV JAVA_TOOL_OPTIONS="-Xmx128m"
WORKDIR /app
COPY --from=build /workspace/mock-inference/target/mock-inference-1.0.0.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
