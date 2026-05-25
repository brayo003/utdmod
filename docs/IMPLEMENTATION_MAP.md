# Perceptual Synthesis: Visual Implementation Map

## Component Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    PERCEPTUAL SYNTHESIS LAYER                       │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │        TUNING CONFIG (PerceptualTuning.java)                 │  │
│  │  • VISUAL_GAIN = 1.5      • AUDIO_GAIN = 1.3                │  │
│  │  • ECOLOGY_GAIN = 1.4     • 4x Smoothing Alphas (0.08-0.15) │  │
│  │  • Thresholds & Hard Caps                                   │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              △                                      │
│         ┌────────────────────┼────────────────────┐                │
│         │                    │                    │                │
│    ┌────▼─────┐         ┌────▼─────┐         ┌────▼─────┐        │
│    │  VISUAL  │         │  AUDIO   │         │ ECOLOGY  │        │
│    │ (CLIENT) │         │ (CLIENT) │         │ (SERVER) │        │
│    └──────────┘         └──────────┘         └──────────┘        │
│         │                    │                    │                │
│   TensionHud.java  TensionAudioCoupling  TensionSpawnEcology    │
│   • State colors   • Smooth gain         • Spawn pressure     │
│   • Smooth bar     • Spatialization      • Mob agitation      │
│   • Warnings       • Layer cap (3)       • ECOLOGY_GAIN ×     │
│                                                               │
└─────────────────────────────────────────────────────────────┘
         △                    △                    △
         │                    │                    │
         └────────┬───────────┴────────┬──────────┘
                  │                    │
           ┌──────▼─────────────────────▼──────┐
           │    SERVER SYNC STATE               │
           │  (TensionSyncState)                │
           │  • perceivedTension()              │
           │  • CLIENT_TENSION (global)         │
           │  • CLIENT_LOCAL_TENSION            │
           │  • CLIENT_REGION_MAX               │
           │  • Exponential smoothing           │
           └──────────────────────────────────┘
                    △
                    │ (every 20 ticks)
           ┌────────┴──────────┐
           │ SERVER TENSION    │
           │  (TensionServerTick)
           │ • PDE diffusion   │
           │ • Mining/combat   │
           │ • Decay           │
           │ • Persistence     │
           └───────────────────┘
```

---

## Data Flow: Mining → Perception

### Timeline: Player mines stone intensely for 5 minutes

```
TIME    SERVER STATE             SYNC TO CLIENT         CLIENT PERCEPTION
────────────────────────────────────────────────────────────────────────────
0:00    tension = 0.0            0.0 synced every 20t   HUD: 🟢 CALM (0.0×1.5)
        (mining starts)          
                                                       Audio: Quiet baseline

1:00    tension = 0.2            0.2 synced             perceived = 0.2×1.5 = 0.3
        (mining accumulated)     exponential avg        HUD: 🟡 STRAINED
        mining = +0.05/tick      smoothing              ⚠ Instability Rising
                                 alpha = 0.07           Audio: Rising slightly
                                 
2:30    tension = 0.5            0.5 synced             perceived = 0.5×1.5 = 0.75
        (continued mining)       smooth to client       HUD: 🟠 FRACTURED
        decay = -0.01/tick                             ⚠⚠ Region Destabilizing
        diff = +0.02/tick                              Audio: Strong presence
                                                       Ecology: 2.2×hostile
                                                       
4:00+   tension = 0.8            0.8 synced             perceived = 0.8×1.5 = 1.2
        (peak mining)            alpha ≈ 0.07           HUD: 🔴 CRITICAL
        combat cascade           smooth interpolate     ⚠⚠⚠ CRITICAL PRESSURE
        diff = ±0.05/tick                              Audio: Intense ambient
                                                       Ecology: 2.45×hostile
                                                       
RECOVERY:                                               
        Player moves away        decay -0.01/tick       HUD: Fades slowly
        tension drops            smooth:alpha=0.07      (1-2 sec per level)
                                                       Audio: Softens
                                                       (1-1.5 sec fade)
                                                       Ecology: Normalizes
```

---

## Smoothing Effect Visualization

### Without Smoothing (Jerky)
```
Tension
  1.0 │                    ╱─────────╲
      │                 ╱╱           ╲╲
  0.5 │             ╱╱                 ╲╲
      │         ╱╱                       ╲
  0.0 └───────╱─────────────────────────╲─── Time
      0:00    1:00    2:00    3:00    4:00
