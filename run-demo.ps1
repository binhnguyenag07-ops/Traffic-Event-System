$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
npm --prefix frontend ci
npm --prefix frontend run build
mvn -q -DskipTests package
java -jar target/traffic-event-system-1.0-SNAPSHOT.jar
