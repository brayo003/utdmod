#!/bin/bash
echo "=== UTD MOD REALITY CHECK ==="
echo ""
echo "1. WARDING CRYSTAL EXISTS?"
if [ -f "src/main/java/com/utdmod/items/WardingCrystal.java" ]; then
    echo "   ✅ Class exists - $(grep -c "public\|private" src/main/java/com/utdmod/items/WardingCrystal.java) methods"
    cat src/main/java/com/utdmod/items/WardingCrystal.java | grep -A5 "public class"
else
    echo "   ❌ NO WardingCrystal.java file"
fi

echo ""
echo "2. PLAYER STATE TENSION ACTUAL CODE?"
if [ -f "src/main/java/com/utdmod/signals/PlayerStateTension.java" ]; then
    cat src/main/java/com/utdmod/signals/PlayerStateTension.java | grep -v "^[[:space:]]*$" | grep -v "^//\|^/\*" | head -10
    echo "   Total non-empty lines: $(cat src/main/java/com/utdmod/signals/PlayerStateTension.java | grep -v "^[[:space:]]*$" | grep -v "^//\|^/\*" | wc -l)"
else
    echo "   ❌ NO PlayerStateTension.java file"
fi

echo ""
echo "3. BIOME EFFECTS ACTUAL CODE?"
if [ -f "src/main/java/com/utdmod/signals/BiomeTensionEffects.java" ]; then
    cat src/main/java/com/utdmod/signals/BiomeTensionEffects.java | grep -v "^[[:space:]]*$" | grep -v "^//\|^/\*" | head -10
    echo "   Total non-empty lines: $(cat src/main/java/com/utdmod/signals/BiomeTensionEffects.java | grep -v "^[[:space:]]*$" | grep -v "^//\|^/\*" | wc -l)"
else
    echo "   ❌ NO BiomeTensionEffects.java file"
fi

echo ""
echo "4. PROFILE LOGGING ACTIVE?"
grep -l "UTDProfiler\|log\|LOGGER" src/main/java/com/utdmod/signals/*.java 2>/dev/null | while read f; do
    echo "   $(basename $f) has logging"
done

echo ""
echo "5. WARDING CRYSTAL RECIPE EXISTS?"
find src/main/resources -name "*warding*" -o -name "*Warding*" 2>/dev/null | while read f; do
    echo "   ✅ Found: $f"
    cat "$f" | head -5
done

echo ""
echo "6. DYNAMIC EVENTS WIRED TO TENSION?"
grep -l "getTension\|SmallSignal" src/main/java/com/utdmod/*.java src/main/java/com/utdmod/**/*.java 2>/dev/null | while read f; do
    lines=$(grep -c "getTension\|SmallSignal" "$f")
    echo "   $(basename $f) has $lines references"
done

echo ""
echo "7. PLAYER ACTIONS AFFECT TENSION?"
grep -l "PlayerBlockBreak\|PlayerInteract\|addTension\|update.*tension" src/main/java/com/utdmod/*.java src/main/java/com/utdmod/**/*.java 2>/dev/null | while read f; do
    echo "   $(basename $f) connects actions to tension"
done

echo ""
echo "8. BIOME-SPECIFIC EFFECTS?"
grep -l "Biome\|biome" src/main/java/com/utdmod/signals/*.java 2>/dev/null | while read f; do
    echo "   $(basename $f) mentions biomes"
done

echo ""
echo "9. ASYNC/DEFERRED CODE?"
grep -l "Thread\|Executor\|CompletableFuture" src/main/java/com/utdmod/*.java src/main/java/com/utdmod/**/*.java 2>/dev/null | while read f; do
    echo "   $(basename $f) has async code"
done

echo ""
echo "=== SUMMARY ==="
echo "Run 'cat' on these files to see if they're empty stubs:"
echo "cat src/main/java/com/utdmod/signals/PlayerStateTension.java"
echo "cat src/main/java/com/utdmod/signals/BiomeTensionEffects.java"
echo "cat src/main/java/com/utdmod/signals/TensionWorldEffects.java"
echo ""
echo "Run 'grep' to check connections:"
echo "grep -r 'getTension\|SmallSignal' src/main/java/com/utdmod/items/"
echo "grep -r 'getTension\|SmallSignal' src/main/java/com/utdmod/blocks/"
