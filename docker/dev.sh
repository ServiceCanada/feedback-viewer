#!/bin/sh
# Local dev entrypoint for docker-compose: runs the app and recompiles Java on save.
# Devtools restarts the app only when the trigger file is touched, i.e. after a
# successful compile, so a broken edit leaves the running app alone.
STAMP=/tmp/last-compile
touch "$STAMP"

(
  while sleep 2; do
    if [ -n "$(find src/main/java -name '*.java' -newer "$STAMP" -print -quit)" ]; then
      touch "$STAMP"
      echo "Java change detected, recompiling..."
      mvn -q -o compile && touch target/classes/.reloadtrigger
    fi
  done
) &

exec mvn -q spring-boot:run \
  -Dspring-boot.run.addResources=true \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000"
