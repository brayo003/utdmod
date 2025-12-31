#!/bin/bash
set -e

# Remove unused, abandoned, or placeholder code
rm -f src/main/java/com/utdmod/debug/* 2>/dev/null || true
rm -f src/main/java/com/utdmod/experimental/* 2>/dev/null || true
rm -f src/main/java/com/utdmod/unused/* 2>/dev/null || true
rm -f src/main/java/com/utdmod/entities/Placeholder*.java 2>/dev/null || true
rm -f src/main/java/com/utdmod/world/Old*.java 2>/dev/null || true

# Ensure structure exists
mkdir -p src/main/java/com/utdmod/manager
mkdir -p src/main/java/com/utdmod/blocks

# Move isolated managers
mv src/main/java/com/utdmod/TensionManager.java src/main/java/com/utdmod/manager/ 2>/dev/null || true
mv src/main/java/com/utdmod/DynamicDifficultyScaler.java src/main/java/com/utdmod/manager/ 2>/dev/null || true

# Move RitualBlock if needed
mv src/main/java/com/utdmod/RitualBlock.java src/main/java/com/utdmod/blocks/ 2>/dev/null || true

echo "Cleanup + isolation done."
