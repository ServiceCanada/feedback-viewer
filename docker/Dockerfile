# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
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
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install required tools
RUN apt-get update && \
    apt-get install -y wget ca-certificates openssl perl && \
    rm -rf /var/lib/apt/lists/*

# Download and import AWS DocumentDB certificates into the JVM default cacerts.
# This extends the default truststore (which contains standard public CAs) rather
# than replacing it, so that outbound HTTPS calls to public APIs (e.g. RDAP) still work.
# Following AWS official documentation: https://docs.aws.amazon.com/documentdb/latest/developerguide/connect_programmatically.html
RUN mkdir -p /app/certs && \
    cd /app/certs && \
    wget -O global-bundle.pem https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem && \
    awk 'split_after == 1 {n++;split_after=0} /-----END CERTIFICATE-----/ {split_after=1}{print > "rds-ca-" n ".pem"}' < global-bundle.pem && \
    for CERT in rds-ca-*.pem; do \
    alias=$(openssl x509 -noout -text -in $CERT | perl -ne 'next unless /Subject:/; s/.*(CN=|CN = )//; print'); \
    echo "Importing $alias"; \
    keytool -import -file ${CERT} -alias "${alias}" -storepass changeit \
        -keystore $JAVA_HOME/lib/security/cacerts -noprompt; \
    rm $CERT; \
    done && \
    rm global-bundle.pem && \
    rmdir /app/certs

# Create config directory for whitelist file
RUN mkdir -p /app/config

# Copy application JAR and decrypted whitelist file from build stage
COPY --from=build /build/target/PageSuccess-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /build/secrets/gc_ip_whitelist.txt /app/config/gc_ip_whitelist.txt

ENV SERVER_PORT=3001
ENV GC_IP_FILTER_WHITELIST_FILE=/app/config/gc_ip_whitelist.txt
EXPOSE 3001

ENTRYPOINT ["java", "-jar", "app.jar"]
