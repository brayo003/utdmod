> **[SIMULATION LAB — NOT MINECRAFT RUNTIME]**
> This document describes gameplay archetypes (miner, explorer, builder, trader) tested in the
> `simulation_lab/` standalone simulation, not observed in a live Minecraft session.
> Activation, memory, and transport values here come from `GridSimulationV2` grid cells, not
> Minecraft chunks. For in-game runtime behavior, see `analysis/UTD_TELEMETRY_REPORT.md`.

PLAYER EXPERIENCE SNAPSHOT: FROZEN ARCHITECTURE
===============================================

"What would a Minecraft player actually observe if this transport system
governed regional world evolution?"

================================================================================
SCENARIO A: THE PEACEFUL VILLAGE (Settlement Growth)
================================================================================

TICK 0-50: INITIAL CONSTRUCTION
  Reality: Player establishes a small village with 50 population units.
  System sees: Activation injection into a 41×41 grid at center.
  Player observes:
    • Activity begins at center
    • Expansion visible outward (radius 3.6 → 12.5 cells)
    • Neighboring cells becoming active
    • Sense of growing settlement

TICK 50-100: GROWTH PHASE
  Reality: Player continues building and populating.
  System sees: Sustained 0.5 activation injection, hotspot radius → 19.4 cells
  Player observes:
    • Village expands further
    • More distant cells activate
    • Natural-looking gradual growth
    • Feels organic and healthy

TICK 100: PLAYER STOPS INVESTING IN VILLAGE
  Reality: Construction completes, population stabilizes, no new activity.
  System sees: Activation injection ceases.
  
  IMMEDIATELY (Tick 100):
    • Center activation drops from 0.29 → 0.17
    • Player might not notice at tick-level

TICK 100-150: POST-CONSTRUCTION PERIOD
  Reality: Player expects village to be self-maintaining. 
           Goes off to explore other regions.
  System sees: Activation cascading down exponentially
  
  Player observes:
    • Tick 100: Village still looks vibrant
    • Tick 125: "Hmm, activity is dropping"
    • Tick 150: "Something is very wrong - the village is dying!"

TICK 150-225: COMPLETE COLLAPSE
  Reality: Player returns to find the village has vanished.
  System sees: Hotspot radius → 0, all activation → 0
  
  Player observes:
    • Tick 200: Only faint traces of activation
    • Tick 225: COMPLETELY DEAD
    • Village has become a ghost town
    • All population scattered or vanished

PLAYER VERDICT: "This is broken. Settlements shouldn't die the moment 
                I stop managing them. A functioning village should maintain 
                itself for at least the time I'm away exploring."

================================================================================
SCENARIO B: THE TRADE ROUTE (Road Formation)
================================================================================

TICK 0: PLAYER ESTABLISHES TWO SETTLEMENTS
  Reality: Builds two thriving villages 42 cells apart.
  System sees: Two sources of constant activity
  
  Player expects: Natural trade routes to form between villages
  Player imagines: Worn paths, merchant trails, obvious connections

TICK 0-500: WAITING FOR ROADS TO FORM
  Reality: Player patiently waits, periodically checks for changes.
  System sees: Transport propagation along diagonal: 0.0005 → 0.0044 → declining
  
  Player observes:
    • Tick 50: "Nothing yet"
    • Tick 100: "Still nothing... trade routes should be forming"
    • Tick 250: "These settlements are completely isolated"
    • Tick 500: "I give up. There are no roads."

PLAYER VERDICT: "The two settlements feel completely disconnected, 
                like they're on different worlds. Trade seems impossible."

ROOT PROBLEM FROM PLAYER'S PERSPECTIVE:
  The player can SEE both settlements clearly.
  The player expects transport to FLOW between them.
  But the system shows no visible corridor or connection.
  
  Reason (invisible to player):
    exp(-42 cells / 1.0 decay_constant) = exp(-42) ≈ 10^-18
    Transport decays to nothing before reaching midpoint.

