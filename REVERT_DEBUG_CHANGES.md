# Reversion Script — Back to Production

After debug testing is complete, use this guide to revert all changes.

## Checklist

### File 1: `UTDModClient.java`
**Line**: 14–15 (remove frameCount)

**Change**:
```diff
- private static int frameCount = 0;
-
  @Override
```

**Line**: 33–38 (remove debug print)

**Change**:
```diff
            TensionSyncState.tickSmoothing();
            ClientRegionalAtmosphere.tick(client);
-
-            // DEBUG: Print perceived tension every 60 ticks to diagnose sync plumbing
-            if (frameCount++ % 60 == 0) {
-                double perceived = TensionSyncState.perceivedTension();
-                double raw = TensionSyncState.CLIENT_TENSION;
-                double local = TensionSyncState.CLIENT_LOCAL_TENSION;
-                System.out.println("[CLIENT_DEBUG] frame=" + frameCount + " perceived=" + String.format("%.6f", perceived) + " raw_global=" + String.format("%.6f", raw) + " local=" + String.format("%.6f", local));
-            }

            if (client.world != null && client.player != null && client.world.getTime() % 38 == 0L) {
```

---

### File 2: `ChunkTensionManager.java`
**Line**: 87–107 (remove multiplier)

**Change**:
```diff
    public static void addMiningTension(ServerWorld world, ChunkPos pos, String blockId) {
        String id = blockId.toLowerCase();
        double baseAmount;
-       // DEBUG: 25x multiplier for testing visibility
-       final double DEBUG_MINING_MULT = 25.0;
        if (id.contains("ancient_debris")) {
-           baseAmount = 0.14 * DEBUG_MINING_MULT;
+           baseAmount = 0.14;
        } else if (id.contains("diamond_ore") || id.contains("deepslate_diamond")) {
-           baseAmount = 0.11 * DEBUG_MINING_MULT;
+           baseAmount = 0.11;
        } else if (id.contains("emerald_ore") || id.contains("deepslate_emerald")) {
-           baseAmount = 0.09 * DEBUG_MINING_MULT;
+           baseAmount = 0.09;
        } else if (id.contains("deepslate") && id.contains("ore")) {
-           baseAmount = 0.078 * DEBUG_MINING_MULT;
+           baseAmount = 0.078;
        } else if (id.contains("coal_ore") || id.contains("coal_block")) {
-           baseAmount = 0.056 * DEBUG_MINING_MULT;
+           baseAmount = 0.056;
        } else if (id.contains("ore") || id.contains("debris")) {
-           baseAmount = 0.052 * DEBUG_MINING_MULT;
+           baseAmount = 0.052;
        } else if (id.contains("deepslate")) {
-           baseAmount = 0.048 * DEBUG_MINING_MULT;
+           baseAmount = 0.048;
        } else if (id.contains("stone")) {
-           baseAmount = 0.042 * DEBUG_MINING_MULT;
+           baseAmount = 0.042;
        } else {
-           baseAmount = 0.008 * DEBUG_MINING_MULT;
+           baseAmount = 0.008;
        }
```

---

### File 3: `ChunkTensionData.java`
**Line**: 28–32 (restore thresholds)

**Change**:
```diff
-   // DEMO: lower state ladder so casual mining reaches STRAINED/FRACTURED during playtests.
-   // DEBUG: Temporarily lowered thresholds to 25x mining multiplier for visibility testing.
-   private static final double T1 = 0.08;  // was 0.70 — now triggers at very low tension for debug
-   private static final double T2 = 0.18;  // was 1.40 — now triggers for medium tension
-   private static final double T3 = 0.40;  // was 3.0 — now triggers for high tension
+   // DEMO: lower state ladder so casual mining reaches STRAINED/FRACTURED during playtests.
+   private static final double T1 = 0.70;
+   private static final double T2 = 1.40;
+   private static final double T3 = 3.0;
```

**Line**: 476–478 (remove NBT write logging)

**Change**:
```diff
            tensionList.add(chunkNbt);
-           // DEBUG: Log NBT writes for persistence verification
-           System.out.println("[NBT_WRITE] chunk " + pos + " tension=" + String.format("%.6f", tension) + " scar=" + String.format("%.6f", scar));
        }
```

