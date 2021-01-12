#!/usr/bin/env bash

./gradlew jibDockerBuild

trap "docker-compose down" EXIT

docker-compose up -d --scale providers=10

xdg-open http://localhost:8080

echo "Press any key to stop ..."
read