================================================================================
SCENARIO C: THE RUINS OF CONFLICT (Conflict Recovery)
================================================================================

TICK 0-100: PEACEFUL CITY THRIVES
  Reality: Player builds a thriving city over 100 ticks
  System sees: Steady activity, memory slowly accumulating
  
  Player observes:
    • City develops normally
    • Activation builds up
    • Structure becomes visible

TICK 100: CATASTROPHIC EVENT (Battle/Explosion/Disaster)
  Reality: A huge conflict/explosion damages the city.
  System sees: Massive energy spike (1.5× normal) + heavy memory injection (2.0)
  
  Player observes (IMMEDIATELY):
    • Flash of activity: Activation spikes to 0.96
    • Visible destruction: Massive energy release
    • Creates lasting scar

TICK 100-150: IMMEDIATE AFTERMATH
  Reality: Damage is visible but still active
  System sees: Activation decays from 0.96 → 0.04
  
  Player observes:
    • Destruction gradually fades
    • Activity decreases
    • Scarred region still visible through memory

TICK 150-300: THE SCAR LINGERS
  Reality: Surface activity is gone, but deep scars remain
  System sees: Memory peaked at 2.017, decaying slowly to ~1.8
  
  Player observes:
    • City is essentially dead (no visible activation)
    • BUT: Landscape shows heightened memory signature
    • Scarred region visibly "remembers" the conflict
    • Area feels wounded, corrupted

TICK 300-700+: SLOW HEALING
  Reality: Scar slowly fades over 400+ additional ticks
  System sees: Memory half-life = 693 ticks
  
  Player observes:
    • Scarred region gradually normalizing
    • Can visibly see the "healing" over time
    • After ~700 ticks, scar is half-gone

PLAYER VERDICT: "The conflict scars are amazing! Battles leave visible, 
                long-lasting damage. The landscape tells stories."

================================================================================
SCENARIO D: THE ABANDONED CITY (Historical Persistence)
================================================================================

TICK 0-200: THRIVING CIVILIZATION
  Reality: Player builds a successful city
  System sees: Activity injection, memory accumulation
  
  Player observes:
    • City develops normally
    • Population present

TICK 200: ABANDONMENT
  Reality: Player packs up and leaves to explore elsewhere
  System sees: Activity injection stops
  
  Player expects:
    • City to decay slowly
    • Reactivation to be easy later
    • Memories of civilization to persist

TICK 200-400: ABANDONED CITY PHASE
  Reality: Player is away. City slowly dies.
  System sees: Activity decays, but memory persists (decay rate = 0.001)
  
  Player observes:
    • Activation drops rapidly
    • Memory remains high
    • City feels "remembered" but dormant

TICK 400+: RETURN AND REACTIVATION
  Reality: Player comes back with intention to restore the city
  System sees: Memory = 0.213 (still healthy), Readiness = 0.897
  
  Player tries to restart city activity:
    • Injects fresh activation
    • City reactivates MORE EASILY than new location
    • (Readiness is marginally higher due to retained memory)

  BUT: Practical advantage is TINY (0.897 vs 0.972 for new site)
  
  Player observes:
    • City does restart relatively easily
    • But the advantage over brand-new locations is minimal
    • Historical significance is more narrative than mechanical

PLAYER VERDICT: "The memory system is interesting. Old cities feel like they've
               been somewhere. But the practical advantage to reclaiming vs 
               starting fresh is almost nonexistent."

================================================================================
SCENARIO E: THE MINING OPERATION (Mining Scar)
================================================================================

PHASE 1: OLD MINE (100 ticks)
  Reality: Player mines actively for 100 ticks
  System sees: Activation = 0.6, memory accumulation
  
  Player observes:
    • Mine is operating
    • Resources extracted

PHASE 2: ABANDONMENT (200 ticks)
  Reality: Player stops mining
  System sees: Activity drops to 0, memory persists
  
  Player observes:
    • Mine closed down
    • Can see it on map as a marked location

