# UTD Mod - DCII Tension System (Minecraft 1.20.4 Fabric)

**Technical Implementation of Substrate X Theory**

This Minecraft mod implements the **DCII (Domain-Calibrated Instability Index)** framework from the Substrate X Theory of Information Gravity.

## Theoretical Foundation
Part of: **Substrate X: Theory of Information Gravity**
- Full theory: https://github.com/brayo003/Substrate-X-Theory-of-Information-Gravity
- Implements: T = α|∇ρ| + βE - γF tension dynamics
- float calculateTension(float baseStress, float threats, float defenses) {
    return baseStress + (threatMultiplier * threats) - (defenseMultiplier * defenses);
}


## Status: Technical Help Needed
The foundation works (mod loads, basic systems) but needs implementation help.

## Working
- Mod loads without crashes
- Basic item registration
- Clean build system

## Need Help With
1. HUD overlay rendering (tension meter)
2. Entity AI (tension serpents/wraiths)
3. Dimension-specific world ticking
4. Ritual mechanics
5. Network synchronization

## Build
```bash
./gradlew build
Collaboration

Looking for Fabric/Java developers to help implement the DCII framework in Minecraft.
