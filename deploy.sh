#!/bin/bash
set -e

# Path as seen from this container
PLUGIN_DIR="/docker-compose/minecraft/plugin-dev/my-first-plugin"
PLUGINS_DIR="/docker-compose/minecraft/data/plugins"

# Path as seen from the host (needed for docker run volume mounts)
HOST_PLUGIN_DIR="/mnt/storage/docker-compose/minecraft/plugin-dev/my-first-plugin"

JAR="my-first-plugin-1.0.jar"

echo "Building plugin..."
docker run --rm \
  -v "$HOST_PLUGIN_DIR:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn clean package -q

echo "Deploying JAR..."
docker cp "$PLUGIN_DIR/target/$JAR" "minecraft:/data/plugins/$JAR"
docker exec minecraft rm -f "/data/plugins/.paper-remapped/$JAR"

echo "Fixing permissions..."
docker exec minecraft chmod -R 777 /data/plugins

echo "Restarting server..."
docker restart minecraft

echo "Done!"