PHASE 3: REOPENING OLD MINE (100 ticks)
  Reality: Player returns to reopen old mine
  System sees: Mine reactivated at same location
  
  Result: Activation = 0.0210
  Memory: 0.0877

  But ALSO:
  
PHASE 4: NEW MINE (100 ticks) 
  Reality: Player opens completely fresh mine nearby
  System sees: Brand new location, zero prior history
  
  Result: Activation = 0.0284
  Memory: 0.0576

COMPARISON:
  New mine is actually STRONGER than reopened old mine!
  New mine: 0.0284 > Old mine: 0.0210

PLAYER VERDICT: "Wait... why is the NEW mine more active than the old one?
               That doesn't make sense. Shouldn't reopening be faster?"

ROOT CAUSE (invisible to player):
  Old mine has accumulated memory from mining history.
  Memory = 0.0877 creates readiness = 0.958
  New mine has zero memory = readiness = 0.972 (higher!)
  
  The memory is a PENALTY, not a bonus.
  This is backwards from player intuition.

================================================================================
SCENARIO F: TERRITORIAL COMPETITION (Competing Settlements)
================================================================================

TICK 0: THREE SETTLEMENTS ESTABLISHED
  Reality: Three rival settlements established at equal power
  System sees: Three activation sources
  
  Player expects:
    • Dynamic territorial competition
    • Boundaries that shift with fortunes
    • Emergent geopolitical patterns

TICK 0-400: OBSERVING COMPETITION
  Reality: Player watches for territorial shifts
  System sees: Static geometric boundaries based on distance
  
  Player observes:
    • Tick 0: Territories divided among three settlements
    • Tick 100: Territories unchanged
    • Tick 200: Territories unchanged
    • Tick 400: Territories completely unchanged

  Territorial Stability = 0.998+ (essentially frozen)

PLAYER VERDICT: "The territories are just fixed based on distance.
               There's no actual competition happening. It's all predetermined."

ROOT CAUSE (invisible to player):
  Dominance calculated as: influence_A / (influence_A + influence_B + influence_C)
  With stable settlements and distance-based influence, territories never shift.
  It's not competition; it's geometry.

================================================================================
SUMMARY: PLAYER EXPERIENCE BREAKDOWN
================================================================================

✓ WORKS WELL:
  • Conflict scars are meaningful and visible
  • Memory persists in landscape
  • Recovery time feels appropriate
  • Historical damage tells stories

✗ DOESN'T WORK:
  • Settlements die completely after injection stops
  • No self-sustaining growth
  • No visible trade routes form
  • Historical advantage is too small to matter
  • Old mines aren't actually better to reopen
  • Territorial competition is static, not dynamic
  • Settlements feel isolated and disconnected

CORE PROBLEMS FROM A PLAYER'S PERSPECTIVE:
──────────────────────────────────────────

1. SETTLEMENTS FEEL DEAD
   "I built a village and went to explore. When I came back, it had
    vanished. Settlements should persist."

2. WORLD FEELS DISCONNECTED
   "I have two settlements but there's no visible connection between them.
    They might as well be on different planets."

3. HISTORY LACKS TEETH
   "The memory system is interesting, but it doesn't actually make old
    locations better. Historical sites feel like tourism, not advantage."

4. GROWTH SEEMS ARTIFICIAL
   "Villages only grow when I'm actively building them. The moment I
    stop, they collapse. This doesn't feel like natural civilization."

================================================================================
CONCLUSION: PLAYER WOULD SAY
================================================================================

"The scars and damage system is cool - battles create lasting visible effects
on the landscape. That's immersive and interesting.

But the overall world doesn't feel alive. Settlements are fragile. Trade is
nonexistent. History is interesting visually but doesn't matter mechanically.

This doesn't feel like a living, evolving world. It feels like a system that
requires constant player intervention to remain visible."

================================================================================
END OF PLAYER EXPERIENCE SNAPSHOT
================================================================================