```

### With Smoothing (Alpha=0.08, Smooth 1-2 sec)
```
Tension
  1.0 │                    ┌──────────┐
      │                ╱╱╱╱          ╲╲╲
  0.5 │            ╱╱                  ╲╲
      │        ╱╱                        ╲
  0.0 └────╱──────────────────────────────╲─ Time
      0:00    1:00    2:00    3:00    4:00
      
      Result: Smooth curve, player perceives
              gradual world change, not sudden
```

---

## State Machine: Perceptual Progression

```
                    CALM
                   (< 0.08)
                     🟢
                      │
                      │ Mining accumulates
                      │ (tension → 0.2)
                      │
                      ▼
            ┌─────────────────────┐
            │ ⚠ INSTABILITY RISING│
            │ Tension: 0.08-0.18  │
            │ STRAINED  🟡        │
            │ • -35% passives     │
            │ • +40% hostiles     │
            │ • Audio subtle      │
            └─────────────────────┘
                      │
                      │ Continued stress
                      │ (tension → 0.35)
                      │
                      ▼
        ┌──────────────────────────────┐
        │⚠⚠ REGION DESTABILIZING      │
        │ Tension: 0.18-0.35           │
        │ FRACTURED 🟠                 │
        │ • -70% passives culled       │
        │ • +120% hostiles aggressive  │
        │ • Audio strong presence      │
        └──────────────────────────────┘
                      │
                      │ Peak stress
                      │ (tension > 0.35)
                      │
                      ▼
     ┌──────────────────────────────────────┐
     │⚠⚠⚠ CRITICAL PRESSURE              │
     │ Tension: > 0.35                      │
     │ CRITICAL 🔴                          │
     │ • Passives gone                      │
     │ • +220% hostiles, highly aggressive  │
     │ • Audio overwhelming                │
     │ • Player: "GET OUT!"                │
     └──────────────────────────────────────┘
                      │
                      │ Player leaves chunk
                      │
                      ▼
                   RECOVERY
                   Decay visible
                   Audio fades
                   Return to CALM
```

---

## Smoothing Timeline: Moving Between States

```
TRANSITION: CALM → STRAINED (Alpha=0.08, ~2 sec)

Raw Tension
  0.20│                         ╱╱────────
      │                      ╱╱╱
      │                  ╱╱╱
  0.10│              ╱╱╱
      │          ╱╱╱
  0.00└──────────────────────────────── Time
      0s    0.5s   1s    1.5s   2s

Perceived Tension (Smoothed, shown in HUD)
  0.30│                         ╱────────
      │                      ╱╱╱
      │                  ╱╱╱
  0.15│              ╱╱╱
      │          ╱╱╱
  0.00└──────────────────────────────── time
      0s    0.5s   1s    1.5s   2s
      
Result: Smooth S-curve, player sees gradual
        escalation, not instant jump
```

---

## Performance Budget Allocation

```
┌─────────────────────────────────────────────────────────────┐
│  FPS BUDGET @ CRITICAL TENSION (Target: 55 TPS)            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Baseline (no tension):              60 FPS                │
│                                                              │
│  + HUD Rendering:                    -0.2 FPS               │
│  + Audio Layer Management (3 max):   -1.5 FPS               │
│  + Particle Effects (12 max/chunk):  -2.0 FPS               │
│  + Ecology Modifiers:                -0.5 FPS               │
│  + Debug Overlay (if enabled):       -0.3 FPS               │
│                                                              │
│  TOTAL @ CRITICAL TENSION:           55.5 FPS ✓             │
│                                                              │
│  Margin:                             +0.5 FPS buffer        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Config Influence Map

```
                    ┌─ VISUAL_GAIN
                    │  (1.5× default)
                    │     │
          HUD Bar   │     ▼ How visible?
            Level   │   ┌─────────────┐
                    │   │ Perceived   │
                    │   │ Tension     │
                    │   │ = Raw × 1.5 │
                    │   └─────────────┘
                    │         │
                    │         ▼ How colorful?
                    │   Color Interpolation
                    │   (smooth lerp)
                    │
          Warning   ├─ VISUAL_WARNING_THRESHOLD_*
            Text    │  (0.06, 0.15, 0.35)
                    │  When to show warnings?
                    │
                    └─ SMOOTHING_ALPHA_HUD
                       (0.08 = 1-2 sec)
                       How smooth?


                    ┌─ AUDIO_GAIN
                    │  (1.3× default)
                    │     │
        Audio Gain  │     ▼ How loud?
         Intensity  │   ┌──────────────┐
                    │   │ Gain         │
                    │   │ = Raw × 1.3  │
                    │   └──────────────┘
                    │         │
        State       ├─ VISUAL_WARNING_THRESHOLD_*
        Transitions │  When to trigger stingers?
        (Stingers)  │
                    │
        Distance    ├─ AUDIO_MAX_DISTANCE_BLOCKS
        Falloff     │  (128 blocks)
                    │  How far heard?
                    │
        Layer Cap   ├─ MAX_AUDIO_LAYERS (3)
                    │  Performance safety
                    │
                    └─ SMOOTHING_ALPHA_AUDIO
                       (0.12 = 1-1.5 sec)
                       How smooth?


        Hostile     ┌─ ECOLOGY_GAIN
        Spawn       │  (1.4× default)
        Pressure    │     │
                    │     ▼ How aggressive?
        Passive     │   ┌──────────────────┐
        Culling     │   │ HostileMod       │
                    │   │ *= 1.4 @ STRAINED│
        Agitation   │   │ *= 1.4 @ FRACTURED
        Boost       │   └──────────────────┘
                    │
                    └─ No smoothing on ecology
                       (server-side logic)
```

