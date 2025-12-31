#!/bin/bash
set -e

mkdir -p src/main/java/com/utdmod/manager

# Move managers if they exist outside manager/
mv src/main/java/com/utdmod/TensionManager.java src/main/java/com/utdmod/manager/ 2>/dev/null || true
mv src/main/java/com/utdmod/DynamicDifficultyScaler.java src/main/java/com/utdmod/manager/ 2>/dev/null || true

# RitualBlock already isolated
mv src/main/java/com/utdmod/RitualBlock.java src/main/java/com/utdmod/blocks/ 2>/dev/null || true

echo "Manager isolation complete."
