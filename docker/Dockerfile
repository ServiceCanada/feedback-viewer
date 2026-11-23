FROM eclipse-temurin:21.0.13.1_2-jre-alpine
RUN mkdir -p /app
COPY target/PageSuccess-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
