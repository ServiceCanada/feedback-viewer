FROM eclipse-temurin:21-jre-alpine
# Java4Shell (CVE-2026-114938) workaround: turn off the vulnerable Base64 intrinsic.
# Remove once the base image ships Temurin 21.0.13.1+2 or later.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
RUN mkdir -p /app
COPY target/PageSuccess-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
