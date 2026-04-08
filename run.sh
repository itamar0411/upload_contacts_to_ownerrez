#!/bin/bash
set -e
mvn -q package -DskipTests
java -jar target/upload-contacts-to-ownerrez-1.0-SNAPSHOT.jar "$@"
