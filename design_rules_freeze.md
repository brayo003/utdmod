# UTD Mod: Design Rules Freeze (V1.3)

## 1. Tension Spread  (FROZEN)
Tension is bound to the player who generates it.  
World reactions only consider the *nearest player's* tension state.  
Global tension systems are forbidden.

## 2. Reactive Objects  (FROZEN)
Only the following systems may react to tension:
- Villager trades
- Loot tables
- Raid events
- Custom ritual block
- Custom tension mobs (Serpent, Wraith)

No allowed changes:
- Vanilla block breaking
- Farming mechanics
- Movement speed (except hostile mobs affected by tension)
- Any passive vanilla system not explicitly approved

## 3. Hook Policy  (FROZEN)
All interactions outside UI must occur using:
- Mixins
- TensionManager API

Direct edits to vanilla Minecraft code files are forbidden.

## 4. Future Features Rule  (FROZEN)
Any new feature must pass:
- Risk justification
- Reward justification
- Zone classification (biome or environment-specific)

Features lacking clear category alignment are prohibited.
