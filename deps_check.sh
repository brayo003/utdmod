#!/bin/bash
# Script to check Minecraft-related dependencies in Gradle project

./gradlew --refresh-dependencies
./gradlew dependencies > deps.txt
grep -i 'minecraft' deps.txt
grep -i 'fabric' deps.txt
grep -i 'loom' deps.txt
