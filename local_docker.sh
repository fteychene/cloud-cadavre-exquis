#!/usr/bin/env bash

./gradlew jibDockerBuild

trap "docker-compose down" EXIT

docker-compose up -d --scale providers-subject=3 --scale providers-verb=3 --scale providers-adj=3

xdg-open http://localhost:8080

echo "Press any key to stop ..."
read