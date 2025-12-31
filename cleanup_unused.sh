#!/bin/bash
set -e

# Remove debug or unused systems
rm -f src/main/java/com/utdmod/debug/* 2>/dev/null || true
rm -f src/main/java/com/utdmod/experimental/* 2>/dev/null || true
rm -f src/main/java/com/utdmod/unused/* 2>/dev/null || true
rm -f src/main/java/com/utdmod/entities/Placeholder*.java 2>/dev/null || true
rm -f src/main/java/com/utdmod/world/Old*.java 2>/dev/null || true

# Ensure directories exist after cleanup
mkdir -p src/main/java/com/utdmod/manager
mkdir -p src/main/java/com/utdmod/util

echo "Cleanup complete."