**Line**: 494–510 (remove NBT read logging and restore old code)

**Change**:
```diff
            double scar = chunkNbt.getDouble("scar_memory");
+           if (scar > 1e-5) {
+               data.scarMemory.put(pos, scar);
+           }
-           if (scar > 1e-5) {
-               data.scarMemory.put(pos, scar);
-           }
-           // DEBUG: Log NBT reads for persistence verification
-           double loadedTension = data.localTensions.getOrDefault(pos, 0.0);
-           double loadedScar = data.scarMemory.getOrDefault(pos, 0.0);
-           if (loadedTension > 0.0 || loadedScar > 0.0) {
-               System.out.println("[NBT_READ] chunk " + pos + " tension=" + String.format("%.6f", loadedTension) + " scar=" + String.format("%.6f", loadedScar));
-           }
            double miningRate = chunkNbt.getDouble("miningRate");
```

---

### File 4: `TensionHud.java` (client-only)
**Line**: 20–46 (restore normal rendering)

**Change**:
```diff
    public static void render(DrawContext context, float tickDelta) {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        ChunkPos pos = mc.player.getChunkPos();
        double t = ChunkTensionManager.getLocalTension(mc.world, pos);
        String label = stateLabel(t);
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int barW = 120;
        int barH = 10;
        int x = w/2 - barW/2;
        int y = h - 32;
        
-       // DEBUG: Aggressive override — show RED whenever tension > 0.05
-       if (t > 0.05) {
-           // Background: dark red
-           context.fill(x, y, x+barW, y+barH, 0xCC330000);
-           // Bar fill: bright red
-           int fill = (int)(Math.min(1.0, t/0.25) * barW);
-           context.fill(x, y, x+fill, y+barH, 0xFFFF0000);
-           // Label: DANGER in blinking white
-           context.drawText(mc.textRenderer, "⚠ TENSION SPIKE ⚠", x-50, y+1, 0xFFFFFF, false);
-           System.out.println("[HUD_DEBUG] TENSION_DETECTED t=" + String.format("%.6f", t));
-       } else {
-           // Bar background
-           context.fill(x, y, x+barW, y+barH, 0x88000000);
-           // Bar fill (normal)
-           int fill = (int)(Math.min(1.0, t/1.5) * barW);
-           int color = barColor(t);
-           context.fill(x, y, x+fill, y+barH, color);
-           // State label
-           context.drawText(mc.textRenderer, label, x+barW+8, y+1, 0xFFFFFF, false);
-       }
+       // Bar background
+       context.fill(x, y, x+barW, y+barH, 0x88000000);
+       // Bar fill
+       int fill = (int)(Math.min(1.0, t/1.5) * barW);
+       int color = barColor(t);
+       context.fill(x, y, x+fill, y+barH, color);
+       // State label
+       context.drawText(mc.textRenderer, label, x+barW+8, y+1, 0xFFFFFF, false);
        
        // Warnings (lower in priority than the aggressive override above)
        if (t > 1.0) {
            context.drawText(mc.textRenderer, "⚠ REGION COLLAPSE IMMINENT", x, y-12, 0xFF4444, false);
        } else if (t > 0.6) {
            context.drawText(mc.textRenderer, "⚠ ECOLOGICAL INSTABILITY", x, y-12, 0xFFAA00, false);
        }
    }
```

---

## Verify Reversion

After making changes:

1. **Compile to confirm no errors**:
   ```bash
   ./gradlew :compileJava -q
   ```

2. **Run test scenarios to confirm behavior restored**:
   ```bash
   ./gradlew :runTests --tests "DiffusionObservableScenario"
   ./gradlew :runTests --tests "RitualMitigationScenario"
   ```

3. **Grep for leftover debug markers**:
   ```bash
   grep -r "DEBUG_MINING_MULT\|HUD_DEBUG\|CLIENT_DEBUG\|NBT_WRITE\|NBT_READ" src/
   ```

---

## Summary
- **Total changes to revert**: 4 files
- **Total lines affected**: ~40 lines
- **Risk**: Low (only constants and debug prints affected; PDE logic unchanged)
