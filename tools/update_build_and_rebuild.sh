#!/bin/bash

# Update Loom to compatible version in build.gradle
sed -i "s/classpath 'net.fabricmc:fabric-loom:1.6.12'/classpath 'net.fabricmc:fabric-loom:1.7-SNAPSHOT'/" build.gradle

# Refresh Gradle dependencies
./gradlew --refresh-dependencies

# Clean previous build
./gradlew clean

# Build project
./gradlew build
