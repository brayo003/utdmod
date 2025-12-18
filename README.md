# UTD Mod - DCII Tension System (Minecraft 1.20.4 Fabric)

**Status: Technical implementation help needed**

A Fabric mod implementing DCII tension mechanics. The foundation works (mod loads, registers items) but needs help with full implementation.

## Working
- Mod loads without crashes
- Basic item registration framework  
- Clean build system (Gradle/Fabric Loom)

## Need Help With
- Tension HUD overlay rendering
- Entity registration and custom AI (tension serpents/wraiths)
- Dimension-specific world ticking (Overworld only)
- Ritual block mechanics
- Network synchronization for multiplayer

## Technical Details
Implements: T = α|∇ρ| + βE - γF
- T: Global tension value
- α|∇ρ|: Player activity gradient
- βE: Environmental factors
- γF: Stabilizing forces

## Current Issues
1. Items not appearing in creative menu
2. Performance concerns with world ticking
3. Missing mixin targets causing warnings
4. Asset loading errors for some models/sounds

## How to Help
Looking for technical collaborators familiar with:
- Fabric Mod Loader API
- Minecraft entity/block systems
- Java game development
- Performance optimization

Build: ./gradlew build
