# Debug Build Complete — Ready for Testing

## Status: ✅ READY TO TEST

**Compilation**: ✅ SUCCESS  
**All debug markers**: ✅ IN PLACE  
**Visibility multipliers**: ✅ APPLIED  
**Persistence logging**: ✅ ENABLED  

---

## What You're Testing

This debug build addresses **two core issues**:

### Problem 1: Client Visuals Show Zero Tension
**Root Cause**: Either sync packet not sent, client not receiving, or thresholds too high.

**Fixes Applied**:
1. ✅ Added `[CLIENT_DEBUG]` output (every 60 frames) showing perceived tension
2. ✅ Mining injection × 25 so visible escalation happens in minutes, not hours
3. ✅ Lowered state thresholds (T1: 0.70→0.08, T2: 1.40→0.18)
4. ✅ Aggressive HUD red-bar override when tension > 0.05

### Problem 2: Tension Vanishes When Player Moves Away
**Root Cause**: Either decay too aggressive or NBT persistence failing.

**Fixes Applied**:
1. ✅ Added `[NBT_WRITE]` / `[NBT_READ]` logging to track serialization
2. ✅ Maintained existing decay rates (not changed) to isolate persistence bugs

---

## Quick Start

### Step 1: Start Server
```bash
cd /home/lenovo/Git/new_world
./gradlew :runServer
```

### Step 2: Load World & Mine
1. Start Minecraft client (connected to above server)
2. Load a world
3. Find stone and break 3–5 blocks
4. **Watch for RED BAR on screen** + console prints

### Step 3: Check Logs
```bash
# Terminal window showing server output:
grep "\[HUD_DEBUG\]" <stdout>
grep "\[CLIENT_DEBUG\]" <stdout>
grep "\[NBT_" <stdout>
```

### Step 4: Test Persistence
1. Mine area to tension > 0.2 (2–3 minutes)
2. Exit world (watch for NBT_WRITE)
3. Re-enter (watch for NBT_READ)
4. Verify loaded ≈ saved

---

## Expected Behavior

### Immediate (within 30 seconds)
✅ Break stone → `[HUD_DEBUG] TENSION_DETECTED` prints  
✅ Red bar appears on HUD  
✅ Action bar shows: "Chunk Tension: 0.XX (STRAINED)"

### Short Term (2–3 minutes)
✅ State escalates: STABLE → STRAINED → FRACTURED  
✅ Tension builds up (not stuck at 0)  
✅ Visual feedback constant (red bar doesn't flicker)

### Persistence (10 minutes)
✅ Exit world → `[NBT_WRITE]` shows non-zero  
✅ Re-enter → `[NBT_READ]` shows same value  
✅ Tension still visible in same chunk

---

## Diagnostic Decision Tree

### ❓ Red bar never appears?
1. Check: `[HUD_DEBUG]` in console?
   - If NO → mining not injected (check break event listener)
   - If YES → HUD not rendering (check TensionHud.render registration)

### ❓ Server shows tension but client doesn't?
1. Check: `[CLIENT_DEBUG]` printing?
   - If NO → sync packet not received (check registerReceiver called)
   - If YES → smoothing broken (check TensionSyncState.tickSmoothing)

### ❓ Tension disappears after moving away?
1. Check: `[NBT_WRITE]` before exit?
   - If NO → never saved (tension too weak to reach persistence threshold)
   - If YES → check `[NBT_READ]` on reload
     - If matches → working (move on)
     - If zero → deserialization bug (check fromNbt code)

### ❓ Decay too aggressive?
1. If `[NBT_READ]` shows saved=0.5 but now=0.05 after 5 minutes
2. Check: `LAMBDA` constant in TensionServerTick.java
3. Reduce from 0.0006 to 0.0003 and retest

---

## Files Modified (4 total)

| File | Change | Type |
|------|--------|------|
| `UTDModClient.java` | Add client debug log every 60 frames | Debug output |
| `ChunkTensionManager.java` | Mining × 25 multiplier | Visibility |
| `ChunkTensionData.java` | Thresholds T1/T2 lowered + NBT logging | Visibility + Persistence |
| `TensionHud.java` | Red bar override when tension > 0.05 | Visibility |

---

## After Debug Testing: Reversion

1. Follow `REVERT_DEBUG_CHANGES.md` to remove all multipliers/logging
2. Re-run test scenarios: `./gradlew :runTests`
3. Grep for leftover debug markers: `grep -r "DEBUG_MINING_MULT\|HUD_DEBUG" src/`
4. Commit clean reversion

---

## Test Scenarios (Should Still Pass)

These test scenarios should work unchanged after debug phase:

```bash
./gradlew :runTests --tests "DiffusionObservableScenario"
./gradlew :runTests --tests "RitualMitigationScenario"
./gradlew :runTests --tests "ScarReminingScenario"
./gradlew :runTests --tests "SingleChunkFractureScenario"
./gradlew :runTests --tests "SaveLoadIntegrityScenario"
```

Expected: All green (debug multipliers don't break test logic, only scale inputs)

---

## Timeline Example (Success Case)

| Time | Action | Console Output |
|------|--------|-----------------|
| 0:00 | Break stone #1 | `[UTDMod] Global Tension: 0.00` |
| 0:05 | Break stones 2–5 | `[HUD_DEBUG] TENSION_DETECTED t=0.10` |
| 0:10 | Look at HUD | **RED BAR appears** |
| 0:30 | Continue mining | `Chunk Tension: 0.15 (STRAINED)` |
| 1:00 | More mining | `Chunk Tension: 0.22 (FRACTURED)` |
| 5:00 | Exit world | `[NBT_WRITE] chunk (X,Z) tension=0.25` |
| 5:05 | Re-enter world | `[NBT_READ] chunk (X,Z) tension=0.25` |
| ✅ | Walk to chunk | Red bar still visible |

**Conclusion**: All systems working. Ready for tuning.

---

## Support

If a test fails:

1. **Check logs first**: `logs/latest.log | tail -200`
2. **Correlate with timeline**: Was output expected at that time?
3. **Isolate which component**: Use decision tree above
4. **Reference**: See `QUICK_DEBUG_CHECKLIST.md` and `DEBUG_TESTING_GUIDE.md` for detailed diagnostics

---

## Build Info

```
Date: May 24, 2026
Status: DEBUG MODE ENABLED
Multiplier: 25x (mining injection)
Thresholds: T1=0.08, T2=0.18, T3=0.40
Compilation: ✅ SUCCESS
Ready: ✅ YES
```

**Next Step**: Start `./gradlew :runServer` and test!
