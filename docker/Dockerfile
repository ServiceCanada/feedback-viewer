# Build stage
FROM maven:3.8.3-jdk-8 AS build
WORKDIR /build

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Copy decrypted whitelist file (decrypted in GitHub Actions)
COPY secrets/gc_ip_whitelist.txt ./secrets/

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:8-jre-jammy

WORKDIR /app

# Install required tools
RUN apt-get update && \
    apt-get install -y wget ca-certificates openssl perl && \
    rm -rf /var/lib/apt/lists/*

# Create truststore directory
RUN mkdir -p /app/certs

# Download and create truststore with AWS DocumentDB certificates
# Following AWS official documentation: https://docs.aws.amazon.com/documentdb/latest/developerguide/connect_programmatically.html
RUN cd /app/certs && \
    wget -O global-bundle.pem https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem && \
    awk 'split_after == 1 {n++;split_after=0} /-----END CERTIFICATE-----/ {split_after=1}{print > "rds-ca-" n ".pem"}' < global-bundle.pem && \
    for CERT in rds-ca-*.pem; do \
    alias=$(openssl x509 -noout -text -in $CERT | perl -ne 'next unless /Subject:/; s/.*(CN=|CN = )//; print'); \
    echo "Importing $alias"; \
    keytool -import -file ${CERT} -alias "${alias}" -storepass changeit -keystore rds-truststore.jks -noprompt; \
    rm $CERT; \
    done && \
    rm global-bundle.pem

# Verify truststore contents
RUN keytool -list -keystore /app/certs/rds-truststore.jks -storepass changeit

# Create config directory for whitelist file
RUN mkdir -p /app/config

# Copy application JAR and decrypted whitelist file from build stage
COPY --from=build /build/target/PageSuccess-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /build/secrets/gc_ip_whitelist.txt /app/config/gc_ip_whitelist.txt

ENV SERVER_PORT=3001
ENV GC_IP_FILTER_WHITELIST_FILE=/app/config/gc_ip_whitelist.txt
EXPOSE 3001

# Set JVM system properties to use the custom truststore
ENTRYPOINT ["java", \
    "-Djavax.net.ssl.trustStore=/app/certs/rds-truststore.jks", \
    "-Djavax.net.ssl.trustStorePassword=changeit", \
    "-jar", "app.jar"]
