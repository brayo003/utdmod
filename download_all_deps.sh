#!/bin/bash
echo "Downloading all Minecraft 1.21.4 dependencies..."

# Create directories
mkdir -p ~/.gradle/caches/fabric-loom
mkdir -p ~/.gradle/caches/modules-2/files-2.1

# Download Minecraft client and server
echo "Downloading Minecraft 1.21.4..."
cd /tmp
wget --no-check-certificate https://piston-data.mojang.com/v1/objects/84194a2f286ef7c14ed7ce0090dba59902951553/client.jar
wget --no-check-certificate https://piston-data.mojang.com/v1/objects/84194a2f286ef7c14ed7ce0090dba59902951553/server.jar

# Download Fabric dependencies
echo "Downloading Fabric dependencies..."
wget --no-check-certificate https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.15.10/fabric-loader-0.15.10.jar
wget --no-check-certificate https://maven.fabricmc.net/net/fabricmc/fabric-api/0.100.1+1.21.4/fabric-api-0.100.1+1.21.4.jar
wget --no-check-certificate https://maven.fabricmc.net/net/fabricmc/yarn/1.21.4+build.1/yarn-1.21.4+build.1-v2.jar

echo "Done! Files downloaded to /tmp/"
