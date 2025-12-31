#!/bin/bash

Clean, build, and run the mod with log collection
Step 1: Clean previous builds

./gradlew clean

Step 2: Build the mod

./gradlew build

Step 3: Run client with logging

LOG_FILE="mod_run_$(date +%Y%m%d_%H%M%S).log"
./gradlew runClient | tee "$LOG_FILE"

echo "Mod run completed. Logs saved to $LOG_FILE"
