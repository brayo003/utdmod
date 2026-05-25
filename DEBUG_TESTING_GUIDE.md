# Debug Testing Guide — Tension Visibility & Persistence

## What Changed

### 1. **Client-Side Debug Logging** (UTDModClient.java)
```java
// Every 60 frames, print perceived tension
[CLIENT_DEBUG] frame=N perceived=X.XXXXXX raw_global=X.XXXXXX local=X.XXXXXX
```
**Purpose**: Identify sync plumbing issues. If all values read 0.0, the packet isn't arriving or smoothing isn't working.

### 2. **Mining Injection × 25** (ChunkTensionManager.java)
- Stone: 0.042 → **1.05**
- Ore: 0.052 → **1.30**
- Coal: 0.056 → **1.40**
- Deepslate: 0.048 → **1.20**
- Deepslate ore: 0.078 → **1.95**
- Diamond: 0.11 → **2.75**
- Emerald: 0.09 → **2.25**
- Ancient debris: 0.14 → **3.50**

**Target**: Break stone 1–2 times → local tension > 0.08 (STRAINED state).

### 3. **Debug Thresholds** (ChunkTensionData.java)
```java
T1 = 0.08  // STRAINED (was 0.70)
T2 = 0.18  // FRACTURED (was 1.40)
T3 = 0.40  // DECOUPLED (was 3.0)
```
**Effect**: Chunk states trigger at achievable tension levels.

### 4. **Aggressive HUD Override** (TensionHud.java client-side)
When `local_tension > 0.05`:
- Bar turns **bright RED**
- Label shows **"⚠ TENSION SPIKE ⚠"**
- Prints: `[HUD_DEBUG] TENSION_DETECTED t=X.XXXXXX`

**Purpose**: Instant visual proof that the system is working.

### 5. **NBT Persistence Logging** (ChunkTensionData.java)
```
[NBT_WRITE] chunk (x,z) tension=X.XXXXXX scar=X.XXXXXX
[NBT_READ] chunk (x,z) tension=X.XXXXXX scar=X.XXXXXX
```
**Purpose**: Verify tension survives chunk save/load cycles.

---

## Testing Checklist

### Phase 1: Sync Plumbing (5 min)
1. **Start server & load world**
2. **Watch console for `[CLIENT_DEBUG]`**
   - If all zeros → packet not arriving or client not receiving
   - If non-zero → sync is working
3. **Expected**: See numbers ramp up every 60 frames

### Phase 2: Mining Injection (5 min)
1. **Mine stone for 30 seconds**
2. **Watch for `[HUD_DEBUG] TENSION_DETECTED`**
3. **Expected**:
   - Red bar + warning text appears
   - HUD debug prints every ~1 sec

### Phase 3: State Transitions (5 min)
1. **Continue mining same chunk**
2. **Watch action bar** (server sends every 40 ticks):
   - "Chunk Tension: 0.XX (STRAINED) | ..."
   - Should escalate: STABLE → STRAINED → FRACTURED quickly
3. **Expected**: Visible state progression in 2–3 minutes

### Phase 4: Persistence (10 min)
1. **Mine a chunk to FRACTURED (local > 0.18)**
2. **Walk 100+ blocks away** (chunk unloads)
3. **Watch console for `[NBT_WRITE]`** (save chunk)
4. **Walk back** (chunk reloads)
5. **Watch console for `[NBT_READ]`** (load chunk)
6. **Expected**: Loaded tension ≈ saved tension (within 5%)

---

## Diagnostic Output Samples

### Healthy Sync
```
[CLIENT_DEBUG] frame=60 perceived=0.000000 raw_global=0.000000 local=0.000000
[CLIENT_DEBUG] frame=120 perceived=0.000000 raw_global=0.000000 local=0.000000
[HUD_DEBUG] TENSION_DETECTED t=0.125430
[HUD_DEBUG] TENSION_DETECTED t=0.245670
[NBT_WRITE] chunk (100,200) tension=0.245670 scar=0.000000
[NBT_READ] chunk (100,200) tension=0.245670 scar=0.000000
```

### Broken Sync
```
[CLIENT_DEBUG] frame=60 perceived=0.000000 raw_global=0.000000 local=0.000000
[CLIENT_DEBUG] frame=120 perceived=0.000000 raw_global=0.000000 local=0.000000
[SERVER log] [UTDMod] Global Tension: 0.24 | Storm: false
```
→ Server has tension but client never receives it. Check `TensionSyncPacket.registerReceiver()`.

### Weak Persistence
```
[NBT_WRITE] chunk (100,200) tension=0.245670 scar=0.000000
[NBT_READ] chunk (100,200) tension=0.000000 scar=0.000000  ← WRONG!
```
→ NBT load failed. Check `fromNbt` deserialization.

---

## Tuning Notes

**After visibility is confirmed**, dial back debug values:

| Parameter | Debug | Realistic | Notes |
|-----------|-------|-----------|-------|
| Mining multiplier | 25x | 1x | Restore normal injection |
| T1 threshold | 0.08 | 0.3–0.5 | Gameplay reach (tunable) |
| T2 threshold | 0.18 | 0.8–1.2 | Escalation speed |
| T3 threshold | 0.40 | 2.0–3.0 | Late-game danger |

---

## Common Issues & Fixes

| Issue | Symptom | Fix |
|-------|---------|-----|
| Client reads zero | `[CLIENT_DEBUG] perceived=0.000000` always | Check `TensionSyncPacket.registerReceiver()` called in `UTDModClient.onInitializeClient()` |
| Sync packet not sent | Server console shows tension, client doesn't | Verify `TensionSyncPacket.sendToAllPlayers` at line 93 of `TensionServerTick.java` runs every 20t |
| Mining doesn't raise tension | HUD never shows red | Check mining event is hooked (look for `[HUD_DEBUG]` spam after mining) |
| Tension drops on unload | `[NBT_READ]` shows zero after save | Verify `scarMemory.put(pos, scar)` in `fromNbt` at line ~520 |

---

## After Testing: Reset to Production

1. Remove `25.0 * ` multiplier in `ChunkTensionManager.addMiningTension`
2. Restore thresholds: `T1 = 0.70, T2 = 1.40, T3 = 3.0`
3. Comment out debug prints or wrap in `if (DEBUG)` flag
4. Re-run scenarios to confirm tests still pass
