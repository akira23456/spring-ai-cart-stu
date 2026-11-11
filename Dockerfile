# Build the application using Maven and JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Run the application using a smaller JRE-only image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /work/target/spring-ai-cart-0.1.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
