# Debug Build Summary — Complete Visibility & Persistence Testing

## Overview
This debug build applies **6 targeted fixes** to expose the tension system plumbing and force visibility. All changes are **temporary** and clearly marked for easy reversion after testing.

---

## Changes Applied

### 1. Client Debug Logging — `UTDModClient.java:26-35`
**What**: Added `frameCount` and 60-tick periodic printing of `TensionSyncState` values.

**Code**:
```java
private static int frameCount = 0;
// ...
if (frameCount++ % 60 == 0) {
    double perceived = TensionSyncState.perceivedTension();
    double raw = TensionSyncState.CLIENT_TENSION;
    double local = TensionSyncState.CLIENT_LOCAL_TENSION;
    System.out.println("[CLIENT_DEBUG] frame=" + frameCount + " perceived=...");
}
```

**Expected Output**:
```
[CLIENT_DEBUG] frame=60 perceived=0.000000 raw_global=0.000000 local=0.000000
[CLIENT_DEBUG] frame=120 perceived=0.050000 raw_global=0.025000 local=0.125000
```

**Diagnosis**:
- All zeros → sync packet not arriving or not processed
- Non-zero → sync plumbing working

---

### 2. Mining Injection × 25 — `ChunkTensionManager.java:90`
**What**: Added `DEBUG_MINING_MULT = 25.0` multiplier to all mining baseAmount values.

**Before**:
```java
baseAmount = 0.042;  // stone
```

**After**:
```java
final double DEBUG_MINING_MULT = 25.0;
baseAmount = 0.042 * DEBUG_MINING_MULT;  // stone = 1.05
```

**Effect**: Break 1–2 stone → local_tension reaches 0.08–0.15 (visible state)

**Mining Injection Table**:
| Block | Old | Debug | Multiplier |
|-------|-----|-------|-----------|
| Stone | 0.042 | 1.05 | 25x |
| Coal | 0.056 | 1.40 | 25x |
| Ore | 0.052 | 1.30 | 25x |
| Deepslate | 0.048 | 1.20 | 25x |
| Diamond | 0.11 | 2.75 | 25x |
| Ancient Debris | 0.14 | 3.50 | 25x |

---

### 3. Debug State Thresholds — `ChunkTensionData.java:28-30`
**What**: Lowered T1, T2 to match debug mining scale.

**Before**:
```java
private static final double T1 = 0.70;   // STRAINED
private static final double T2 = 1.40;   // FRACTURED
private static final double T3 = 3.0;    // DECOUPLED
```

**After**:
```java
private static final double T1 = 0.08;   // STRAINED (triggers at 8% tension)
private static final double T2 = 0.18;   // FRACTURED (triggers at 18% tension)
private static final double T3 = 0.40;   // DECOUPLED (triggers at 40% tension)
```

**Effect**: State transitions now visible within 1–5 minutes of mining.

---

### 4. Aggressive HUD Override — `TensionHud.java:20-46`
**What**: Added red-bar override when `local_tension > 0.05`.

**Code**:
```java
if (t > 0.05) {
    // Show BRIGHT RED bar
    context.fill(x, y, x+barW, y+barH, 0xCC330000);  // dark red bg
    int fill = (int)(Math.min(1.0, t/0.25) * barW);
    context.fill(x, y, x+fill, y+barH, 0xFFFF0000);  // bright red fill
    context.drawText(..., "⚠ TENSION SPIKE ⚠", ..., 0xFFFFFF, false);
    System.out.println("[HUD_DEBUG] TENSION_DETECTED t=" + ...);
}
```

**Expected In-Game**:
- Red bar fills from left when mining
- "⚠ TENSION SPIKE ⚠" warning text flashes
- Console prints every ~1 sec while tension active

---

### 5. NBT Persistence Logging — `ChunkTensionData.java:476-480, 494-502`
**What**: Added debug prints in `writeNbt()` and `fromNbt()`.

**On Save**:
```java
System.out.println("[NBT_WRITE] chunk " + pos + " tension=" + 
    String.format("%.6f", tension) + " scar=" + String.format("%.6f", scar));
```

**On Load**:
```java
System.out.println("[NBT_READ] chunk " + pos + " tension=" + 
    String.format("%.6f", loadedTension) + " scar=" + String.format("%.6f", loadedScar));
```

