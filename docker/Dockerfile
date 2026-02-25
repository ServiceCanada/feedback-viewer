FROM maven:3.9.9-eclipse-temurin-21
RUN rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y \
    # add any necessary packages here
    && apt-get clean
RUN mkdir -p /app
ADD target/PageSuccess-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
