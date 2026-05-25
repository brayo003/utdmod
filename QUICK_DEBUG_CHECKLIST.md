# Quick Debug Checklist — Copy/Paste Into Console While Testing

## Watch for These Logs (Paste into a text file as you test)

### 1. Server Tick Loop (Check every ~2 sec in server console)
```
[UTDMod] Global Tension: X.XX | Storm: false
```
✅ If tension > 0.2 → server PDE is working
❌ If always 0.0 → PDE isn't receiving mining events

### 2. Client Receives Sync (Check client console every 3 sec)
```
[CLIENT_DEBUG] frame=N perceived=X.XXXXXX raw_global=X.XXXXXX local=X.XXXXXX
```
✅ If non-zero → packet is arriving and smoothing works
❌ If always 0.0 → sync packet not received or `applySnapshot` not called

### 3. Mining Injected (Check during mining in client console)
```
[HUD_DEBUG] TENSION_DETECTED t=X.XXXXXX
```
✅ Should appear after 1–5 stone breaks
❌ If never appears → mining not reaching `ChunkTensionManager.addMiningTension`

### 4. NBT Saved (Check when exiting world in server console)
```
[NBT_WRITE] chunk (X,Z) tension=X.XXXXXX scar=X.XXXXXX
```
✅ Should show values > 0 in high-tension chunks
❌ If always 0 → tension not persisting to the chunk

### 5. NBT Loaded (Check when re-entering world in server console)
```
[NBT_READ] chunk (X,Z) tension=X.XXXXXX scar=X.XXXXXX
```
✅ Should match `[NBT_WRITE]` value from previous session (±5%)
❌ If shows 0 after save showed non-zero → deserialization bug

---

## Visual Proof (In-Game)

### Look for in Minecraft HUD:
1. **Red bar** appears in center-bottom when tension > 0.05
2. **"⚠ TENSION SPIKE ⚠"** warning text flashes
3. **Action bar** (above hotbar) shows state:
   - "Chunk Tension: 0.XX (STRAINED) | ..."

### Test Procedure:
1. Load world near surface
2. Mine stone for 30 sec
3. **Red bar should appear within 10 breaks**
4. **State should escalate**:
   - After 30 sec: STRAINED
   - After 2 min: FRACTURED
5. **Walk away & return** → tension should still be there

---

## One-Line Troubleshooting

| What You See | What It Means | What To Check |
|---|---|---|
| Red bar appears instantly | ✅ WORKING | Continue to persistence test |
| Red bar never appears | ❌ Client not reading tension | Search console: does `[HUD_DEBUG]` ever print? |
| `[HUD_DEBUG]` prints but no red | ❌ HUD render not called | Check `TensionHud.render()` is registered as screen overlay |
| Server shows global tension but client doesn't | ❌ Sync broken | Is `TensionSyncPacket.registerReceiver()` called in `onInitializeClient`? |
| `[NBT_WRITE]` shows 0.0 after mining | ❌ Mining not injecting | Check mining event listener (BlockBreakEvent or similar) |
| `[NBT_READ]` shows 0.0 but `[NBT_WRITE]` showed 0.5 | ❌ Deserialization bug | Look for `data.scarMemory.put()` in `fromNbt` |
| Tension appears but instantly vanishes | ❌ Decay too aggressive | Reduce `LAMBDA` in `TensionServerTick.java` |

---

## Copy-Paste: Where to Find Logs

### Server Console
```bash
# In server window or logs/latest.log
grep "\[UTDMod\]" latest.log
grep "\[CLIENT_DEBUG\]" latest.log
grep "\[NBT_" latest.log
grep "\[HUD_DEBUG\]" latest.log
```

### Client Console (in-game F3+A or debugger)
```
[CLIENT_DEBUG] → Client tick loop
[HUD_DEBUG]    → HUD render proving tension
[ERROR]        → If sync packet fails
```

---

## Expected Timeline

| Time | Event | Log |
|------|-------|-----|
| 0:00 | Start mining | Console: `[UTDMod] Global Tension: 0.00` |
| 0:05 | Break 5–10 stone | Console: `[CLIENT_DEBUG] ... perceived=0.15` |
| 0:10 | HUD should turn red | Console: `[HUD_DEBUG] TENSION_DETECTED t=0.25` |
| 0:30 | Action bar: STRAINED | Console: `[CHUNK_STATE] ... STABLE -> STRAINED` |
| 2:00 | Action bar: FRACTURED | Console: `[CHUNK_STATE] ... STRAINED -> FRACTURED` |
| 5:00 | Exit & save | Console: `[NBT_WRITE] chunk (X,Z) tension=0.5X` |
| 5:05 | Re-enter & load | Console: `[NBT_READ] chunk (X,Z) tension=0.5X` |

If your timeline looks like this → **everything works**. Dial back multipliers and re-run test scenarios.