**Expected Output**:
```
[NBT_WRITE] chunk (125,200) tension=0.250000 scar=0.000000
[NBT_WRITE] chunk (126,200) tension=0.180000 scar=0.000000
[NBT_READ] chunk (125,200) tension=0.250000 scar=0.000000
[NBT_READ] chunk (126,200) tension=0.180000 scar=0.000000
```

---

## How to Test

### Step 1: Compile
```bash
cd /home/lenovo/Git/new_world
./gradlew :compileJava -q
```

### Step 2: Run Server
```bash
./gradlew :runServer
```

### Step 3: Load World & Mine
1. Create/load a world
2. Move to a mining area (stone level)
3. Break 3–5 stone blocks
4. **Watch for**:
   - Console: `[HUD_DEBUG] TENSION_DETECTED`
   - In-game: Red bar + warning text
   - Action bar: "Chunk Tension: 0.XX (STRAINED)"

### Step 4: Verify Persistence
1. Mine to FRACTURED state (2–3 minutes)
2. Exit world (watch for `[NBT_WRITE]`)
3. Re-enter world (watch for `[NBT_READ]`)
4. **Expected**: Loaded tension ≈ saved tension

### Step 5: Check All Logs
```bash
tail -100 logs/latest.log | grep -E "\[CLIENT_DEBUG\]|\[HUD_DEBUG\]|\[NBT_|Global Tension"
```

---

## Expected Results

### ✅ Success
```
[UTDMod] Global Tension: 0.00 | Storm: false
[CLIENT_DEBUG] frame=60 perceived=0.000000 raw_global=0.000000 local=0.000000
[HUD_DEBUG] TENSION_DETECTED t=0.125000
[HUD_DEBUG] TENSION_DETECTED t=0.235000
[NBT_WRITE] chunk (100,200) tension=0.350000 scar=0.000000
[NBT_READ] chunk (100,200) tension=0.350000 scar=0.000000
```
→ All systems working. Proceed to gameplay tuning.

### ❌ Sync Broken
```
[UTDMod] Global Tension: 0.25 | Storm: false
[CLIENT_DEBUG] frame=60 perceived=0.000000 raw_global=0.000000 local=0.000000
```
→ Server has tension, client doesn't. Debug `TensionSyncPacket.registerReceiver()`.

### ❌ Mining Not Injecting
```
[HUD_DEBUG] TENSION_DETECTED never appears
[NBT_WRITE] never appears with non-zero tension
```
→ Mining event not hooked. Check `PlayerBlockBreakEvent` listener.

### ❌ Persistence Broken
```
[NBT_WRITE] chunk (100,200) tension=0.350000 scar=0.000000
[NBT_READ] chunk (100,200) tension=0.000000 scar=0.000000
```
→ NBT load failed. Check `fromNbt()` deserialization.

---

## Reverting to Production

After confirming all systems work:

1. **Remove mining multiplier**: Delete `final double DEBUG_MINING_MULT = 25.0;` and remove `* DEBUG_MINING_MULT` from all baseAmount assignments.

2. **Restore thresholds**:
   ```java
   private static final double T1 = 0.70;
   private static final double T2 = 1.40;
   private static final double T3 = 3.0;
   ```

3. **Comment out debug prints**:
   ```java
   // System.out.println("[CLIENT_DEBUG] ...");
   // System.out.println("[HUD_DEBUG] ...");
   // System.out.println("[NBT_WRITE] ...");
   // System.out.println("[NBT_READ] ...");
   ```

4. **Revert HUD to normal** (remove red override, keep state-based colors).

5. **Re-run test scenarios** to confirm original behavior preserved.

---

## Files Modified

| File | Changes | Lines |
|------|---------|-------|
| `UTDModClient.java` | Added frameCount & debug logging | 26–35 |
| `ChunkTensionManager.java` | Mining multiplier 25x | 90 |
| `ChunkTensionData.java` | Lowered thresholds + NBT logging | 28–30, 476–502 |
| `TensionHud.java` (client-only) | Red bar override | 20–46 |

---

## Build Status
✅ Compiles successfully (no new errors beyond RitualBlock deprecation warning)

---

## Notes
- Debug values are **obviously overpowered** — this is intentional. We're testing visibility, not gameplay.
- All changes preserve PDE logic — we only scale inputs/thresholds.
- Test scenarios should still pass (diffusion_bound, ritual_mitigation, etc.) because per-chunk mechanics unchanged.
- After debug phase, gameplay tuning can dial back multipliers independently.
