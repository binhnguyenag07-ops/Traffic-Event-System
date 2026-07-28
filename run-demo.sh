#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
npm --prefix frontend ci
npm --prefix frontend run build
mvn -q -DskipTests clean package
exec java -jar target/traffic-event-system-1.0-SNAPSHOT.jar