---

## Integration Points Checklist

```
CLIENT SIDE:
✓ TensionSyncState.perceivedTension()  ← All visual/audio read from here
✓ TensionHud.render()                  ← Hook in render event
✓ TensionAudioCoupling methods         ← Call in audio update
✓ TensionDebugOverlay render/toggle    ← Hook in render + input
✓ PerceptualTuning constants           ← Define once, use everywhere

SERVER SIDE:
✓ TensionSpawnEcology multipliers      ← Apply ECOLOGY_GAIN
✓ TensionServerTick.java               ← Already sends sync packets
✓ RegionDiagnosticsManager.EcoState    ← Already computed

NETWORK:
✓ TensionSyncPacket.java               ← Already syncs every 20 ticks
✓ perceivedTension formula             ← Already exponential smoothed
```

---

## Debugging: What to Monitor

### Visual Issues
```
Issue: HUD bar not visible
→ Check: Is TensionHud.render() called every frame?
→ Check: Is DrawContext valid?
→ Check: TensionSyncState receiving updates?

Issue: Colors not changing
→ Check: Color interpolation lerp() function
→ Check: Thresholds in PerceptualTuning

Issue: Bar jumps instead of smooth
→ Check: Is SMOOTHING_ALPHA_HUD being used?
→ Check: Alpha value < 0.15?
```

### Audio Issues
```
Issue: No audio changes
→ Check: Is UTDAudioManager using TensionAudioCoupling?
→ Check: AUDIO_GAIN > 1.0?
→ Check: Ambient audio layer working?

Issue: Audio cuts off abruptly
→ Check: Is finishLayer() being called?
→ Check: MAX_AUDIO_LAYERS cap being hit?

Issue: Stingers never trigger
→ Check: State detection working?
→ Check: Calling triggerStateTransitionAudio()?
```

### Ecology Issues
```
Issue: Too many mobs
→ Check: ECOLOGY_GAIN value
→ Adjust: Decrease from 1.4 to 1.1-1.2

Issue: Passives not culling
→ Check: TensionSpawnEcology.isPassiveSpawn() logic
→ Check: EcoState detection

Issue: Mobs not aggressive
→ Check: Velocity boost calculation
→ Check: FRACTURED state detection
```

---

## Success Criteria: Final Validation

```
VISUAL ✓
  □ HUD visible in-game
  □ Bar smooth (no jitter/jumps)
  □ Colors: Green → Yellow → Orange → Red
  □ Warnings appear at right times
  
AUDIO ✓
  □ Ambient fades in gradually
  □ Stingers play on state changes
  □ Audio cuts off when leaving area
  □ No audio spam (layer cap working)
  
ECOLOGY ✓
  □ More hostile mobs spawn
  □ Passive animals disappear at FRACTURED
  □ Mobs are more aggressive
  □ Effects are local (not global)
  
PERFORMANCE ✓
  □ TPS >= 55 throughout
  □ No FPS spikes at state changes
  □ Audio cap prevents lag
  □ 30-min gameplay test successful
  
SMOOTHING ✓
  □ Transitions 1-2 seconds
  □ No instant changes
  □ Recovery visible + audible
  □ Debug overlay responsive
  
DOCUMENTATION ✓
  □ All constants in PerceptualTuning
  □ All alphas used consistently
  □ No hardcoded values scattered
  □ Easy to adjust post-launch
```

---

**Status**: Ready for gameplay testing  
**Date**: May 25, 2026  
**Expected Player Experience**: "The world gradually feels more threatening as I mine longer, then relaxes when I leave"
